package com.riseai.dto.response;

import com.riseai.entity.AuditLog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(UUID id, UUID actorUserId, String action, String entityType, UUID entityId,
                               String previousState, String newState, String correlationId, String ipAddress,
                               Map<String, Object> metadata, Instant createdAt) {
    public static AuditLogResponse from(AuditLog a, boolean includeNetwork) {
        return new AuditLogResponse(a.getId(), a.getActorUserId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                a.getPreviousState(), a.getNewState(), a.getCorrelationId(), includeNetwork ? a.getIpAddress() : null,
                a.getMetadata(), a.getCreatedAt());
    }
}
