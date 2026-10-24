package com.riseai.milestone;

import com.riseai.audit.AuditDtos.StatusHistoryResponse;
import com.riseai.audit.AuditService;
import com.riseai.campaign.Campaign;
import com.riseai.campaign.CampaignService;
import com.riseai.campaign.CampaignStatus;
import com.riseai.common.Money;
import com.riseai.common.States;
import com.riseai.common.exception.ApplicationException;
import com.riseai.common.exception.ErrorCode;
import com.riseai.disbursement.DisbursementRepository;
import com.riseai.disbursement.DisbursementStatus;
import com.riseai.evidence.EvidenceRepository;
import com.riseai.evidence.EvidenceStatus;
import com.riseai.milestone.MilestoneDtos.MilestoneRequest;
import com.riseai.milestone.MilestoneDtos.MilestoneResponse;
import com.riseai.security.AuthUser;
import com.riseai.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilestoneService {
    public static final Set<MilestoneStatus> ACTIVE_WORK = EnumSet.of(MilestoneStatus.READY, MilestoneStatus.IN_PROGRESS,
            MilestoneStatus.EVIDENCE_SUBMITTED, MilestoneStatus.UNDER_REVIEW, MilestoneStatus.PARTIALLY_DISBURSED,
            MilestoneStatus.DISBURSED, MilestoneStatus.DISPUTED);
    private static final Set<MilestoneStatus> DERIVED = EnumSet.of(MilestoneStatus.IN_PROGRESS, MilestoneStatus.EVIDENCE_SUBMITTED,
            MilestoneStatus.UNDER_REVIEW, MilestoneStatus.PARTIALLY_DISBURSED, MilestoneStatus.DISBURSED);
    private static final Set<MilestoneStatus> DISPUTABLE = EnumSet.of(MilestoneStatus.READY, MilestoneStatus.IN_PROGRESS,
            MilestoneStatus.EVIDENCE_SUBMITTED, MilestoneStatus.UNDER_REVIEW, MilestoneStatus.PARTIALLY_DISBURSED, MilestoneStatus.DISBURSED);
    private static final Set<CampaignStatus> EDITABLE_CAMPAIGN = EnumSet.of(CampaignStatus.DRAFT, CampaignStatus.REJECTED);
    private static final String ENTITY = "MILESTONE";

    private final MilestoneRepository milestoneRepository;
    private final CampaignService campaignService;
    private final EvidenceRepository evidenceRepository;
    private final DisbursementRepository disbursementRepository;
    private final AuditService auditService;

    @Transactional
    public MilestoneResponse create(AuthUser actor, UUID campaignId, MilestoneRequest request) {
        Campaign campaign = campaignService.loadOwned(actor, campaignId);
        requireEditable(campaign);
        BigDecimal amount = validate(request);
        BigDecimal planned = Money.orZero(milestoneRepository.sumPlannedAmount(campaignId));
        if (planned.add(amount).compareTo(campaign.getTargetAmount()) > 0) {
            throw new ApplicationException(ErrorCode.BUDGET_MISMATCH, "Total milestone budgets would exceed the campaign targetAmount");
        }
        Milestone milestone = new Milestone();
        milestone.setCampaignId(campaignId);
        Integer max = milestoneRepository.maxSequence(campaignId);
        milestone.setSequenceNumber(max == null ? 1 : max + 1);
        apply(milestone, request, amount);
        milestone.setMilestoneStatus(MilestoneStatus.PLANNED);
        milestoneRepository.saveAndFlush(milestone);
        auditService.transition(actor.id(), "MILESTONE_CREATED", ENTITY, milestone.getId(), null, MilestoneStatus.PLANNED, null);
        return MilestoneResponse.from(milestone, 0L);
    }

    @Transactional
    public MilestoneResponse update(AuthUser actor, UUID id, MilestoneRequest request) {
        Milestone milestone = load(id);
        Campaign campaign = campaignService.loadOwned(actor, milestone.getCampaignId());
        requireEditable(campaign);
        BigDecimal amount = validate(request);
        BigDecimal others = Money.orZero(milestoneRepository.sumPlannedAmountExcluding(campaign.getId(), id));
        if (others.add(amount).compareTo(campaign.getTargetAmount()) > 0) {
            throw new ApplicationException(ErrorCode.BUDGET_MISMATCH, "Total milestone budgets would exceed the campaign targetAmount");
        }
        apply(milestone, request, amount);
        auditService.record(actor.id(), "MILESTONE_UPDATED", ENTITY, id, null, null, null);
        return MilestoneResponse.from(milestone, evidenceRepository.countByMilestoneId(id));
    }

    @Transactional
    public void delete(AuthUser actor, UUID id) {
        Milestone milestone = load(id);
        Campaign campaign = campaignService.loadOwned(actor, milestone.getCampaignId());
        requireEditable(campaign);
        milestoneRepository.delete(milestone);
        milestoneRepository.flush();
        int sequence = 1;
        for (Milestone remaining : milestoneRepository.findByCampaignIdOrderBySequenceNumberAsc(campaign.getId())) {
            remaining.setSequenceNumber(sequence++);
            milestoneRepository.flush();
        }
        auditService.record(actor.id(), "MILESTONE_DELETED", ENTITY, id, milestone.getMilestoneStatus().name(), null, null);
    }

    @Transactional(readOnly = true)
    public MilestoneResponse get(AuthUser actor, UUID id) {
        Milestone milestone = load(id);
        campaignService.loadVisible(milestone.getCampaignId(), actor);
        return MilestoneResponse.from(milestone, evidenceRepository.countByMilestoneId(id));
    }

    @Transactional(readOnly = true)
    public List<MilestoneResponse> list(AuthUser actor, UUID campaignId) {
        campaignService.loadVisible(campaignId, actor);
        return milestoneRepository.findByCampaignIdOrderBySequenceNumberAsc(campaignId).stream()
                .map(m -> MilestoneResponse.from(m, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(AuthUser actor, UUID id) {
        Milestone milestone = load(id);
        campaignService.loadVisible(milestone.getCampaignId(), actor);
        return auditService.history(ENTITY, id);
    }

    @Transactional
    public MilestoneResponse start(AuthUser actor, UUID id) {
        Milestone milestone = lock(id);
        Campaign campaign = campaignService.loadOwned(actor, milestone.getCampaignId());
        States.require(campaign.getCampaignStatus(), CampaignStatus.ACTIVE);
        States.require(milestone.getMilestoneStatus(), MilestoneStatus.READY);
        move(milestone, MilestoneStatus.IN_PROGRESS, actor.id(), "MILESTONE_STARTED", null);
        return MilestoneResponse.from(milestone, evidenceRepository.countByMilestoneId(id));
    }

    @Transactional
    public MilestoneResponse complete(AuthUser actor, UUID id, String reason) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Milestone milestone = lock(id);
        Campaign campaign = campaignService.load(milestone.getCampaignId());
        if (actor.is(campaign.getCreatorId())) {
            throw new ApplicationException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        }
        States.require(milestone.getMilestoneStatus(), MilestoneStatus.DISBURSED, MilestoneStatus.PARTIALLY_DISBURSED);
        requireNoOpenRequests(milestone);
        milestone.setCompletedAt(Instant.now());
        move(milestone, MilestoneStatus.COMPLETED, actor.id(), "MILESTONE_COMPLETED", reason);
        promoteNext(campaign, actor.id());
        return MilestoneResponse.from(milestone, evidenceRepository.countByMilestoneId(id));
    }

    @Transactional
    public MilestoneResponse cancel(AuthUser actor, UUID id, String reason) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Milestone milestone = lock(id);
        Campaign campaign = campaignService.load(milestone.getCampaignId());
        States.require(milestone.getMilestoneStatus(), MilestoneStatus.PLANNED, MilestoneStatus.READY, MilestoneStatus.IN_PROGRESS, MilestoneStatus.EVIDENCE_SUBMITTED);
        requireNoOpenRequests(milestone);
        move(milestone, MilestoneStatus.CANCELLED, actor.id(), "MILESTONE_CANCELLED", reason);
        promoteNext(campaign, actor.id());
        return MilestoneResponse.from(milestone, evidenceRepository.countByMilestoneId(id));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshStatus(Milestone milestone, UUID actorId, String reason) {
        if (!DERIVED.contains(milestone.getMilestoneStatus())) {
            return;
        }
        MilestoneStatus target = derive(milestone);
        if (target != milestone.getMilestoneStatus()) {
            move(milestone, target, actorId, "MILESTONE_STATUS_CHANGED", reason);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markDisputed(UUID milestoneId, UUID actorId, String reason) {
        Milestone milestone = lock(milestoneId);
        if (DISPUTABLE.contains(milestone.getMilestoneStatus())) {
            milestone.setPreDisputeStatus(milestone.getMilestoneStatus());
            move(milestone, MilestoneStatus.DISPUTED, actorId, "MILESTONE_DISPUTED", reason);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreAfterDispute(UUID milestoneId, UUID actorId, String reason) {
        Milestone milestone = lock(milestoneId);
        if (milestone.getMilestoneStatus() != MilestoneStatus.DISPUTED) {
            return;
        }
        MilestoneStatus previous = milestone.getPreDisputeStatus() == null ? MilestoneStatus.IN_PROGRESS : milestone.getPreDisputeStatus();
        MilestoneStatus restored = DERIVED.contains(previous) ? derive(milestone) : previous;
        milestone.setPreDisputeStatus(null);
        move(milestone, restored, actorId, "MILESTONE_DISPUTE_RELEASED", reason);
    }

    private MilestoneStatus derive(Milestone milestone) {
        if (disbursementRepository.existsByMilestoneIdAndRequestStatusIn(milestone.getId(), DisbursementStatus.OPEN)) {
            return MilestoneStatus.UNDER_REVIEW;
        }
        if (milestone.getReleasedAmount().compareTo(milestone.getPlannedAmount()) >= 0) {
            return MilestoneStatus.DISBURSED;
        }
        if (milestone.getReleasedAmount().signum() > 0) {
            return MilestoneStatus.PARTIALLY_DISBURSED;
        }
        if (evidenceRepository.existsByMilestoneIdAndEvidenceStatusIn(milestone.getId(), EvidenceStatus.VALID)) {
            return MilestoneStatus.EVIDENCE_SUBMITTED;
        }
        return MilestoneStatus.IN_PROGRESS;
    }

    private void promoteNext(Campaign campaign, UUID actorId) {
        if (campaign.getCampaignStatus() != CampaignStatus.ACTIVE || milestoneRepository.existsByCampaignIdAndMilestoneStatusIn(campaign.getId(), ACTIVE_WORK)) {
            return;
        }
        milestoneRepository.findFirstByCampaignIdAndMilestoneStatusOrderBySequenceNumberAsc(campaign.getId(), MilestoneStatus.PLANNED)
                .ifPresent(next -> move(next, MilestoneStatus.READY, actorId, "MILESTONE_READY", "Previous milestone closed"));
    }

    private void requireNoOpenRequests(Milestone milestone) {
        if (milestone.getReservedAmount().signum() > 0
                || disbursementRepository.existsByMilestoneIdAndRequestStatusIn(milestone.getId(), DisbursementStatus.OPEN)) {
            throw new ApplicationException(ErrorCode.OPEN_REQUEST_EXISTS, "Milestone has open disbursement requests or reserved funds");
        }
    }

    private void requireEditable(Campaign campaign) {
        if (!EDITABLE_CAMPAIGN.contains(campaign.getCampaignStatus())) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Milestones can only be changed while the campaign is DRAFT or REJECTED");
        }
    }

    private BigDecimal validate(MilestoneRequest request) {
        if (!request.dueAt().isAfter(request.startAt())) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "dueAt must be after startAt");
        }
        return Money.requirePositive(request.plannedAmount());
    }

    private void apply(Milestone milestone, MilestoneRequest request, BigDecimal amount) {
        milestone.setTitle(request.title().trim());
        milestone.setDescription(request.description());
        milestone.setPlannedAmount(amount);
        milestone.setStartAt(request.startAt());
        milestone.setDueAt(request.dueAt());
        milestone.setReleaseConditions(request.releaseConditions());
        milestone.setExpectedDeliverables(request.expectedDeliverables());
    }

    private void move(Milestone milestone, MilestoneStatus target, UUID actorId, String action, String reason) {
        MilestoneStatus previous = milestone.getMilestoneStatus();
        milestone.setMilestoneStatus(target);
        auditService.transition(actorId, action, ENTITY, milestone.getId(), previous, target, reason);
    }

    private Milestone load(UUID id) {
        return milestoneRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Milestone"));
    }

    private Milestone lock(UUID id) {
        return milestoneRepository.findByIdForUpdate(id).orElseThrow(() -> ApplicationException.notFound("Milestone"));
    }
}
