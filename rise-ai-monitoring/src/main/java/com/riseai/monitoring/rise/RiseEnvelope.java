package com.riseai.monitoring.rise;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public record RiseEnvelope(String eventId, String eventType, int schemaVersion, Instant occurredAt, String actorId, String actorRole,
                           String aggregateType, String aggregateId, Map<String, String> references, JsonNode payload) {
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("^-?[0-9]{1,18}(\\.[0-9]{1,8})?$");
    private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    public String text(String field) {
        JsonNode value = payload.path(field);
        return value.isValueNode() && !value.isNull() && !value.asText().isBlank() ? value.asText() : null;
    }

    public BigDecimal decimal(String field) {
        String value = text(field);
        if (value == null) {
            return null;
        }
        if (!PLAIN_DECIMAL.matcher(value).matches()) {
            return null;
        }
        return new BigDecimal(value);
    }

    public String fileHash() {
        String value = text("fileHash");
        return value != null && SHA_256_HEX.matcher(value).matches() ? value.toLowerCase(Locale.ROOT) : null;
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
