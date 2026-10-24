package com.riseai.settlement;

import com.riseai.audit.AuditService;
import com.riseai.campaign.Campaign;
import com.riseai.campaign.CampaignService;
import com.riseai.campaign.CampaignStatus;
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
import com.riseai.escrow.EscrowStatus;
import com.riseai.security.AuthUser;
import com.riseai.settlement.SettlementDtos.SettlementResponse;
import com.riseai.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementService {
    private static final String ENTITY = "SETTLEMENT_REQUEST";
    private static final Set<CampaignStatus> SETTLEABLE = EnumSet.of(CampaignStatus.COMPLETED, CampaignStatus.CANCELLED);
    private static final Set<DonationStatus> LOCKED_DONATIONS = EnumSet.of(DonationStatus.CONFIRMED, DonationStatus.PARTIALLY_REFUNDED, DonationStatus.REFUND_PENDING);

    private final SettlementRepository settlementRepository;
    private final DonationRepository donationRepository;
    private final EscrowAccountRepository escrowAccountRepository;
    private final CampaignService campaignService;
    private final EscrowEngine escrowEngine;
    private final AuditService auditService;

    @Transactional
    public SettlementResponse propose(AuthUser actor, UUID campaignId, String reason) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Campaign campaign = campaignService.lock(campaignId);
        requireNotCreator(actor, campaign);
        if (settlementRepository.existsByCampaignIdAndSettlementStatus(campaignId, SettlementStatus.PROPOSED)) {
            throw new ApplicationException(ErrorCode.OPEN_REQUEST_EXISTS, "Campaign already has a proposed settlement");
        }
        Plan plan = plan(campaign);
        SettlementRequest settlement = new SettlementRequest();
        settlement.setSettlementCode(Codes.generate("STL"));
        settlement.setCampaignId(campaignId);
        settlement.setCurrency(campaign.getCurrency());
        settlement.setSettlementType(SettlementType.PRO_RATA_DONOR_REFUND);
        settlement.setAmount(plan.amount());
        settlement.setSettlementStatus(SettlementStatus.PROPOSED);
        settlement.setReason(reason);
        settlement.setProposedBy(actor.id());
        settlementRepository.save(settlement);
        auditService.transition(actor.id(), "SETTLEMENT_PROPOSED", ENTITY, settlement.getId(), null, SettlementStatus.PROPOSED, reason);
        return SettlementResponse.from(settlement);
    }

    @Transactional
    public SettlementResponse approve(AuthUser actor, UUID id, String reason) {
        SettlementRequest settlement = lock(id);
        Campaign campaign = requireSecondAdmin(actor, settlement);
        States.require(settlement.getSettlementStatus(), SettlementStatus.PROPOSED);
        campaignService.lock(campaign.getId());
        Plan plan = plan(campaign);
        if (plan.amount().compareTo(settlement.getAmount()) != 0) {
            throw new ApplicationException(ErrorCode.CONCURRENT_MODIFICATION,
                    "Settleable amount changed from " + settlement.getAmount().toPlainString() + " to " + plan.amount().toPlainString() + "; reject and propose again");
        }
        Map<Donation, BigDecimal> shares = ProRataAllocation.allocate(plan.amount(), plan.refundable());
        int count = 0;
        for (Map.Entry<Donation, BigDecimal> share : shares.entrySet()) {
            if (share.getValue().signum() == 0) {
                continue;
            }
            Donation donation = share.getKey();
            escrowEngine.recordSettlementRefund(campaign.getId(), settlement.getCurrency(), settlement.getId(), donation.getId(), share.getValue(), actor.id());
            donation.setRefundedAmount(donation.getRefundedAmount().add(share.getValue()));
            DonationStatus previous = donation.getDonationStatus();
            DonationStatus target = donation.refundableAmount().signum() == 0 ? DonationStatus.REFUNDED : DonationStatus.PARTIALLY_REFUNDED;
            donation.setDonationStatus(target);
            auditService.transition(actor.id(), "DONATION_STATUS_CHANGED", "DONATION", donation.getId(), previous, target,
                    "Pro-rata settlement " + settlement.getSettlementCode() + " refunded " + share.getValue().toPlainString());
            count++;
        }
        Instant now = Instant.now();
        settlement.setDecidedBy(actor.id());
        settlement.setDecidedAt(now);
        settlement.setDecisionReason(reason);
        settlement.setExecutedAt(now);
        settlement.setDonationCount(count);
        move(settlement, SettlementStatus.EXECUTED, actor.id(), "SETTLEMENT_EXECUTED", reason);
        return SettlementResponse.from(settlement);
    }

    @Transactional
    public SettlementResponse reject(AuthUser actor, UUID id, String reason) {
        SettlementRequest settlement = lock(id);
        requireSecondAdmin(actor, settlement);
        States.require(settlement.getSettlementStatus(), SettlementStatus.PROPOSED);
        settlement.setDecidedBy(actor.id());
        settlement.setDecidedAt(Instant.now());
        settlement.setDecisionReason(reason);
        move(settlement, SettlementStatus.REJECTED, actor.id(), "SETTLEMENT_REJECTED", reason);
        return SettlementResponse.from(settlement);
    }

    @Transactional
    public SettlementResponse cancel(AuthUser actor, UUID id, String reason) {
        SettlementRequest settlement = lock(id);
        AuthUser.require(actor).requireRole(Role.ADMIN);
        if (!actor.is(settlement.getProposedBy())) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED, "Only the proposing admin can cancel a settlement");
        }
        States.require(settlement.getSettlementStatus(), SettlementStatus.PROPOSED);
        move(settlement, SettlementStatus.CANCELLED, actor.id(), "SETTLEMENT_CANCELLED", reason);
        return SettlementResponse.from(settlement);
    }

    @Transactional(readOnly = true)
    public SettlementResponse get(UUID id) {
        return SettlementResponse.from(load(id));
    }

    @Transactional(readOnly = true)
    public Page<SettlementResponse> byCampaign(UUID campaignId, Pageable pageable) {
        campaignService.load(campaignId);
        return settlementRepository.findByCampaignId(campaignId, pageable).map(SettlementResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<SettlementResponse> list(SettlementStatus status, Pageable pageable) {
        Page<SettlementRequest> page = status == null ? settlementRepository.findAll(pageable) : settlementRepository.findBySettlementStatus(status, pageable);
        return page.map(SettlementResponse::from);
    }

    private Plan plan(Campaign campaign) {
        if (!SETTLEABLE.contains(campaign.getCampaignStatus())) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Only COMPLETED or CANCELLED campaigns can be settled");
        }
        List<Donation> donations = donationRepository.lockByCampaignIdAndDonationStatusIn(campaign.getId(), LOCKED_DONATIONS);
        if (donations.stream().anyMatch(d -> d.getDonationStatus() == DonationStatus.REFUND_PENDING)) {
            throw new ApplicationException(ErrorCode.OPEN_REQUEST_EXISTS, "Resolve open refund requests before settlement");
        }
        EscrowAccount account = escrowAccountRepository.lockByCampaignIdAndCurrency(campaign.getId(), campaign.getCurrency())
                .orElseThrow(() -> new ApplicationException(ErrorCode.ESCROW_NOT_ACTIVE, "Escrow account has not been opened for this campaign"));
        if (account.getEscrowStatus() != EscrowStatus.SETTLEMENT) {
            throw new ApplicationException(ErrorCode.ESCROW_NOT_ACTIVE, "Escrow account is " + account.getEscrowStatus());
        }
        if (account.getTotalReserved().signum() > 0 || account.getTotalRefundReserved().signum() > 0) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Escrow still has reserved funds");
        }
        Map<Donation, BigDecimal> refundable = new LinkedHashMap<>();
        for (Donation donation : donations) {
            if (donation.refundableAmount().signum() > 0) {
                refundable.put(donation, donation.refundableAmount());
            }
        }
        BigDecimal amount = Money.sum(refundable.values().stream()).min(account.getAvailableBalance());
        if (amount.signum() <= 0) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "There is no available balance to settle");
        }
        return new Plan(amount, refundable);
    }

    private Campaign requireSecondAdmin(AuthUser actor, SettlementRequest settlement) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        if (actor.is(settlement.getProposedBy())) {
            throw new ApplicationException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED, "A second admin must decide on the settlement");
        }
        Campaign campaign = campaignService.load(settlement.getCampaignId());
        requireNotCreator(actor, campaign);
        return campaign;
    }

    private void requireNotCreator(AuthUser actor, Campaign campaign) {
        if (actor.is(campaign.getCreatorId())) {
            throw new ApplicationException(ErrorCode.CONFLICT_OF_INTEREST, "Campaign creators cannot settle their own campaign");
        }
    }

    private void move(SettlementRequest settlement, SettlementStatus target, UUID actorId, String action, String reason) {
        SettlementStatus previous = settlement.getSettlementStatus();
        settlement.setSettlementStatus(target);
        auditService.transition(actorId, action, ENTITY, settlement.getId(), previous, target, reason);
    }

    private SettlementRequest load(UUID id) {
        return settlementRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Settlement"));
    }

    private SettlementRequest lock(UUID id) {
        return settlementRepository.findByIdForUpdate(id).orElseThrow(() -> ApplicationException.notFound("Settlement"));
    }

    private record Plan(BigDecimal amount, Map<Donation, BigDecimal> refundable) {
    }
}
