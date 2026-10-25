package com.riseai.service.impl;

import com.riseai.dto.request.CreateRefundRequest;
import com.riseai.dto.response.RefundResponse;
import com.riseai.entity.Donation;
import com.riseai.entity.EscrowLedgerEntry;
import com.riseai.entity.RefundRequest;
import com.riseai.enums.DonationStatus;
import com.riseai.enums.RefundStatus;
import com.riseai.enums.Role;
import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;
import com.riseai.mapper.RiseEvents;
import com.riseai.repository.DonationRepository;
import com.riseai.repository.RefundRepository;
import com.riseai.security.AuthUser;
import com.riseai.service.AuditService;
import com.riseai.service.DomainEventPublisher;
import com.riseai.service.EscrowEngine;
import com.riseai.service.RefundService;
import com.riseai.util.Codes;
import com.riseai.util.Money;
import com.riseai.util.States;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
    private static final String ENTITY = "REFUND_REQUEST";

    private final RefundRepository refundRepository;
    private final DonationRepository donationRepository;
    private final EscrowEngine escrowEngine;
    private final AuditService auditService;
    private final DomainEventPublisher domainEvents;

    @Transactional
    @Override
    public RefundResponse request(AuthUser actor, UUID donationId, CreateRefundRequest request) {
        return request(actor, donationId, request, null);
    }

    @Transactional
    @Override
    public RefundResponse request(AuthUser actor, UUID donationId, CreateRefundRequest request, String idempotencyKey) {
        AuthUser.require(actor);
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 100)) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Idempotency-Key must be 1-100 characters");
        }
        Donation donation = lockDonation(donationId);
        if (!actor.isAdmin() && !actor.is(donation.getDonorId())) {
            throw ApplicationException.notFound("Donation");
        }
        if (idempotencyKey != null) {
            Optional<RefundRequest> existing = refundRepository.findByRequestedByAndIdempotencyKey(actor.id(), idempotencyKey);
            if (existing.isPresent()) {
                RefundRequest refund = existing.get();
                if (!refund.getDonationId().equals(donationId) || refund.getAmount().compareTo(Money.requirePositive(request.amount())) != 0) {
                    throw new ApplicationException(ErrorCode.DUPLICATE_OPERATION, "Idempotency-Key was already used for a different refund request");
                }
                return RefundResponse.from(refund);
            }
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
        refund.setIdempotencyKey(idempotencyKey);
        refund.setRefundStatus(RefundStatus.REQUESTED);
        refundRepository.saveAndFlush(refund);
        auditService.transition(actor.id(), "REFUND_REQUESTED", ENTITY, refund.getId(), null, RefundStatus.REQUESTED, request.reason());
        domainEvents.publish(RiseEvents.refundRequested(refund, actor));
        moveDonation(donation, DonationStatus.REFUND_PENDING, actor.id(), "Refund requested " + refund.getRefundCode());
        return RefundResponse.from(refund);
    }

    @Transactional
    @Override
    public RefundResponse approve(AuthUser actor, UUID id, String reason) {
        RefundRequest refund = lock(id);
        Donation donation = requireReviewer(actor, refund);
        States.require(refund.getRefundStatus(), RefundStatus.REQUESTED);
        if (refund.getAmount().compareTo(donation.refundableAmount()) > 0) {
            throw new ApplicationException(ErrorCode.REFUND_EXCEEDS_REFUNDABLE);
        }
        escrowEngine.reserveRefund(refund.getCampaignId(), refund.getCurrency(), donation.getId(), refund.getId(), refund.getAmount(), actor.id());
        refund.setFundsReserved(true);
        refund.setReviewedBy(actor.id());
        refund.setReviewedAt(Instant.now());
        refund.setDecisionReason(reason);
        move(refund, RefundStatus.APPROVED, actor.id(), "REFUND_APPROVED", reason);
        return RefundResponse.from(refund);
    }

    @Transactional
    @Override
    public RefundResponse reject(AuthUser actor, UUID id, String reason) {
        RefundRequest refund = lock(id);
        requireReviewer(actor, refund);
        States.require(refund.getRefundStatus(), RefundStatus.OPEN);
        releaseReservation(refund, actor.id());
        refund.setReviewedBy(actor.id());
        refund.setReviewedAt(Instant.now());
        refund.setDecisionReason(reason);
        move(refund, RefundStatus.REJECTED, actor.id(), "REFUND_REJECTED", reason);
        restoreDonation(lockDonation(refund.getDonationId()), actor.id(), "Refund rejected");
        return RefundResponse.from(refund);
    }

    @Transactional
    @Override
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
    @Override
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
        releaseReservation(refund, actor.id());
        EscrowLedgerEntry entry = escrowEngine.recordRefund(refund.getCampaignId(), refund.getCurrency(), donation.getId(), refund.getId(), refund.getAmount(), actor.id());
        donation.setRefundedAmount(donation.getRefundedAmount().add(refund.getAmount()));
        DonationStatus target = donation.refundableAmount().signum() == 0 ? DonationStatus.REFUNDED : DonationStatus.PARTIALLY_REFUNDED;
        moveDonation(donation, target, actor.id(), "Refund recorded " + refund.getRefundCode());
        refund.setRecordedBy(actor.id());
        refund.setRecordedAt(Instant.now());
        move(refund, RefundStatus.RECORDED, actor.id(), "REFUND_RECORDED", "Simulated ledger refund; no external payment executed");
        domainEvents.publish(RiseEvents.refundRecorded(refund, entry, actor));
        return RefundResponse.from(refund);
    }

    @Transactional(readOnly = true)
    @Override
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
    @Override
    public Page<RefundResponse> mine(AuthUser actor, Pageable pageable) {
        return refundRepository.findByRequestedBy(AuthUser.require(actor).id(), pageable).map(RefundResponse::from);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<RefundResponse> adminList(RefundStatus status, Pageable pageable) {
        Page<RefundRequest> page = status == null ? refundRepository.findAll(pageable) : refundRepository.findByRefundStatus(status, pageable);
        return page.map(RefundResponse::from);
    }

    private void releaseReservation(RefundRequest refund, UUID actorId) {
        if (refund.isFundsReserved()) {
            escrowEngine.releaseRefundReservation(refund.getCampaignId(), refund.getCurrency(), refund.getDonationId(), refund.getId(), refund.getAmount(), actorId);
            refund.setFundsReserved(false);
        }
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
