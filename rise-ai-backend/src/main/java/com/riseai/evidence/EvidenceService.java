package com.riseai.evidence;

import com.riseai.audit.AuditService;
import com.riseai.campaign.Campaign;
import com.riseai.campaign.CampaignService;
import com.riseai.campaign.CampaignStatus;
import com.riseai.common.States;
import com.riseai.common.exception.ApplicationException;
import com.riseai.common.exception.ErrorCode;
import com.riseai.disbursement.DisbursementRepository;
import com.riseai.disbursement.DisbursementRequest;
import com.riseai.disbursement.DisbursementStatus;
import com.riseai.evidence.EvidenceDtos.EvidenceRequest;
import com.riseai.evidence.EvidenceDtos.EvidenceResponse;
import com.riseai.milestone.Milestone;
import com.riseai.milestone.MilestoneRepository;
import com.riseai.milestone.MilestoneService;
import com.riseai.milestone.MilestoneStatus;
import com.riseai.security.AuthUser;
import com.riseai.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EvidenceService {
    private static final Set<CampaignStatus> CAMPAIGN_STATES = EnumSet.of(CampaignStatus.ACTIVE, CampaignStatus.PAUSED, CampaignStatus.COMPLETED);
    private static final Set<MilestoneStatus> MILESTONE_STATES = EnumSet.of(MilestoneStatus.IN_PROGRESS, MilestoneStatus.EVIDENCE_SUBMITTED,
            MilestoneStatus.UNDER_REVIEW, MilestoneStatus.PARTIALLY_DISBURSED, MilestoneStatus.DISBURSED, MilestoneStatus.DISPUTED, MilestoneStatus.COMPLETED);
    private static final Set<DisbursementStatus> CLOSED_REQUESTS = EnumSet.of(DisbursementStatus.REJECTED, DisbursementStatus.CANCELLED);
    private static final String ENTITY = "EVIDENCE";

    private final EvidenceRepository evidenceRepository;
    private final MilestoneRepository milestoneRepository;
    private final DisbursementRepository disbursementRepository;
    private final CampaignService campaignService;
    private final MilestoneService milestoneService;
    private final AuditService auditService;

    @Transactional
    public EvidenceResponse submit(AuthUser actor, UUID milestoneId, UUID disbursementRequestId, EvidenceRequest request) {
        Milestone milestone = milestoneRepository.findByIdForUpdate(milestoneId).orElseThrow(() -> ApplicationException.notFound("Milestone"));
        Campaign campaign = campaignService.loadOwned(actor, milestone.getCampaignId());
        if (!CAMPAIGN_STATES.contains(campaign.getCampaignStatus())) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Evidence can only be submitted for published campaigns");
        }
        if (!MILESTONE_STATES.contains(milestone.getMilestoneStatus())) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Milestone must be started before evidence can be submitted");
        }
        if (request.fileUrl() == null && request.externalReference() == null && (request.description() == null || request.description().isBlank())) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Provide a fileUrl, externalReference or description");
        }
        if (disbursementRequestId != null) {
            DisbursementRequest disbursement = disbursementRepository.findById(disbursementRequestId)
                    .orElseThrow(() -> ApplicationException.notFound("Disbursement request"));
            if (!disbursement.getMilestoneId().equals(milestoneId)) {
                throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Disbursement request does not belong to this milestone");
            }
            if (CLOSED_REQUESTS.contains(disbursement.getRequestStatus())) {
                throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Disbursement request is closed");
            }
        }
        EvidenceRecord evidence = new EvidenceRecord();
        evidence.setCampaignId(campaign.getId());
        evidence.setMilestoneId(milestoneId);
        evidence.setDisbursementRequestId(disbursementRequestId);
        evidence.setSubmittedBy(actor.id());
        evidence.setEvidenceType(request.evidenceType());
        evidence.setTitle(request.title().trim());
        evidence.setDescription(request.description());
        evidence.setFileUrl(request.fileUrl());
        evidence.setFileHash(request.fileHash() == null ? null : request.fileHash().toLowerCase());
        evidence.setExternalReference(request.externalReference());
        evidence.setEvidenceStatus(EvidenceStatus.SUBMITTED);
        evidence.setSubmittedAt(Instant.now());
        evidenceRepository.save(evidence);
        if (milestone.getSubmittedAt() == null) {
            milestone.setSubmittedAt(evidence.getSubmittedAt());
        }
        auditService.transition(actor.id(), "MILESTONE_EVIDENCE_SUBMITTED", ENTITY, evidence.getId(), null, EvidenceStatus.SUBMITTED, request.title());
        milestoneService.refreshStatus(milestone, actor.id(), "Evidence submitted");
        return EvidenceResponse.from(evidence);
    }

    @Transactional
    public EvidenceResponse review(AuthUser actor, UUID id, boolean accept, String note) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        EvidenceRecord evidence = load(id);
        Campaign campaign = campaignService.load(evidence.getCampaignId());
        if (actor.is(evidence.getSubmittedBy()) || actor.is(campaign.getCreatorId())) {
            throw new ApplicationException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        }
        States.require(evidence.getEvidenceStatus(), EvidenceStatus.SUBMITTED);
        EvidenceStatus target = accept ? EvidenceStatus.ACCEPTED : EvidenceStatus.REJECTED;
        evidence.setEvidenceStatus(target);
        evidence.setReviewedBy(actor.id());
        evidence.setReviewedAt(Instant.now());
        evidence.setReviewNote(note);
        auditService.transition(actor.id(), accept ? "EVIDENCE_ACCEPTED" : "EVIDENCE_REJECTED", ENTITY, id, EvidenceStatus.SUBMITTED, target, note);
        Milestone milestone = milestoneRepository.findByIdForUpdate(evidence.getMilestoneId()).orElseThrow(() -> ApplicationException.notFound("Milestone"));
        milestoneService.refreshStatus(milestone, actor.id(), "Evidence reviewed");
        return EvidenceResponse.from(evidence);
    }

    @Transactional(readOnly = true)
    public EvidenceResponse get(AuthUser actor, UUID id) {
        EvidenceRecord evidence = load(id);
        campaignService.loadVisible(evidence.getCampaignId(), actor);
        return EvidenceResponse.from(evidence);
    }

    @Transactional(readOnly = true)
    public Page<EvidenceResponse> listByMilestone(AuthUser actor, UUID milestoneId, Pageable pageable) {
        Milestone milestone = milestoneRepository.findById(milestoneId).orElseThrow(() -> ApplicationException.notFound("Milestone"));
        campaignService.loadVisible(milestone.getCampaignId(), actor);
        return evidenceRepository.findByMilestoneId(milestoneId, pageable).map(EvidenceResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<EvidenceResponse> listByRequest(AuthUser actor, UUID requestId, Pageable pageable) {
        DisbursementRequest request = disbursementRepository.findById(requestId).orElseThrow(() -> ApplicationException.notFound("Disbursement request"));
        campaignService.loadVisible(request.getCampaignId(), actor);
        return evidenceRepository.findByDisbursementRequestId(requestId, pageable).map(EvidenceResponse::from);
    }

    private EvidenceRecord load(UUID id) {
        return evidenceRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Evidence"));
    }
}
