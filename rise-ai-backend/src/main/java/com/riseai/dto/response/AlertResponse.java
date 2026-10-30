package com.riseai.dto.response;

import com.riseai.entity.AlertNotification;
import com.riseai.enums.AlertStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AlertResponse(UUID id, String kind, Map<String, String> details, AlertStatus status, int attempts, Instant createdAt,
                            Instant nextAttemptAt, Instant deliveredAt, Instant failedAt, String lastError) {
    public static AlertResponse from(AlertNotification a) {
        return new AlertResponse(a.getId(), a.getKind(), a.getDetails(), a.getStatus(), a.getAttempts(), a.getCreatedAt(),
                a.getNextAttemptAt(), a.getDeliveredAt(), a.getFailedAt(), a.getLastError());
    }
}
