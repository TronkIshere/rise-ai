package com.riseai.service;

import com.riseai.entity.EvidenceRecord;

import java.util.UUID;

/** Mirrors business steps that have no EscrowEngine method onto the on-chain escrow. Does nothing with the simulated engine. */
public interface ChainEscrowRecorder {
    /** True when a release must be confirmed on-chain before it is recorded as RELEASED. */
    boolean releaseRequiresChain();

    void requestRelease(UUID campaignId, UUID disbursementId, UUID actorId);

    void evidenceSubmitted(EvidenceRecord evidence);

    void evidenceReviewed(EvidenceRecord evidence, boolean accepted);

    void disputeOpened(UUID campaignId, UUID milestoneId, UUID disputeId);

    void disputeClosed(UUID campaignId, UUID disputeId);
}
