package com.riseai.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AuditDtos {
    private AuditDtos() {
    }

    public record AuditLogResponse(UUID id, UUID actorUserId, String action, String entityType, UUID entityId,
                                   String previousState, String newState, String correlationId, String ipAddress,
                                   Map<String, Object> metadata, Instant createdAt) {
        public static AuditLogResponse from(AuditLog a, boolean includeNetwork) {
            return new AuditLogResponse(a.getId(), a.getActorUserId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                    a.getPreviousState(), a.getNewState(), a.getCorrelationId(), includeNetwork ? a.getIpAddress() : null,
                    a.getMetadata(), a.getCreatedAt());
        }
    }

    public record StatusHistoryResponse(UUID id, String entityType, UUID entityId, String fromStatus, String toStatus,
                                        UUID changedBy, String reason, Instant createdAt) {
        public static StatusHistoryResponse from(StatusHistory h) {
            return new StatusHistoryResponse(h.getId(), h.getEntityType(), h.getEntityId(), h.getFromStatus(), h.getToStatus(),
                    h.getChangedBy(), h.getReason(), h.getCreatedAt());
        }
    }
}
