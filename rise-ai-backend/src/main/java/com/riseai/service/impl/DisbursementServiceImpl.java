package com.riseai.service.impl;

import com.riseai.configuration.RiseProperties;
import com.riseai.dto.request.CreateDisbursementRequest;
import com.riseai.dto.request.EvidenceRequest;
import com.riseai.dto.response.DisbursementResponse;
import com.riseai.dto.response.EvidenceResponse;
import com.riseai.dto.response.StatusHistoryResponse;
import com.riseai.entity.Campaign;
import com.riseai.entity.DisbursementRequest;
import com.riseai.entity.EscrowLedgerEntry;
import com.riseai.entity.Milestone;
import com.riseai.enums.CampaignStatus;
import com.riseai.enums.DisbursementStatus;
import com.riseai.enums.DisputeStatus;
import com.riseai.enums.EvidenceStatus;
import com.riseai.enums.MilestoneStatus;
import com.riseai.enums.Role;
import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;
import com.riseai.mapper.RiseEvents;
import com.riseai.repository.DisbursementRepository;
import com.riseai.repository.DisputeRepository;
import com.riseai.repository.EvidenceRepository;
import com.riseai.repository.MilestoneRepository;
import com.riseai.security.AuthUser;
import com.riseai.service.AuditService;
import com.riseai.service.CampaignService;
import com.riseai.service.ChainEscrowRecorder;
import com.riseai.service.DisbursementService;
import com.riseai.service.DomainEventPublisher;
import com.riseai.service.EscrowEngine;
import com.riseai.service.EvidenceService;
import com.riseai.service.MilestoneService;
import com.riseai.util.Codes;
import com.riseai.util.Money;
import com.riseai.util.States;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisbursementServiceImpl implements DisbursementService {
    private static final Set<MilestoneStatus> REQUESTABLE = EnumSet.of(MilestoneStatus.IN_PROGRESS, MilestoneStatus.EVIDENCE_SUBMITTED, MilestoneStatus.PARTIALLY_DISBURSED);
    private static final String ENTITY = "DISBURSEMENT_REQUEST";

    private final DisbursementRepository disbursementRepository;
    private final MilestoneRepository milestoneRepository;
    private final EvidenceRepository evidenceRepository;
    private final DisputeRepository disputeRepository;
    private final CampaignService campaignService;
    private final MilestoneService milestoneService;
    private final EvidenceService evidenceService;
    private final EscrowEngine escrowEngine;
    private final ChainEscrowRecorder chainEscrow;
    private final AuditService auditService;
    private final DomainEventPublisher domainEvents;
    private final RiseProperties properties;

    @Transactional
    @Override
    public DisbursementResponse create(AuthUser actor, UUID milestoneId, CreateDisbursementRequest request) {
        return create(actor, milestoneId, request, null);
    }

    @Transactional
    @Override
    public DisbursementResponse create(AuthUser actor, UUID milestoneId, CreateDisbursementRequest request, String idempotencyKey) {
        AuthUser.require(actor);
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 100)) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Idempotency-Key must be 1-100 characters");
        }
        Milestone milestone = lockMilestone(milestoneId);
        Campaign campaign = campaignService.loadOwned(actor, milestone.getCampaignId());
        if (idempotencyKey != null) {
            Optional<DisbursementRequest> existing = disbursementRepository.findByRequestedByAndIdempotencyKey(actor.id(), idempotencyKey);
            if (existing.isPresent()) {
                DisbursementRequest disbursement = existing.get();
                if (!disbursement.getMilestoneId().equals(milestoneId) || disbursement.getRequestedAmount().compareTo(Money.requirePositive(request.amount())) != 0) {
                    throw new ApplicationException(ErrorCode.DUPLICATE_OPERATION, "Idempotency-Key was already used for a different disbursement request");
                }
                return DisbursementResponse.from(disbursement);
            }
        }
        if (campaign.getCampaignStatus() != CampaignStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Campaign must be ACTIVE to request a disbursement");
        }
        if (disbursementRepository.existsByMilestoneIdAndRequestStatusIn(milestoneId, DisbursementStatus.OPEN)) {
            throw new ApplicationException(ErrorCode.OPEN_REQUEST_EXISTS, "Milestone already has an open disbursement request");
        }
        if (!REQUESTABLE.contains(milestone.getMilestoneStatus())) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Milestone in status " + milestone.getMilestoneStatus() + " does not accept disbursement requests");
        }
        BigDecimal amount = Money.requirePositive(request.amount());
        if (amount.compareTo(milestone.remainingAmount()) > 0) {
            throw new ApplicationException(ErrorCode.MILESTONE_BUDGET_EXCEEDED,
                    "Requested " + amount.toPlainString() + " exceeds remaining milestone budget " + milestone.remainingAmount().toPlainString());
        }
        if (!evidenceRepository.existsByMilestoneIdAndEvidenceStatusIn(milestoneId, EvidenceStatus.VALID)) {
            throw new ApplicationException(ErrorCode.EVIDENCE_REQUIRED, "Submit evidence for the milestone before requesting funds");
        }
        DisbursementRequest disbursement = new DisbursementRequest();
        disbursement.setRequestCode(Codes.generate("DSB"));
        disbursement.setCampaignId(campaign.getId());
        disbursement.setMilestoneId(milestoneId);
        disbursement.setRequestedBy(actor.id());
        disbursement.setRequestedAmount(amount);
        disbursement.setCurrency(campaign.getCurrency());
        disbursement.setRequestReason(request.reason());
        disbursement.setIdempotencyKey(idempotencyKey);
        disbursement.setRequestStatus(DisbursementStatus.SUBMITTED);
        disbursement.setSubmittedAt(Instant.now());
        disbursementRepository.save(disbursement);
        escrowEngine.reserve(campaign.getId(), campaign.getCurrency(), milestoneId, disbursement.getId(), amount, actor.id());
        milestone.setReservedAmount(milestone.getReservedAmount().add(amount));
        auditService.transition(actor.id(), "DISBURSEMENT_REQUESTED", ENTITY, disbursement.getId(), null, DisbursementStatus.SUBMITTED, request.reason());
        domainEvents.publish(RiseEvents.disbursementRequested(disbursement, milestone, actor));
        milestoneService.refreshStatus(milestone, actor.id(), "Disbursement requested");
        return DisbursementResponse.from(disbursement);
    }

    @Transactional
    @Override
    public DisbursementResponse startReview(AuthUser actor, UUID id) {
        DisbursementRequest disbursement = lock(id);
        requireReviewer(actor, disbursement);
        States.require(disbursement.getRequestStatus(), DisbursementStatus.SUBMITTED);
        disbursement.setReviewedBy(actor.id());
        move(disbursement, DisbursementStatus.UNDER_REVIEW, actor.id(), "DISBURSEMENT_REVIEW_STARTED", null);
        return DisbursementResponse.from(disbursement);
    }

    @Transactional
    @Override
    public DisbursementResponse requestMoreEvidence(AuthUser actor, UUID id, String reason) {
        DisbursementRequest disbursement = lock(id);
        requireReviewer(actor, disbursement);
        States.require(disbursement.getRequestStatus(), DisbursementStatus.UNDER_REVIEW);
        disbursement.setDecisionReason(reason);
        move(disbursement, DisbursementStatus.NEEDS_MORE_EVIDENCE, actor.id(), "DISBURSEMENT_EVIDENCE_REQUESTED", reason);
        return DisbursementResponse.from(disbursement);
    }

    @Transactional
    @Override
    public DisbursementResponse resubmit(AuthUser actor, UUID id, String note) {
        DisbursementRequest disbursement = lock(id);
        campaignService.loadOwned(actor, disbursement.getCampaignId());
        States.require(disbursement.getRequestStatus(), DisbursementStatus.NEEDS_MORE_EVIDENCE);
        move(disbursement, DisbursementStatus.UNDER_REVIEW, actor.id(), "DISBURSEMENT_RESUBMITTED", note);
        return DisbursementResponse.from(disbursement);
    }

    @Transactional
    @Override
    public DisbursementResponse approve(AuthUser actor, UUID id, String reason) {
        DisbursementRequest disbursement = lock(id);
        Campaign campaign = requireReviewer(actor, disbursement);
        States.require(disbursement.getRequestStatus(), DisbursementStatus.UNDER_REVIEW);
        if (campaign.getCampaignStatus() != CampaignStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Campaign must be ACTIVE to approve a disbursement");
        }
        if (!evidenceRepository.existsByMilestoneIdAndEvidenceStatusIn(disbursement.getMilestoneId(), EnumSet.of(EvidenceStatus.ACCEPTED))) {
            throw new ApplicationException(ErrorCode.EVIDENCE_REQUIRED, "At least one accepted evidence record is required for approval");
        }
        requireNoOpenDispute(disbursement);
        disbursement.setReviewedBy(actor.id());
        disbursement.setReviewedAt(Instant.now());
        disbursement.setDecisionReason(reason);
        move(disbursement, DisbursementStatus.APPROVED, actor.id(), "DISBURSEMENT_APPROVED", reason);
        long acceptedEvidence = evidenceRepository.countByMilestoneIdAndEvidenceStatus(disbursement.getMilestoneId(), EvidenceStatus.ACCEPTED);
        domainEvents.publish(RiseEvents.disbursementApproved(disbursement, acceptedEvidence, actor));
        return DisbursementResponse.from(disbursement);
    }

    @Transactional
    @Override
    public DisbursementResponse reject(AuthUser actor, UUID id, String reason) {
        DisbursementRequest disbursement = lock(id);
        requireReviewer(actor, disbursement);
        States.require(disbursement.getRequestStatus(), DisbursementStatus.SUBMITTED, DisbursementStatus.UNDER_REVIEW,
                DisbursementStatus.NEEDS_MORE_EVIDENCE, DisbursementStatus.APPROVED);
        disbursement.setReviewedBy(actor.id());
        disbursement.setReviewedAt(Instant.now());
        disbursement.setDecisionReason(reason);
        closeAndReturnReservation(disbursement, DisbursementStatus.REJECTED, actor.id(), "DISBURSEMENT_REJECTED", reason);
        domainEvents.publish(RiseEvents.disbursementRejected(disbursement, actor));
        return DisbursementResponse.from(disbursement);
    }

    @Transactional
    @Override
    public DisbursementResponse cancel(AuthUser actor, UUID id, String reason) {
        DisbursementRequest disbursement = lock(id);
        campaignService.loadOwned(actor, disbursement.getCampaignId());
        States.require(disbursement.getRequestStatus(), DisbursementStatus.SUBMITTED, DisbursementStatus.UNDER_REVIEW, DisbursementStatus.NEEDS_MORE_EVIDENCE);
        closeAndReturnReservation(disbursement, DisbursementStatus.CANCELLED, actor.id(), "DISBURSEMENT_CANCELLED", reason);
        return DisbursementResponse.from(disbursement);
    }

    @Transactional
    @Override
    public DisbursementResponse release(AuthUser actor, UUID id) {
        DisbursementRequest disbursement = lock(id);
        if (disbursement.getRequestStatus() == DisbursementStatus.RELEASED) {
            throw new ApplicationException(ErrorCode.DUPLICATE_OPERATION, "Disbursement has already been released");
        }
        Campaign campaign = requireReviewer(actor, disbursement);
        States.require(disbursement.getRequestStatus(), DisbursementStatus.APPROVED);
        if (campaign.getCampaignStatus() != CampaignStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Campaign must be ACTIVE to release funds");
        }
        requireNoOpenDispute(disbursement);
        Milestone milestone = lockMilestone(disbursement.getMilestoneId());
        BigDecimal amount = disbursement.getRequestedAmount();
        if (milestone.getReleasedAmount().add(amount).compareTo(milestone.getPlannedAmount()) > 0) {
            throw new ApplicationException(ErrorCode.MILESTONE_BUDGET_EXCEEDED, "Release would exceed the milestone budget");
        }
        if (milestone.getReservedAmount().compareTo(amount) < 0) {
            throw new IllegalStateException("Milestone reservation is lower than the approved amount");
        }
        if (chainEscrow.releaseRequiresChain()) {
            chainEscrow.requestRelease(campaign.getId(), disbursement.getId(), actor.id());
            return DisbursementResponse.from(disbursement);
        }
        return applyRelease(disbursement, campaign, milestone, actor);
    }

    @Transactional
    @Override
    public void completeChainRelease(UUID id, UUID actorId) {
        DisbursementRequest disbursement = lock(id);
        if (disbursement.getRequestStatus() == DisbursementStatus.RELEASED) {
            return;
        }
        States.require(disbursement.getRequestStatus(), DisbursementStatus.APPROVED);
        Campaign campaign = campaignService.load(disbursement.getCampaignId());
        applyRelease(disbursement, campaign, lockMilestone(disbursement.getMilestoneId()), new AuthUser(actorId, null, Set.of(Role.ADMIN)));
    }

    private DisbursementResponse applyRelease(DisbursementRequest disbursement, Campaign campaign, Milestone milestone, AuthUser actor) {
        BigDecimal amount = disbursement.getRequestedAmount();
        EscrowLedgerEntry entry = escrowEngine.release(campaign.getId(), disbursement.getCurrency(), milestone.getId(), disbursement.getId(), amount, actor.id());
        Instant now = Instant.now();
        milestone.setReservedAmount(milestone.getReservedAmount().subtract(amount));
        milestone.setReleasedAmount(milestone.getReleasedAmount().add(amount));
        milestone.setReviewedAt(now);
        disbursement.setReleasedAt(now);
        disbursement.setReleasedBy(actor.id());
        move(disbursement, DisbursementStatus.RELEASED, actor.id(), "DISBURSEMENT_RELEASED", "Simulated release; no real transfer was executed");
        domainEvents.publish(RiseEvents.disbursementReleased(disbursement, entry, actor));
        milestoneService.refreshStatus(milestone, actor.id(), "Funds released");
        return DisbursementResponse.from(disbursement);
    }

    @Transactional
    @Override
    public EvidenceResponse addEvidence(AuthUser actor, UUID id, EvidenceRequest request) {
        DisbursementRequest disbursement = disbursementRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Disbursement request"));
        return evidenceService.submit(actor, disbursement.getMilestoneId(), id, request);
    }

    @Transactional(readOnly = true)
    @Override
    public DisbursementResponse get(AuthUser actor, UUID id) {
        DisbursementRequest disbursement = load(id);
        campaignService.loadVisible(disbursement.getCampaignId(), actor);
        return DisbursementResponse.from(disbursement);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<DisbursementResponse> listByCampaign(AuthUser actor, UUID campaignId, Pageable pageable) {
        campaignService.loadVisible(campaignId, actor);
        return disbursementRepository.findByCampaignId(campaignId, pageable).map(DisbursementResponse::from);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<DisbursementResponse> adminQueue(DisbursementStatus status, Pageable pageable) {
        Page<DisbursementRequest> page = status == null ? disbursementRepository.findAll(pageable) : disbursementRepository.findByRequestStatus(status, pageable);
        return page.map(DisbursementResponse::from);
    }

    @Transactional(readOnly = true)
    @Override
    public List<StatusHistoryResponse> history(AuthUser actor, UUID id) {
        DisbursementRequest disbursement = load(id);
        campaignService.loadVisible(disbursement.getCampaignId(), actor);
        return auditService.history(ENTITY, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void holdForDispute(UUID milestoneId, UUID actorId, String reason) {
        for (DisbursementRequest disbursement : disbursementRepository.lockByMilestoneAndStatuses(milestoneId, DisbursementStatus.HOLDABLE)) {
            disbursement.setPreDisputeStatus(disbursement.getRequestStatus());
            move(disbursement, DisbursementStatus.DISPUTED, actorId, "DISBURSEMENT_HELD_FOR_DISPUTE", reason);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void rejectDisputed(UUID milestoneId, UUID onlyRequestId, UUID actorId, String reason) {
        for (DisbursementRequest disbursement : disbursementRepository.lockByMilestoneAndStatuses(milestoneId, EnumSet.of(DisbursementStatus.DISPUTED))) {
            if (onlyRequestId == null || onlyRequestId.equals(disbursement.getId())) {
                disbursement.setPreDisputeStatus(null);
                disbursement.setReviewedBy(actorId);
                disbursement.setReviewedAt(Instant.now());
                disbursement.setDecisionReason(reason);
                closeAndReturnReservation(disbursement, DisbursementStatus.REJECTED, actorId, "DISBURSEMENT_REJECTED_BY_DISPUTE", reason);
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void restoreDisputed(UUID milestoneId, UUID actorId, String reason) {
        for (DisbursementRequest disbursement : disbursementRepository.lockByMilestoneAndStatuses(milestoneId, EnumSet.of(DisbursementStatus.DISPUTED))) {
            DisbursementStatus restored = disbursement.getPreDisputeStatus() == null ? DisbursementStatus.SUBMITTED : disbursement.getPreDisputeStatus();
            disbursement.setPreDisputeStatus(null);
            move(disbursement, restored, actorId, "DISBURSEMENT_DISPUTE_RELEASED", reason);
        }
    }

    private void closeAndReturnReservation(DisbursementRequest disbursement, DisbursementStatus target, UUID actorId, String action, String reason) {
        Milestone milestone = lockMilestone(disbursement.getMilestoneId());
        BigDecimal amount = disbursement.getRequestedAmount();
        escrowEngine.cancelReservation(disbursement.getCampaignId(), disbursement.getCurrency(), milestone.getId(), disbursement.getId(), amount, actorId);
        BigDecimal reserved = milestone.getReservedAmount().subtract(amount);
        if (reserved.signum() < 0) {
            throw new IllegalStateException("Milestone reserved amount would become negative");
        }
        milestone.setReservedAmount(reserved);
        milestone.setReviewedAt(Instant.now());
        move(disbursement, target, actorId, action, reason);
        milestoneService.refreshStatus(milestone, actorId, reason);
    }

    private Campaign requireReviewer(AuthUser actor, DisbursementRequest disbursement) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Campaign campaign = campaignService.load(disbursement.getCampaignId());
        if (actor.is(disbursement.getRequestedBy()) || actor.is(campaign.getCreatorId())) {
            throw new ApplicationException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        }
        return campaign;
    }

    private void requireNoOpenDispute(DisbursementRequest disbursement) {
        if (properties.escrow().disputeAutoHold()
                && disputeRepository.existsByMilestoneIdAndDisputeStatusIn(disbursement.getMilestoneId(), DisputeStatus.OPEN_STATES)) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "An open dispute affects this milestone");
        }
    }

    private void move(DisbursementRequest disbursement, DisbursementStatus target, UUID actorId, String action, String reason) {
        DisbursementStatus previous = disbursement.getRequestStatus();
        disbursement.setRequestStatus(target);
        auditService.transition(actorId, action, ENTITY, disbursement.getId(), previous, target, reason);
    }

    private DisbursementRequest load(UUID id) {
        return disbursementRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Disbursement request"));
    }

    private DisbursementRequest lock(UUID id) {
        return disbursementRepository.findByIdForUpdate(id).orElseThrow(() -> ApplicationException.notFound("Disbursement request"));
    }

    private Milestone lockMilestone(UUID id) {
        return milestoneRepository.findByIdForUpdate(id).orElseThrow(() -> ApplicationException.notFound("Milestone"));
    }
}
