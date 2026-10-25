package com.riseai.dto.response;

import com.riseai.entity.Dispute;
import com.riseai.enums.DisputeStatus;
import com.riseai.enums.DisputeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DisputeResponse(UUID id, String disputeCode, UUID campaignId, UUID milestoneId, UUID disbursementRequestId,
                              UUID openedBy, DisputeType disputeType, String title, String description, List<String> supportingReferences,
                              DisputeStatus disputeStatus, UUID assignedTo, String resolution, UUID resolvedBy, Instant resolvedAt,
                              Instant createdAt, Instant updatedAt) {
    public static DisputeResponse from(Dispute d) {
        return new DisputeResponse(d.getId(), d.getDisputeCode(), d.getCampaignId(), d.getMilestoneId(), d.getDisbursementRequestId(),
                d.getOpenedBy(), d.getDisputeType(), d.getTitle(), d.getDescription(), d.getSupportingReferences(), d.getDisputeStatus(),
                d.getAssignedTo(), d.getResolution(), d.getResolvedBy(), d.getResolvedAt(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
