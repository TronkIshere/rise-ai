package com.riseai.dispute;

import com.riseai.audit.AuditDtos.StatusHistoryResponse;
import com.riseai.audit.AuditService;
import com.riseai.campaign.Campaign;
import com.riseai.campaign.CampaignService;
import com.riseai.common.Codes;
import com.riseai.common.States;
import com.riseai.common.exception.ApplicationException;
import com.riseai.common.exception.ErrorCode;
import com.riseai.config.RiseProperties;
import com.riseai.disbursement.DisbursementRepository;
import com.riseai.disbursement.DisbursementRequest;
import com.riseai.disbursement.DisbursementService;
import com.riseai.dispute.DisputeDtos.DisputeMessageResponse;
import com.riseai.dispute.DisputeDtos.DisputeResponse;
import com.riseai.dispute.DisputeDtos.OpenDisputeRequest;
import com.riseai.dispute.DisputeDtos.ResolveDisputeRequest;
import com.riseai.donation.DonationRepository;
import com.riseai.donation.DonationStatus;
import com.riseai.milestone.Milestone;
import com.riseai.milestone.MilestoneRepository;
import com.riseai.milestone.MilestoneService;
import com.riseai.outbox.DomainEventPublisher;
import com.riseai.outbox.RiseEvents;
import com.riseai.security.AuthUser;
import com.riseai.user.Role;
import com.riseai.user.User;
import com.riseai.user.UserRepository;
import com.riseai.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisputeService {
    private static final String ENTITY = "DISPUTE";

    private final DisputeRepository disputeRepository;
    private final DisputeMessageRepository messageRepository;
    private final DisbursementRepository disbursementRepository;
    private final MilestoneRepository milestoneRepository;
    private final DonationRepository donationRepository;
    private final UserRepository userRepository;
    private final CampaignService campaignService;
    private final DisbursementService disbursementService;
    private final MilestoneService milestoneService;
    private final AuditService auditService;
    private final DomainEventPublisher domainEvents;
    private final RiseProperties properties;

    @Transactional
    public DisputeResponse open(AuthUser actor, UUID campaignId, OpenDisputeRequest request) {
        AuthUser.require(actor);
        Campaign campaign = campaignService.loadVisible(campaignId, actor);
        if (campaign.getPublishedAt() == null) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Disputes can only be opened for published campaigns");
        }
        if (actor.is(campaign.getCreatorId())) {
            throw new ApplicationException(ErrorCode.DISPUTE_NOT_ELIGIBLE, "Campaign creators cannot open disputes against their own campaign");
        }
        if (!actor.isAdmin() && !donationRepository.existsByCampaignIdAndDonorIdAndDonationStatusIn(campaignId, actor.id(), DonationStatus.COUNTED)) {
            throw new ApplicationException(ErrorCode.DISPUTE_NOT_ELIGIBLE, "Only donors with a confirmed donation to this campaign can open a dispute");
        }
        UUID milestoneId = request.milestoneId();
        UUID requestId = request.disbursementRequestId();
        if (requestId != null) {
            DisbursementRequest disbursement = disbursementRepository.findById(requestId)
                    .filter(r -> r.getCampaignId().equals(campaignId))
                    .orElseThrow(() -> ApplicationException.notFound("Disbursement request"));
            if (milestoneId != null && !milestoneId.equals(disbursement.getMilestoneId())) {
                throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Disbursement request does not belong to the given milestone");
            }
            milestoneId = disbursement.getMilestoneId();
        }
        if (milestoneId != null) {
            milestoneRepository.findById(milestoneId)
                    .filter(m -> m.getCampaignId().equals(campaignId))
                    .orElseThrow(() -> ApplicationException.notFound("Milestone"));
        }
        if (disputeRepository.countOpenDuplicates(actor.id(), campaignId, milestoneId, requestId, DisputeStatus.OPEN_STATES) > 0) {
            throw new ApplicationException(ErrorCode.OPEN_REQUEST_EXISTS, "You already have an open dispute for this target");
        }
        Dispute dispute = new Dispute();
        dispute.setDisputeCode(Codes.generate("DSP"));
        dispute.setCampaignId(campaignId);
        dispute.setMilestoneId(milestoneId);
        dispute.setDisbursementRequestId(requestId);
        dispute.setOpenedBy(actor.id());
        dispute.setDisputeType(request.disputeType());
        dispute.setTitle(request.title().trim());
        dispute.setDescription(request.description());
        dispute.setSupportingReferences(request.supportingReferences() == null ? new ArrayList<>() : new ArrayList<>(request.supportingReferences()));
        dispute.setDisputeStatus(DisputeStatus.OPEN);
        disputeRepository.save(dispute);
        auditService.transition(actor.id(), "DISPUTE_OPENED", ENTITY, dispute.getId(), null, DisputeStatus.OPEN, request.title());
        domainEvents.publish(RiseEvents.disputeOpened(dispute, actor));
        if (milestoneId != null && properties.escrow().disputeAutoHold()) {
            String reason = "Held by dispute " + dispute.getDisputeCode();
            disbursementService.holdForDispute(milestoneId, actor.id(), reason);
            milestoneService.markDisputed(milestoneId, actor.id(), reason);
        }
        return DisputeResponse.from(dispute);
    }

    @Transactional
    public DisputeResponse assign(AuthUser actor, UUID id, UUID reviewerId) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Dispute dispute = lock(id);
        States.require(dispute.getDisputeStatus(), DisputeStatus.OPEN_STATES);
        Campaign campaign = campaignService.load(dispute.getCampaignId());
        User reviewer = userRepository.findById(reviewerId).orElseThrow(() -> ApplicationException.notFound("Reviewer"));
        if (!reviewer.getRoles().contains(Role.ADMIN) || reviewer.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Reviewer must be an active ADMIN");
        }
        if (reviewerId.equals(dispute.getOpenedBy()) || reviewerId.equals(campaign.getCreatorId())) {
            throw new ApplicationException(ErrorCode.CONFLICT_OF_INTEREST, "Reviewer cannot be a party to the dispute");
        }
        dispute.setAssignedTo(reviewerId);
        if (dispute.getDisputeStatus() == DisputeStatus.OPEN) {
            move(dispute, DisputeStatus.UNDER_REVIEW, actor.id(), "DISPUTE_ASSIGNED", "Assigned to " + reviewerId);
        } else {
            auditService.record(actor.id(), "DISPUTE_REASSIGNED", ENTITY, id, null, null, AuditService.meta("reviewerId", reviewerId));
        }
        return DisputeResponse.from(dispute);
    }

    @Transactional
    public DisputeResponse resolve(AuthUser actor, UUID id, ResolveDisputeRequest request) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        Dispute dispute = lock(id);
        States.require(dispute.getDisputeStatus(), DisputeStatus.UNDER_REVIEW);
        Campaign campaign = campaignService.load(dispute.getCampaignId());
        if (actor.is(dispute.getOpenedBy()) || actor.is(campaign.getCreatorId())) {
            throw new ApplicationException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        }
        if (!actor.is(dispute.getAssignedTo())) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED, "Only the assigned reviewer can resolve this dispute");
        }
        boolean upheld = request.outcome() == ResolutionOutcome.UPHELD;
        dispute.setResolution(request.resolution());
        dispute.setResolvedBy(actor.id());
        dispute.setResolvedAt(Instant.now());
        move(dispute, upheld ? DisputeStatus.RESOLVED_UPHELD : DisputeStatus.RESOLVED_DISMISSED, actor.id(), "DISPUTE_RESOLVED", request.resolution());
        domainEvents.publish(RiseEvents.disputeResolved(dispute, actor));
        releaseHolds(dispute, actor.id(), upheld);
        return DisputeResponse.from(dispute);
    }

    @Transactional
    public DisputeResponse withdraw(AuthUser actor, UUID id, String reason) {
        Dispute dispute = lock(id);
        if (!AuthUser.require(actor).is(dispute.getOpenedBy())) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED, "Only the dispute opener can withdraw it");
        }
        States.require(dispute.getDisputeStatus(), DisputeStatus.OPEN_STATES);
        move(dispute, DisputeStatus.WITHDRAWN, actor.id(), "DISPUTE_WITHDRAWN", reason);
        releaseHolds(dispute, actor.id(), false);
        return DisputeResponse.from(dispute);
    }

    @Transactional
    public DisputeMessageResponse addMessage(AuthUser actor, UUID id, String text) {
        Dispute dispute = load(id);
        requireParticipant(actor, dispute);
        States.require(dispute.getDisputeStatus(), DisputeStatus.OPEN_STATES);
        DisputeMessage message = new DisputeMessage();
        message.setDisputeId(id);
        message.setAuthorId(actor.id());
        message.setMessage(text);
        messageRepository.saveAndFlush(message);
        auditService.record(actor.id(), "DISPUTE_MESSAGE_ADDED", ENTITY, id, null, null, AuditService.meta("messageId", message.getId()));
        return DisputeMessageResponse.from(message);
    }

    @Transactional(readOnly = true)
    public Page<DisputeMessageResponse> messages(AuthUser actor, UUID id, Pageable pageable) {
        requireParticipant(actor, load(id));
        return messageRepository.findByDisputeId(id, pageable).map(DisputeMessageResponse::from);
    }

    @Transactional(readOnly = true)
    public DisputeResponse get(AuthUser actor, UUID id) {
        Dispute dispute = load(id);
        requireParticipant(actor, dispute);
        return DisputeResponse.from(dispute);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> history(AuthUser actor, UUID id) {
        requireParticipant(actor, load(id));
        return auditService.history(ENTITY, id);
    }

    @Transactional(readOnly = true)
    public Page<DisputeResponse> mine(AuthUser actor, Pageable pageable) {
        return disputeRepository.findByOpenedBy(AuthUser.require(actor).id(), pageable).map(DisputeResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<DisputeResponse> byCampaign(AuthUser actor, UUID campaignId, Pageable pageable) {
        campaignService.loadOwnerOrAdmin(actor, campaignId);
        return disputeRepository.findByCampaignId(campaignId, pageable).map(DisputeResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<DisputeResponse> adminQueue(DisputeStatus status, Pageable pageable) {
        Page<Dispute> page = status == null ? disputeRepository.findAll(pageable) : disputeRepository.findByDisputeStatus(status, pageable);
        return page.map(DisputeResponse::from);
    }

    private void releaseHolds(Dispute dispute, UUID actorId, boolean upheld) {
        UUID milestoneId = dispute.getMilestoneId();
        if (milestoneId == null || !properties.escrow().disputeAutoHold()) {
            return;
        }
        String note = "Dispute " + dispute.getDisputeCode() + " closed as " + dispute.getDisputeStatus();
        if (upheld) {
            disbursementService.rejectDisputed(milestoneId, dispute.getDisbursementRequestId(), actorId, note);
        }
        if (!disputeRepository.existsByMilestoneIdAndDisputeStatusIn(milestoneId, DisputeStatus.OPEN_STATES)) {
            disbursementService.restoreDisputed(milestoneId, actorId, note);
            milestoneService.restoreAfterDispute(milestoneId, actorId, note);
        }
    }

    private void requireParticipant(AuthUser actor, Dispute dispute) {
        AuthUser.require(actor);
        if (actor.isAdmin() || actor.is(dispute.getOpenedBy())) {
            return;
        }
        Campaign campaign = campaignService.load(dispute.getCampaignId());
        if (!actor.is(campaign.getCreatorId())) {
            throw ApplicationException.notFound("Dispute");
        }
    }

    private void move(Dispute dispute, DisputeStatus target, UUID actorId, String action, String reason) {
        DisputeStatus previous = dispute.getDisputeStatus();
        dispute.setDisputeStatus(target);
        auditService.transition(actorId, action, ENTITY, dispute.getId(), previous, target, reason);
    }

    private Dispute load(UUID id) {
        return disputeRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("Dispute"));
    }

    private Dispute lock(UUID id) {
        return disputeRepository.findByIdForUpdate(id).orElseThrow(() -> ApplicationException.notFound("Dispute"));
    }
}
