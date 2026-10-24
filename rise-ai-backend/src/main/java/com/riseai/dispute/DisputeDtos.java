package com.riseai.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DisputeDtos {
    private DisputeDtos() {
    }

    public record OpenDisputeRequest(
            UUID milestoneId,
            UUID disbursementRequestId,
            @NotNull DisputeType disputeType,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String description,
            @Size(max = 10) List<@NotBlank @Size(max = 500) String> supportingReferences) {
    }

    public record AssignDisputeRequest(@NotNull UUID reviewerId) {
    }

    public record ResolveDisputeRequest(@NotNull ResolutionOutcome outcome, @NotBlank @Size(max = 5000) String resolution) {
    }

    public record DisputeMessageRequest(@NotBlank @Size(max = 5000) String message) {
    }

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

    public record DisputeMessageResponse(UUID id, UUID disputeId, UUID authorId, String message, Instant createdAt) {
        public static DisputeMessageResponse from(DisputeMessage m) {
            return new DisputeMessageResponse(m.getId(), m.getDisputeId(), m.getAuthorId(), m.getMessage(), m.getCreatedAt());
        }
    }
}
