package com.riseai.monitoring.rise;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record RiseEnvelope(String eventId, String eventType, int schemaVersion, Instant occurredAt, String actorId, String actorRole,
                           String aggregateType, String aggregateId, Map<String, String> references, JsonNode payload) {

    public String text(String field) {
        JsonNode value = payload.path(field);
        return value.isValueNode() && !value.isNull() && !value.asText().isBlank() ? value.asText() : null;
    }

    public BigDecimal decimal(String field) {
        String value = text(field);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public long number(String field, long fallback) {
        JsonNode value = payload.path(field);
        return value.canConvertToLong() ? value.asLong() : fallback;
    }

    public String reference(String name) {
        String value = references.get(name);
        return value != null ? value : text(name);
    }

    public boolean is(String type) {
        return eventType.equals(type);
    }
}
