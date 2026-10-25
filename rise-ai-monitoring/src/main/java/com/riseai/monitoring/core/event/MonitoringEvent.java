package com.riseai.monitoring.core.event;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder(toBuilder = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MonitoringEvent {
    String eventId;
    String eventType;
    String source;
    Instant occurredAt;
    Instant receivedAt;
    String correlationId;
    Severity severity;

    @Builder.Default
    Map<String, Object> attributes = Map.of();

    JsonNode payload;
    int payloadBytes;
    String fingerprint;
}
