package com.riseai.refund;

import com.riseai.audit.AuditService;
import com.riseai.common.Codes;
import com.riseai.common.Money;
import com.riseai.common.States;
import com.riseai.common.exception.ApplicationException;
import com.riseai.common.exception.ErrorCode;
import com.riseai.donation.Donation;
import com.riseai.donation.DonationRepository;
import com.riseai.donation.DonationStatus;
import com.riseai.escrow.EscrowAccount;
import com.riseai.escrow.EscrowAccountRepository;
import com.riseai.escrow.EscrowEngine;
import com.riseai.refund.RefundDtos.CreateRefundRequest;
import com.riseai.refund.RefundDtos.RefundResponse;
import com.riseai.security.AuthUser;
import com.riseai.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundService {
    private static final String ENTITY = "REFUND_REQUEST";

    private final RefundRepository refundRepository;
    private final DonationRepository donationRepository;
    private final EscrowAccountRepository escrowAccountRepository;
    private final EscrowEngine escrowEngine;
    private final AuditService auditService;

    @Transactional
    public RefundResponse request(AuthUser actor, UUID donationId, CreateRefundRequest request) {
        AuthUser.require(actor);
        Donation donation = lockDonation(donationId);
        if (!actor.isAdmin() && !actor.is(donation.getDonorId())) {
            throw ApplicationException.notFound("Donation");
        }
        States.require(donation.getDonationStatus(), DonationStatus.CONFIRMED, DonationStatus.PARTIALLY_REFUNDED);
        if (refundRepository.existsByDonationIdAndRefundStatusIn(donationId, RefundStatus.OPEN)) {
            throw new ApplicationException(ErrorCode.OPEN_REQUEST_EXISTS, "Donation already has an open refund request");
        }
        BigDecimal amount = Money.requirePositive(request.amount());
        if (amount.compareTo(donation.refundableAmount()) > 0) {
            throw new ApplicationException(ErrorCode.REFUND_EXCEEDS_REFUNDABLE,
                    "Refundable amount is " + donation.refundableAmount().toPlainString() + " " + donation.getCurrency());
        }
        RefundRequest refund = new RefundRequest();
        refund.setRefundCode(Codes.generate("RFD"));
        refund.setDonationId(donationId);
        refund.setCampaignId(donation.getCampaignId());
        refund.setRequestedBy(actor.id());
        refund.setAmount(amount);
        refund.setCurrency(donation.getCurrency());
        refund.setReason(request.reason());
        refund.setRefundStatus(RefundStatus.REQUESTED);
        refundRepository.saveAndFlush(refund);
        auditService.transition(actor.id(), "REFUND_REQUESTED", ENTITY, refund.getId(), null, RefundStatus.REQUESTED, request.reason());
        moveDonation(donation, DonationStatus.REFUND_PENDING, actor.id(), "Refund requested " + refund.getRefundCode());
        return RefundResponse.from(refund);
    }

    @Transactional
    public RefundResponse approve(AuthUser actor, UUID id, String reason) {
        RefundRequest refund = lock(id);
        Donation donation = requireReviewer(actor, refund);
        States.require(refund.getRefundStatus(), RefundStatus.REQUESTED);
        EscrowAccount account = escrowAccountRepository.findByCampaignIdAndCurrency(refund.getCampaignId(), refund.getCurrency())
                .orElseThrow(() -> ApplicationException.notFound("Escrow account"));
        if (account.getAvailableBalance().compareTo(refund.getAmount()) < 0) {
            throw new ApplicationException(ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE, "Unreserved escrow balance cannot cover this refund");
        }
        if (refund.getAmount().compareTo(donation.refundableAmount()) > 0) {
            throw new ApplicationException(ErrorCode.REFUND_EXCEEDS_REFUNDABLE);
        }
        refund.setReviewedBy(actor.id());
        refund.setReviewedAt(Instant.now());
        refund.setDecisionReason(reason);
        move(refund, RefundStatus.APPROVED, actor.id(), "REFUND_APPROVED", reason);
        return RefundResponse.from(refund);
    }

    @Transactional
    public RefundResponse reject(AuthUser actor, UUID id, String reason) {
        RefundRequest refund = lock(id);
        requireReviewer(actor, refund);
        States.require(refund.getRefundStatus(), RefundStatus.OPEN);
        refund.setReviewedBy(actor.id());
        refund.setReviewedAt(Instant.now());
        refund.setDecisionReason(reason);
        move(refund, RefundStatus.REJECTED, actor.id(), "REFUND_REJECTED", reason);
        restoreDonation(lockDonation(refund.getDonationId()), actor.id(), "Refund rejected");
        return RefundResponse.from(refund);
    }

    @Transactional
    public RefundResponse cancel(AuthUser actor, UUID id) {
        RefundRequest refund = lock(id);
        if (!AuthUser.require(actor).is(refund.getRequestedBy())) {
            throw ApplicationException.notFound("Refund request");
        }
        States.require(refund.getRefundStatus(), RefundStatus.REQUESTED);
        move(refund, RefundStatus.CANCELLED, actor.id(), "REFUND_CANCELLED", null);
        restoreDonation(lockDonation(refund.getDonationId()), actor.id(), "Refund cancelled");
        return RefundResponse.from(refund);
    }

    @Transactional
    public RefundResponse record(AuthUser actor, UUID id) {
        RefundRequest refund = lock(id);
        if (refund.getRefundStatus() == RefundStatus.RECORDED) {
            throw new ApplicationException(ErrorCode.DUPLICATE_OPERATION, "Refund has already been recorded");
        }
        requireReviewer(actor, refund);
        States.require(refund.getRefundStatus(), RefundStatus.APPROVED);
        Donation donation = lockDonation(refund.getDonationId());
        if (refund.getAmount().compareTo(donation.refundableAmount()) > 0) {
            throw new ApplicationException(ErrorCode.REFUND_EXCEEDS_REFUNDABLE);
        }
        escrowEngine.recordRefund(refund.getCampaignId(), refund.getCurrency(), donation.getId(), refund.getId(), refund.getAmount(), actor.id());
        donation.setRefundedAmount(donation.getRefundedAmount().add(refund.getAmount()));
        DonationStatus target = donation.refundableAmount().signum() == 0 ? DonationStatus.REFUNDED : DonationStatus.PARTIALLY_REFUNDED;
        moveDonation(donation, target, actor.id(), "Refund recorded " + refund.getRefundCode());
        refund.setRecordedBy(actor.id());
        refund.setRecordedAt(Instant.now());
        move(refund, RefundStatus.RECORDED, actor.id(), "REFUND_RECORDED", "Simulated ledger refund; no external payment executed");
        return RefundResponse.from(refund);
    }

    @Transactional(readOnly = true)
    public RefundResponse get(AuthUser actor, UUID id) {
        RefundRequest refund = refundRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Refund request"));
        if (AuthUser.require(actor).isAdmin() || actor.is(refund.getRequestedBy())) {
            return RefundResponse.from(refund);
        }
        Donation donation = donationRepository.findById(refund.getDonationId()).orElseThrow(() -> ApplicationException.notFound("Refund request"));
        if (actor.is(donation.getDonorId())) {
            return RefundResponse.from(refund);
        }
        throw ApplicationException.notFound("Refund request");
    }

    @Transactional(readOnly = true)
    public Page<RefundResponse> mine(AuthUser actor, Pageable pageable) {
        return refundRepository.findByRequestedBy(AuthUser.require(actor).id(), pageable).map(RefundResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<RefundResponse> adminList(RefundStatus status, Pageable pageable) {
        Page<RefundRequest> page = status == null ? refundRepository.findAll(pageable) : refundRepository.findByRefundStatus(status, pageable);
        return page.map(RefundResponse::from);
    }

    private Donation requireReviewer(AuthUser actor, RefundRequest refund) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Donation donation = donationRepository.findById(refund.getDonationId()).orElseThrow(() -> ApplicationException.notFound("Donation"));
        if (actor.is(refund.getRequestedBy()) || actor.is(donation.getDonorId())) {
            throw new ApplicationException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        }
        return donation;
    }

    private void restoreDonation(Donation donation, UUID actorId, String reason) {
        if (donation.getDonationStatus() == DonationStatus.REFUND_PENDING) {
            moveDonation(donation, donation.getRefundedAmount().signum() > 0 ? DonationStatus.PARTIALLY_REFUNDED : DonationStatus.CONFIRMED, actorId, reason);
        }
    }

    private void moveDonation(Donation donation, DonationStatus target, UUID actorId, String reason) {
        DonationStatus previous = donation.getDonationStatus();
        donation.setDonationStatus(target);
        auditService.transition(actorId, "DONATION_STATUS_CHANGED", "DONATION", donation.getId(), previous, target, reason);
    }

    private void move(RefundRequest refund, RefundStatus target, UUID actorId, String action, String reason) {
        RefundStatus previous = refund.getRefundStatus();
        refund.setRefundStatus(target);
        auditService.transition(actorId, action, ENTITY, refund.getId(), previous, target, reason);
    }

    private RefundRequest lock(UUID id) {
        return refundRepository.findByIdForUpdate(id).orElseThrow(() -> ApplicationException.notFound("Refund request"));
    }

    private Donation lockDonation(UUID id) {
        return donationRepository.findByIdForUpdate(id).orElseThrow(() -> ApplicationException.notFound("Donation"));
    }
}
