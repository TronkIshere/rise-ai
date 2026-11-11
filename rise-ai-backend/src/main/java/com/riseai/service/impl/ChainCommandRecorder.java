package com.riseai.service.impl;

import com.riseai.entity.EvidenceRecord;
import com.riseai.enums.ChainCommandStatus;
import com.riseai.repository.ChainCommandRepository;
import com.riseai.service.ChainEscrowRecorder;
import com.riseai.util.ChainValues;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rise.escrow", name = "engine", havingValue = "avalanche")
public class ChainCommandRecorder implements ChainEscrowRecorder {
    private static final List<String> RELEASE_ACTIONS = List.of(ChainActions.QUEUE_RELEASE, ChainActions.EXECUTE_RELEASE);

    private final ChainCommandWriter commands;
    private final ChainCommandRepository repository;

    @Override
    public boolean releaseRequiresChain() {
        return true;
    }

    /** Repeating the admin action while a release is already on its way changes nothing. */
    @Override
    public void requestRelease(UUID campaignId, UUID disbursementId, UUID actorId) {
        if (repository.existsByReferenceIdAndActionInAndStatusIn(disbursementId, RELEASE_ACTIONS, ChainCommandStatus.OPEN)) {
            return;
        }
        commands.enqueue(campaignId, ChainActions.QUEUE_RELEASE, Map.of("requestId", ChainValues.bytes32(disbursementId)), disbursementId, actorId);
    }

    @Override
    public void evidenceSubmitted(EvidenceRecord evidence) {
        commands.enqueue(evidence.getCampaignId(), ChainActions.ANCHOR_EVIDENCE, Map.of("milestoneId", ChainValues.bytes32(evidence.getMilestoneId()),
                "evidenceId", ChainValues.bytes32(evidence.getId()), "contentHash", contentHash(evidence)), evidence.getId(), evidence.getSubmittedBy());
    }

    @Override
    public void evidenceReviewed(EvidenceRecord evidence, boolean accepted) {
        commands.enqueue(evidence.getCampaignId(), ChainActions.DECIDE_EVIDENCE, Map.of("evidenceId", ChainValues.bytes32(evidence.getId()), "accepted", accepted),
                evidence.getId(), evidence.getReviewedBy());
    }

    @Override
    public void disputeOpened(UUID campaignId, UUID milestoneId, UUID disputeId) {
        commands.enqueue(campaignId, ChainActions.OPEN_DISPUTE, Map.of("milestoneId", ChainValues.bytes32(milestoneId), "disputeId", ChainValues.bytes32(disputeId)), disputeId, null);
    }

    @Override
    public void disputeClosed(UUID campaignId, UUID disputeId) {
        commands.enqueue(campaignId, ChainActions.CLOSE_DISPUTE, Map.of("disputeId", ChainValues.bytes32(disputeId)), disputeId, null);
    }

    /** The file hash when the creator supplied one, so reuse is visible on-chain; otherwise a hash of the recorded metadata. */
    private static String contentHash(EvidenceRecord evidence) {
        if (evidence.getFileHash() != null) {
            return "0x" + evidence.getFileHash();
        }
        return ChainValues.sha256Hex(String.join("|", evidence.getId().toString(), evidence.getTitle(), Objects.toString(evidence.getDescription(), ""),
                Objects.toString(evidence.getFileUrl(), ""), Objects.toString(evidence.getExternalReference(), "")));
    }
}
