package com.aicore.eventmonitor.normalization;

import com.aicore.eventmonitor.configuration.MonitoringProperties;
import com.aicore.eventmonitor.core.event.MonitoringEvent;
import com.aicore.eventmonitor.core.event.Severity;
import com.aicore.eventmonitor.dto.request.event.MonitoringEventRequest;
import com.aicore.eventmonitor.exception.ApplicationException;
import com.aicore.eventmonitor.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventNormalizer {
    ObjectMapper objectMapper;
    MonitoringProperties properties;
    Clock clock;

    public MonitoringEvent normalize(MonitoringEventRequest request, String fallbackCorrelationId) {
        String eventType = request.getEventType() == null ? null : request.getEventType().trim().toLowerCase(Locale.ROOT);
        String source = trim(request.getSource());
        Map<String, Object> attributes = normalizeAttributes(request.getAttributes());
        JsonNode payload = normalizePayload(request.getPayload());

        return MonitoringEvent.builder()
                .eventId(trim(request.getEventId()))
                .eventType(eventType)
                .source(source)
                .occurredAt(request.getOccurredAt())
                .receivedAt(request.getReceivedAt() != null ? request.getReceivedAt() : clock.instant())
                .correlationId(StringUtils.hasText(request.getCorrelationId()) ? request.getCorrelationId().trim() : fallbackCorrelationId)
                .severity(request.getSeverity())
                .attributes(attributes)
                .payload(payload)
                .payloadBytes(payload == null ? 0 : serialize(payload).length)
                .fingerprint(fingerprint(eventType, source, request.getOccurredAt(), request.getSeverity(), attributes, payload))
                .build();
    }

    private Map<String, Object> normalizeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        int maxValueLength = properties.getLimits().getMaxAttributeValueLength();
        Map<String, Object> normalized = new TreeMap<>();
        attributes.forEach((rawName, value) -> {
            String name = normalizeAttributeName(rawName);
            validateAttributeValue(name, value, maxValueLength);
            if (normalized.putIfAbsent(name, value) != null) {
                throw invalid("attribute '" + name + "' is defined more than once");
            }
        });
        return Collections.unmodifiableMap(normalized);
    }

    private String normalizeAttributeName(String rawName) {
        String name = trim(rawName);
        if (!StringUtils.hasText(name)) {
            throw invalid("attribute names must not be blank");
        }
        return name;
    }

    private void validateAttributeValue(String name, Object value, int maxValueLength) {
        if (value == null) {
            throw invalid("attribute '" + name + "' must not be null");
        }
        if (value instanceof String text) {
            if (text.length() > maxValueLength) {
                throw invalid("attribute '" + name + "' exceeds " + maxValueLength + " characters");
            }
            return;
        }
        if (value instanceof Number number) {
            if (!Double.isFinite(number.doubleValue())) {
                throw invalid("attribute '" + name + "' must be a finite number");
            }
            return;
        }
        if (!(value instanceof Boolean)) {
            throw invalid("attribute '" + name + "' must be a string, number or boolean");
        }
    }

    private JsonNode normalizePayload(JsonNode payload) {
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            return null;
        }
        int maxDepth = properties.getLimits().getMaxPayloadDepth();
        if (exceedsDepth(payload, 1, maxDepth)) {
            throw invalid("payload nesting exceeds " + maxDepth + " levels");
        }
        return payload.deepCopy();
    }

    private boolean exceedsDepth(JsonNode node, int depth, int maxDepth) {
        if (!node.isContainerNode()) {
            return false;
        }
        if (depth > maxDepth) {
            return true;
        }
        for (JsonNode child : node) {
            if (exceedsDepth(child, depth + 1, maxDepth)) {
                return true;
            }
        }
        return false;
    }

    private String fingerprint(String eventType, String source, Instant occurredAt, Severity severity,
                               Map<String, Object> attributes, JsonNode payload) {
        ObjectNode canonical = objectMapper.createObjectNode();
        canonical.put("eventType", eventType);
        canonical.put("source", source);
        canonical.put("occurredAt", occurredAt == null ? null : occurredAt.toString());
        canonical.put("severity", severity == null ? null : severity.name());
        ObjectNode attributeNode = canonical.putObject("attributes");
        attributes.forEach((name, value) -> attributeNode.set(name, objectMapper.valueToTree(value)));
        canonical.set("payload", payload == null ? NullNode.getInstance() : canonicalize(payload));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialize(canonical));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            ObjectNode sorted = objectMapper.createObjectNode();
            for (String name : names) {
                sorted.set(name, canonicalize(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            for (JsonNode element : node) {
                array.add(canonicalize(element));
            }
            return array;
        }
        return node;
    }

    private byte[] serialize(JsonNode node) {
        try {
            return objectMapper.writeValueAsBytes(node);
        } catch (JsonProcessingException ex) {
            throw invalid("payload cannot be serialized");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private ApplicationException invalid(String reason) {
        return new ApplicationException(ErrorCode.INVALID_EVENT, reason);
    }
}
