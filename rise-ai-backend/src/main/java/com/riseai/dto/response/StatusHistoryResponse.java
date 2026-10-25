package com.riseai.dto.response;

import com.riseai.entity.StatusHistory;

import java.time.Instant;
import java.util.UUID;

public record StatusHistoryResponse(UUID id, String entityType, UUID entityId, String fromStatus, String toStatus,
                                    UUID changedBy, String reason, Instant createdAt) {
    public static StatusHistoryResponse from(StatusHistory h) {
        return new StatusHistoryResponse(h.getId(), h.getEntityType(), h.getEntityId(), h.getFromStatus(), h.getToStatus(),
                h.getChangedBy(), h.getReason(), h.getCreatedAt());
    }
}
