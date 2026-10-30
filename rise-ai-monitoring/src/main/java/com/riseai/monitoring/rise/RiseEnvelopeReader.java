package com.riseai.monitoring.rise;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.exception.ApplicationException;
import com.riseai.monitoring.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RiseEnvelopeReader {
    private static final int MAX_EVENT_ID_LENGTH = 64;

    MonitoringProperties properties;

    public boolean isRiseEvent(MonitoringEvent event) {
        MonitoringProperties.Rise rise = properties.getRise();
        return rise.isEnabled() && rise.getSource().equals(event.getSource());
    }

    public Optional<RiseEnvelope> read(MonitoringEvent event) {
        if (!isRiseEvent(event)) {
            return Optional.empty();
        }
        JsonNode envelope = event.getPayload();
        if (envelope == null || !envelope.isObject()) {
            throw invalid("Rise events must carry the event envelope as payload");
        }
        int schemaVersion = envelope.path("schemaVersion").asInt(-1);
        if (!properties.getRise().getSupportedSchemaVersions().contains(schemaVersion)) {
            throw invalid("unsupported Rise envelope schemaVersion " + schemaVersion);
        }
        String eventId = required(envelope, "eventId");
        if (eventId.length() > MAX_EVENT_ID_LENGTH) {
            throw invalid("envelope eventId exceeds " + MAX_EVENT_ID_LENGTH + " characters");
        }
        if (!eventId.equals(event.getEventId())) {
            throw invalid("envelope eventId does not match the request eventId");
        }
        String eventType = required(envelope, "eventType").toUpperCase(Locale.ROOT);
        if (!eventType.toLowerCase(Locale.ROOT).equals(event.getEventType())) {
            throw invalid("envelope eventType does not match the request eventType");
        }
        JsonNode payload = envelope.path("payload");
        if (!payload.isObject()) {
            throw invalid("envelope payload must be an object");
        }
        return Optional.of(new RiseEnvelope(eventId, eventType, schemaVersion, instant(required(envelope, "occurredAt")),
                optional(envelope, "actorId"), optional(envelope, "actorRole"), required(envelope, "aggregateType"),
                required(envelope, "aggregateId"), references(envelope.path("references")), payload));
    }

    private static Map<String, String> references(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw invalid("envelope references must be an object");
        }
        Map<String, String> references = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isTextual()) {
                references.put(field.getKey(), field.getValue().asText());
            }
        }
        return Collections.unmodifiableMap(references);
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw invalid("envelope occurredAt must be an ISO-8601 instant");
        }
    }

    private static String required(JsonNode node, String field) {
        String value = optional(node, field);
        if (value == null) {
            throw invalid("envelope " + field + " is required");
        }
        return value;
    }

    private static String optional(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static ApplicationException invalid(String reason) {
        return new ApplicationException(ErrorCode.INVALID_EVENT, reason);
    }
}
