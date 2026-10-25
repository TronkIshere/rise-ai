package com.riseai.dto.response;

import com.riseai.entity.DisputeMessage;

import java.time.Instant;
import java.util.UUID;

public record DisputeMessageResponse(UUID id, UUID disputeId, UUID authorId, String message, Instant createdAt) {
    public static DisputeMessageResponse from(DisputeMessage m) {
        return new DisputeMessageResponse(m.getId(), m.getDisputeId(), m.getAuthorId(), m.getMessage(), m.getCreatedAt());
    }
}
