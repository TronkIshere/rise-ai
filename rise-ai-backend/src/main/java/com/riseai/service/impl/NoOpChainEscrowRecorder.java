package com.riseai.service.impl;

import com.riseai.entity.EvidenceRecord;
import com.riseai.service.ChainEscrowRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "rise.escrow", name = "engine", havingValue = "simulated", matchIfMissing = true)
public class NoOpChainEscrowRecorder implements ChainEscrowRecorder {
    @Override
    public boolean releaseRequiresChain() {
        return false;
    }

    @Override
    public void requestRelease(UUID campaignId, UUID disbursementId, UUID actorId) {
        throw new IllegalStateException("The simulated engine has no chain release path");
    }

    @Override
    public void evidenceSubmitted(EvidenceRecord evidence) {
    }

    @Override
    public void evidenceReviewed(EvidenceRecord evidence, boolean accepted) {
    }

    @Override
    public void disputeOpened(UUID campaignId, UUID milestoneId, UUID disputeId) {
    }

    @Override
    public void disputeClosed(UUID campaignId, UUID disputeId) {
    }
}
