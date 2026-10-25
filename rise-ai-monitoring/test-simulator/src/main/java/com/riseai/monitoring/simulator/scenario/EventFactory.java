package com.riseai.monitoring.simulator.scenario;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class EventFactory {

    public static final List<String> SOURCES = List.of("edge-gateway", "batch-scheduler", "storage-agent", "api-service");

    private static final List<String> REGIONS = List.of("eu-west", "us-east", "ap-south");
    private static final List<String> PATHS = List.of("/orders", "/health", "/login", "/reports");
    private static final int ITEM_BYTES_WITH_SEPARATOR = 33;

    private final ObjectMapper objectMapper;
    private final Instant receivedAt;

    public EventFactory(ObjectMapper objectMapper, Instant receivedAt) {
        this.objectMapper = objectMapper;
        this.receivedAt = receivedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Map<String, Object> event(String eventId, String eventType, Random random) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("latencyMs", 1 + random.nextInt(999));
        attributes.put("freeDiskPercent", 11 + random.nextInt(90));
        attributes.put("region", pick(random, REGIONS));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", pick(random, PATHS));
        payload.put("status", 200);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("eventType", eventType);
        event.put("source", pick(random, SOURCES));
        event.put("occurredAt", receivedAt.minusSeconds(30 + random.nextInt(3600)).toString());
        event.put("receivedAt", receivedAt.toString());
        event.put("attributes", attributes);
        event.put("payload", payload);
        return event;
    }

    public Map<String, Object> normalEvent(String eventId, Random random) {
        return event(eventId, pick(random, CoreContract.ALLOWED_EVENT_TYPES), random);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> attributes(Map<String, Object> event) {
        return (Map<String, Object>) event.get("attributes");
    }

    public static Map<String, Object> copy(Map<String, Object> event) {
        Map<String, Object> copy = new LinkedHashMap<>(event);
        if (event.get("attributes") != null) {
            copy.put("attributes", new LinkedHashMap<>(attributes(event)));
        }
        return copy;
    }

    public static void fillAttributes(Map<String, Object> event, int totalAttributes) {
        Map<String, Object> attributes = attributes(event);
        int filler = 1;
        while (attributes.size() < totalAttributes) {
            attributes.put(String.format("f%02d", filler), filler);
            filler++;
        }
    }

    public Map<String, Object> payloadOfBytes(int bytes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("blob", "x".repeat(bytes - 11));
        return payload;
    }

    public Map<String, Object> nestedPayload(int depth) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("value", 1);
        for (int level = 1; level < depth; level++) {
            Map<String, Object> parent = new LinkedHashMap<>();
            parent.put("n", node);
            node = parent;
        }
        return node;
    }

    public Map<String, Object> itemsPayload(int approximateBytes) {
        int count = Math.max(1, (approximateBytes - 12) / ITEM_BYTES_WITH_SEPARATOR);
        List<Map<String, Object>> items = new ArrayList<>(count);
        for (int item = 1; item <= count; item++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", String.format("%06d", item));
            entry.put("v", "abcdefghij");
            items.add(entry);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        return payload;
    }

    public int payloadBytes(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload).length;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Payload could not be serialized", ex);
        }
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Event could not be serialized", ex);
        }
    }

    public static <T> T pick(Random random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }
}
