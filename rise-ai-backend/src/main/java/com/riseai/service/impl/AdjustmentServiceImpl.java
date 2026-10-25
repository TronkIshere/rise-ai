package com.riseai.service.impl;

import com.riseai.dto.request.CreateAdjustmentRequest;
import com.riseai.dto.response.AdjustmentResponse;
import com.riseai.entity.Campaign;
import com.riseai.entity.EscrowAdjustment;
import com.riseai.enums.AdjustmentStatus;
import com.riseai.enums.LedgerDirection;
import com.riseai.enums.Role;
import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;
import com.riseai.mapper.RiseEvents;
import com.riseai.repository.AdjustmentRepository;
import com.riseai.repository.EscrowAccountRepository;
import com.riseai.security.AuthUser;
import com.riseai.service.AdjustmentService;
import com.riseai.service.AuditService;
import com.riseai.service.CampaignService;
import com.riseai.service.DomainEventPublisher;
import com.riseai.service.EscrowEngine;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdjustmentServiceImpl implements AdjustmentService {
    private static final String ENTITY = "ESCROW_ADJUSTMENT";

    private final AdjustmentRepository adjustmentRepository;
    private final EscrowAccountRepository escrowAccountRepository;
    private final CampaignService campaignService;
    private final EscrowEngine escrowEngine;
    private final AuditService auditService;
    private final DomainEventPublisher domainEvents;

    @Transactional
    @Override
    public AdjustmentResponse request(AuthUser actor, UUID campaignId, CreateAdjustmentRequest request) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Campaign campaign = campaignService.load(campaignId);
        requireNotCreator(actor, campaign);
        if (request.direction() == LedgerDirection.NEUTRAL) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Adjustment direction must be CREDIT or DEBIT");
        }
        BigDecimal amount = Money.requirePositive(request.amount());
        escrowAccountRepository.findByCampaignIdAndCurrency(campaignId, campaign.getCurrency())
                .orElseThrow(() -> new ApplicationException(ErrorCode.ESCROW_NOT_ACTIVE, "Escrow account has not been opened for this campaign"));
        EscrowAdjustment adjustment = new EscrowAdjustment();
        adjustment.setAdjustmentCode(Codes.generate("ADJ"));
        adjustment.setCampaignId(campaignId);
        adjustment.setCurrency(campaign.getCurrency());
        adjustment.setDirection(request.direction());
        adjustment.setAmount(amount);
        adjustment.setReason(request.reason());
        adjustment.setRequestedBy(actor.id());
        adjustment.setAdjustmentStatus(AdjustmentStatus.REQUESTED);
        adjustmentRepository.save(adjustment);
        auditService.transition(actor.id(), "ADJUSTMENT_REQUESTED", ENTITY, adjustment.getId(), null, AdjustmentStatus.REQUESTED,
                request.direction() + " " + amount.toPlainString() + ": " + request.reason());
        return AdjustmentResponse.from(adjustment);
    }

    @Transactional
    @Override
    public AdjustmentResponse approve(AuthUser actor, UUID id, String reason) {
        EscrowAdjustment adjustment = lock(id);
        requireSecondAdmin(actor, adjustment);
        States.require(adjustment.getAdjustmentStatus(), AdjustmentStatus.REQUESTED);
        escrowEngine.recordAdjustment(adjustment.getCampaignId(), adjustment.getCurrency(), adjustment.getId(), adjustment.getDirection(),
                adjustment.getAmount(), actor.id());
        decide(adjustment, actor, reason);
        move(adjustment, AdjustmentStatus.APPLIED, actor.id(), "ADJUSTMENT_APPLIED", reason);
        domainEvents.publish(RiseEvents.adjustmentApplied(adjustment, actor));
        return AdjustmentResponse.from(adjustment);
    }

    @Transactional
    @Override
    public AdjustmentResponse reject(AuthUser actor, UUID id, String reason) {
        EscrowAdjustment adjustment = lock(id);
        requireSecondAdmin(actor, adjustment);
        States.require(adjustment.getAdjustmentStatus(), AdjustmentStatus.REQUESTED);
        decide(adjustment, actor, reason);
        move(adjustment, AdjustmentStatus.REJECTED, actor.id(), "ADJUSTMENT_REJECTED", reason);
        return AdjustmentResponse.from(adjustment);
    }

    @Transactional
    @Override
    public AdjustmentResponse cancel(AuthUser actor, UUID id, String reason) {
        EscrowAdjustment adjustment = lock(id);
        AuthUser.require(actor).requireRole(Role.ADMIN);
        if (!actor.is(adjustment.getRequestedBy())) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED, "Only the requesting admin can cancel an adjustment");
        }
        States.require(adjustment.getAdjustmentStatus(), AdjustmentStatus.REQUESTED);
        move(adjustment, AdjustmentStatus.CANCELLED, actor.id(), "ADJUSTMENT_CANCELLED", reason);
        return AdjustmentResponse.from(adjustment);
    }

    @Transactional(readOnly = true)
    @Override
    public AdjustmentResponse get(UUID id) {
        return adjustmentRepository.findById(id).map(AdjustmentResponse::from).orElseThrow(() -> ApplicationException.notFound("Adjustment"));
    }

    @Transactional(readOnly = true)
    @Override
    public Page<AdjustmentResponse> byCampaign(UUID campaignId, Pageable pageable) {
        campaignService.load(campaignId);
        return adjustmentRepository.findByCampaignId(campaignId, pageable).map(AdjustmentResponse::from);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<AdjustmentResponse> list(AdjustmentStatus status, Pageable pageable) {
        Page<EscrowAdjustment> page = status == null ? adjustmentRepository.findAll(pageable) : adjustmentRepository.findByAdjustmentStatus(status, pageable);
        return page.map(AdjustmentResponse::from);
    }

    private void requireSecondAdmin(AuthUser actor, EscrowAdjustment adjustment) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        if (actor.is(adjustment.getRequestedBy())) {
            throw new ApplicationException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED, "A second admin must decide on the adjustment");
        }
        requireNotCreator(actor, campaignService.load(adjustment.getCampaignId()));
    }

    private void requireNotCreator(AuthUser actor, Campaign campaign) {
        if (actor.is(campaign.getCreatorId())) {
            throw new ApplicationException(ErrorCode.CONFLICT_OF_INTEREST, "Campaign creators cannot adjust their own campaign escrow");
        }
    }

    private void decide(EscrowAdjustment adjustment, AuthUser actor, String reason) {
        adjustment.setDecidedBy(actor.id());
        adjustment.setDecidedAt(Instant.now());
        adjustment.setDecisionReason(reason);
    }

    private void move(EscrowAdjustment adjustment, AdjustmentStatus target, UUID actorId, String action, String reason) {
        AdjustmentStatus previous = adjustment.getAdjustmentStatus();
        adjustment.setAdjustmentStatus(target);
        auditService.transition(actorId, action, ENTITY, adjustment.getId(), previous, target, reason);
    }

    private EscrowAdjustment lock(UUID id) {
        return adjustmentRepository.findByIdForUpdate(id).orElseThrow(() -> ApplicationException.notFound("Adjustment"));
    }
}
