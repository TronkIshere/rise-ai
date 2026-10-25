package com.riseai.dto.response;

import com.riseai.entity.OutboxEvent;
import com.riseai.enums.OutboxStatus;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventResponse(UUID id, UUID eventId, String eventType, String aggregateType, UUID aggregateId, OutboxStatus status,
                                  int attempts, Instant occurredAt, Instant nextAttemptAt, Instant publishedAt, String lastError) {
    public static OutboxEventResponse from(OutboxEvent e) {
        return new OutboxEventResponse(e.getId(), e.getEventId(), e.getEventType(), e.getAggregateType(), e.getAggregateId(), e.getStatus(),
                e.getAttempts(), e.getOccurredAt(), e.getNextAttemptAt(), e.getPublishedAt(), e.getLastError());
    }
}
