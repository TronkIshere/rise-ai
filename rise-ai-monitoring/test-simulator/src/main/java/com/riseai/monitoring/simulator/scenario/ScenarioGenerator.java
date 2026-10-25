package com.riseai.monitoring.simulator.scenario;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ScenarioGenerator {

    public static final Set<String> CLEAR_FAILURE_ASSESSMENTS = Set.of("NEEDS_REVIEW", "LIKELY_ANOMALOUS");
    public static final String EXPLORATORY_REASON = "Exploratory: elevated latency during a declared maintenance window has "
            + "several reasonable interpretations, so no defensible AI rubric exists";

    private static final String[] NORMAL_SEVERITIES = {null, "INFO", "LOW", "MEDIUM"};

    private final long seed;
    private final String runTag;
    private final EventFactory events;
    private final Map<Category, Integer> plannedCounts;
    private final byte[] plan;

    public ScenarioGenerator(long seed, int scenarioCount, Map<Category, Integer> weights, boolean includeAiScenarios,
                             String runTag, EventFactory events) {
        this.seed = seed;
        this.runTag = runTag;
        this.events = events;
        this.plannedCounts = plannedCounts(scenarioCount, weights, includeAiScenarios);
        this.plan = buildPlan(scenarioCount, plannedCounts, seed);
    }

    public static Map<Category, Integer> plannedCounts(int total, Map<Category, Integer> weights, boolean includeAiScenarios) {
        Map<Category, Integer> effective = new EnumMap<>(Category.class);
        long weightSum = 0;
        for (Category category : Category.values()) {
            Integer configured = weights.get(category);
            int weight = configured == null || (!includeAiScenarios && category.isAiDependent()) ? 0 : Math.max(0, configured);
            effective.put(category, weight);
            weightSum += weight;
        }
        if (weightSum == 0) {
            throw new IllegalArgumentException("At least one scenario category must have a positive weight");
        }

        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        Map<Category, Long> remainders = new EnumMap<>(Category.class);
        int assigned = 0;
        for (Category category : Category.values()) {
            long scaled = (long) total * effective.get(category);
            counts.put(category, (int) (scaled / weightSum));
            remainders.put(category, scaled % weightSum);
            assigned += counts.get(category);
        }
        List<Category> byRemainder = new ArrayList<>(List.of(Category.values()));
        byRemainder.sort(Comparator.<Category, Long>comparing(remainders::get).reversed().thenComparing(Comparator.naturalOrder()));
        for (int position = 0; assigned < total; position++) {
            counts.merge(byRemainder.get(position % byRemainder.size()), 1, Integer::sum);
            assigned++;
        }
        return counts;
    }

    private static byte[] buildPlan(int total, Map<Category, Integer> counts, long seed) {
        byte[] plan = new byte[total];
        int position = 0;
        for (Map.Entry<Category, Integer> entry : counts.entrySet()) {
            for (int count = 0; count < entry.getValue(); count++) {
                plan[position++] = (byte) entry.getKey().ordinal();
            }
        }
        Random random = new Random(seed);
        for (int index = total - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            byte value = plan[index];
            plan[index] = plan[swap];
            plan[swap] = value;
        }
        return plan;
    }

    public int getScenarioCount() {
        return plan.length;
    }

    public Map<Category, Integer> getPlannedCounts() {
        return plannedCounts;
    }

    public Category categoryOf(int index) {
        return Category.values()[plan[index]];
    }

    public Scenario generate(int index) {
        Draft draft = new Draft(index, String.format("S%06d", index + 1), "sim-" + runTag + "-" + index,
                new Random(seed * 1_000_003L + index));
        return switch (categoryOf(index)) {
            case NORMAL -> normal(draft);
            case RULE_VIOLATION -> ruleViolation(draft);
            case BOUNDARY -> boundary(draft);
            case AI_CONTEXT -> aiContext(draft);
            case INVALID -> invalid(draft);
            case DUPLICATE -> duplicate(draft);
            case OPTIONAL_FIELDS -> optionalFields(draft);
            case EVENT_SEQUENCE -> sequence(draft);
            case LARGE_PAYLOAD -> largePayload(draft);
            case AI_RESILIENCE -> resilience(draft);
        };
    }

    private Scenario normal(Draft d) {
        String eventType = EventFactory.pick(d.random, CoreContract.ALLOWED_EVENT_TYPES);
        Map<String, Object> event = events.event(d.eventId(0), eventType, d.random);
        Map<String, Object> attributes = EventFactory.attributes(event);
        if (eventType.equals("service.heartbeat")) {
            attributes.put("uptimeSeconds", 60 + d.random.nextInt(86400));
            event.remove("payload");
        }
        if (eventType.equals("job.completed")) {
            attributes.put("durationSeconds", 1 + d.random.nextInt(3600));
            attributes.put("exitCode", 0);
        }
        String severity = NORMAL_SEVERITIES[d.random.nextInt(NORMAL_SEVERITIES.length)];
        if (severity != null) {
            event.put("severity", severity);
        }
        return single(d, Category.NORMAL, "normal-" + eventType,
                "Normal " + eventType + " event, latencyMs=" + attributes.get("latencyMs") + ", freeDiskPercent="
                        + attributes.get("freeDiskPercent") + ", severity=" + severity,
                event, Outcomes.normal());
    }

    private Scenario ruleViolation(Draft d) {
        Map<String, Object> event = events.normalEvent(d.eventId(0), d.random);
        Map<String, Object> attributes = EventFactory.attributes(event);
        Instant receivedAt = events.getReceivedAt();
        return switch (d.random.nextInt(8)) {
            case 0 -> {
                int latency = 1001 + d.random.nextInt(59000);
                attributes.put("latencyMs", latency);
                yield single(d, Category.RULE_VIOLATION, "latency-above-max", "latencyMs=" + latency + " exceeds max 1000",
                        event, Outcomes.flagged(CoreContract.RULE_THRESHOLD, "HIGH", CoreContract.SRC_LATENCY));
            }
            case 1 -> {
                int freeDisk = d.random.nextInt(10);
                attributes.put("freeDiskPercent", freeDisk);
                yield single(d, Category.RULE_VIOLATION, "free-disk-below-min", "freeDiskPercent=" + freeDisk + " is below min 10",
                        event, Outcomes.flagged(CoreContract.RULE_THRESHOLD, "MEDIUM", CoreContract.SRC_FREE_DISK));
            }
            case 2 -> {
                event.put("severity", "HIGH");
                yield single(d, Category.RULE_VIOLATION, "reported-high", "Source reports severity HIGH",
                        event, Outcomes.flagged(CoreContract.RULE_REPORTED_SEVERITY, "HIGH", CoreContract.SRC_SEVERITY));
            }
            case 3 -> {
                event.put("severity", "CRITICAL");
                yield single(d, Category.RULE_VIOLATION, "reported-critical", "Source reports severity CRITICAL",
                        event, Outcomes.flagged(CoreContract.RULE_REPORTED_SEVERITY, "CRITICAL", CoreContract.SRC_SEVERITY));
            }
            case 4 -> {
                attributes.put("latencyMs", "slow");
                yield single(d, Category.RULE_VIOLATION, "non-numeric-latency", "latencyMs is the non-numeric value 'slow'",
                        event, Outcomes.flagged(CoreContract.RULE_THRESHOLD, "MEDIUM", CoreContract.SRC_NON_NUMERIC));
            }
            case 5 -> {
                event.put("eventType", CoreContract.UNLISTED_EVENT_TYPE);
                yield single(d, Category.RULE_VIOLATION, "unsupported-event-type", "eventType is not in the allowed list",
                        event, Outcomes.rejected(CoreContract.RULE_EVENT_TYPE, CoreContract.SRC_EVENT_TYPE));
            }
            case 6 -> {
                event.put("occurredAt", receivedAt.plus(Duration.ofHours(1)).toString());
                yield single(d, Category.RULE_VIOLATION, "occurred-in-future", "occurredAt is 1 hour after receivedAt",
                        event, Outcomes.rejected(CoreContract.RULE_TIMESTAMP, CoreContract.SRC_TIMESTAMP_SKEW));
            }
            default -> {
                event.put("occurredAt", receivedAt.minus(Duration.ofDays(8)).toString());
                yield single(d, Category.RULE_VIOLATION, "occurred-too-long-ago", "occurredAt is 8 days before receivedAt",
                        event, Outcomes.rejected(CoreContract.RULE_TIMESTAMP, CoreContract.SRC_TIMESTAMP_AGE));
            }
        };
    }

    private Scenario boundary(Draft d) {
        Map<String, Object> event = events.normalEvent(d.eventId(0), d.random);
        Map<String, Object> attributes = EventFactory.attributes(event);
        Instant receivedAt = events.getReceivedAt();
        return switch (d.random.nextInt(28)) {
            case 0 -> latencyBoundary(d, event, 999, false);
            case 1 -> latencyBoundary(d, event, 1000, false);
            case 2 -> latencyBoundary(d, event, 1001, true);
            case 3 -> latencyBoundary(d, event, new BigDecimal("1000.0"), false);
            case 4 -> latencyBoundary(d, event, new BigDecimal("1000.001"), true);
            case 5 -> latencyBoundary(d, event, 0, false);
            case 6 -> freeDiskBoundary(d, event, 10, false);
            case 7 -> freeDiskBoundary(d, event, new BigDecimal("9.999"), true);
            case 8 -> freeDiskBoundary(d, event, 0, true);
            case 9 -> freeDiskBoundary(d, event, -1, true);
            case 10 -> {
                event.put("severity", "MEDIUM");
                yield boundary(d, "severity-MEDIUM", "Reported severity MEDIUM is below the flag level HIGH", event, Outcomes.normal());
            }
            case 11 -> {
                event.put("severity", "HIGH");
                yield boundary(d, "severity-HIGH", "Reported severity HIGH equals the flag level (rule uses >=)", event,
                        Outcomes.flagged(CoreContract.RULE_REPORTED_SEVERITY, "HIGH", CoreContract.SRC_SEVERITY));
            }
            case 12 -> {
                event.put("occurredAt", receivedAt.plus(CoreContract.MAX_CLOCK_SKEW).toString());
                yield boundary(d, "occurred-at-skew-limit", "occurredAt is exactly receivedAt + 5m (allowed)", event, Outcomes.normal());
            }
            case 13 -> {
                event.put("occurredAt", receivedAt.plus(CoreContract.MAX_CLOCK_SKEW).plusSeconds(1).toString());
                yield boundary(d, "occurred-at-skew-exceeded", "occurredAt is receivedAt + 5m + 1s", event,
                        Outcomes.rejected(CoreContract.RULE_TIMESTAMP, CoreContract.SRC_TIMESTAMP_SKEW));
            }
            case 14 -> {
                event.put("occurredAt", receivedAt.minus(CoreContract.MAX_EVENT_AGE).toString());
                yield boundary(d, "occurred-at-age-limit", "occurredAt is exactly receivedAt - 7d (allowed)", event, Outcomes.normal());
            }
            case 15 -> {
                event.put("occurredAt", receivedAt.minus(CoreContract.MAX_EVENT_AGE).minusSeconds(1).toString());
                yield boundary(d, "occurred-at-age-exceeded", "occurredAt is receivedAt - 7d - 1s", event,
                        Outcomes.rejected(CoreContract.RULE_TIMESTAMP, CoreContract.SRC_TIMESTAMP_AGE));
            }
            case 16 -> {
                event.put("payload", events.payloadOfBytes(CoreContract.MAX_PAYLOAD_BYTES));
                yield boundary(d, "payload-65536-bytes", "Payload is exactly 65536 bytes (allowed)", event, Outcomes.normal());
            }
            case 17 -> {
                event.put("payload", events.payloadOfBytes(CoreContract.MAX_PAYLOAD_BYTES + 1));
                yield boundary(d, "payload-65537-bytes", "Payload is 65537 bytes", event,
                        Outcomes.rejected(CoreContract.RULE_PAYLOAD_LIMITS, CoreContract.SRC_PAYLOAD_SIZE));
            }
            case 18 -> {
                EventFactory.fillAttributes(event, CoreContract.MAX_ATTRIBUTES);
                yield boundary(d, "attributes-50", "Event has exactly 50 attributes (allowed)", event, Outcomes.normal());
            }
            case 19 -> {
                EventFactory.fillAttributes(event, CoreContract.MAX_ATTRIBUTES + 1);
                yield boundary(d, "attributes-51", "Event has 51 attributes", event,
                        Outcomes.rejected(CoreContract.RULE_PAYLOAD_LIMITS, CoreContract.SRC_ATTRIBUTE_COUNT));
            }
            case 20 -> {
                attributes.put("note", "n".repeat(CoreContract.MAX_ATTRIBUTE_VALUE_LENGTH));
                yield boundary(d, "attribute-value-1000", "Attribute value is exactly 1000 characters (allowed)", event, Outcomes.normal());
            }
            case 21 -> {
                attributes.put("note", "n".repeat(CoreContract.MAX_ATTRIBUTE_VALUE_LENGTH + 1));
                yield boundary(d, "attribute-value-1001", "Attribute value is 1001 characters", event,
                        Outcomes.error(400, CoreContract.ERROR_INVALID_EVENT, CoreContract.SRC_INVALID_EVENT));
            }
            case 22 -> {
                event.put("payload", events.nestedPayload(CoreContract.MAX_PAYLOAD_DEPTH));
                yield boundary(d, "payload-depth-20", "Payload nesting depth is exactly 20 (allowed)", event, Outcomes.normal());
            }
            case 23 -> {
                event.put("payload", events.nestedPayload(CoreContract.MAX_PAYLOAD_DEPTH + 1));
                yield boundary(d, "payload-depth-21", "Payload nesting depth is 21", event,
                        Outcomes.error(400, CoreContract.ERROR_INVALID_EVENT, CoreContract.SRC_INVALID_EVENT));
            }
            case 24 -> {
                event.put("eventId", padded(d.eventId(0), CoreContract.MAX_EVENT_ID_LENGTH));
                yield boundary(d, "event-id-128", "eventId is exactly 128 characters (allowed)", event, Outcomes.normal());
            }
            case 25 -> {
                event.put("eventId", padded(d.eventId(0), CoreContract.MAX_EVENT_ID_LENGTH + 1));
                yield boundary(d, "event-id-129", "eventId is 129 characters", event,
                        Outcomes.error(400, CoreContract.ERROR_INVALID_DATA, CoreContract.SRC_VALIDATION));
            }
            case 26 -> {
                String correlationId = "c".repeat(CoreContract.MAX_CORRELATION_ID_LENGTH);
                event.put("correlationId", correlationId);
                yield boundary(d, "correlation-id-64", "correlationId is exactly 64 characters (allowed)", event,
                        Outcomes.normal().toBuilder().correlationId(correlationId).build());
            }
            default -> {
                event.put("correlationId", "c".repeat(CoreContract.MAX_CORRELATION_ID_LENGTH + 1));
                yield boundary(d, "correlation-id-65", "correlationId is 65 characters", event,
                        Outcomes.error(400, CoreContract.ERROR_INVALID_DATA, CoreContract.SRC_VALIDATION));
            }
        };
    }

    private Scenario latencyBoundary(Draft d, Map<String, Object> event, Object value, boolean flagged) {
        EventFactory.attributes(event).put("latencyMs", value);
        return boundary(d, "latency-" + value,
                "latencyMs=" + value + (flagged ? " is above" : " is not above") + " max 1000 (rule uses value > max)", event,
                flagged ? Outcomes.flagged(CoreContract.RULE_THRESHOLD, "HIGH", CoreContract.SRC_LATENCY) : Outcomes.normal());
    }

    private Scenario freeDiskBoundary(Draft d, Map<String, Object> event, Object value, boolean flagged) {
        EventFactory.attributes(event).put("freeDiskPercent", value);
        return boundary(d, "free-disk-" + value,
                "freeDiskPercent=" + value + (flagged ? " is below" : " is not below") + " min 10 (rule uses value < min)", event,
                flagged ? Outcomes.flagged(CoreContract.RULE_THRESHOLD, "MEDIUM", CoreContract.SRC_FREE_DISK) : Outcomes.normal());
    }

    private Scenario boundary(Draft d, String template, String description, Map<String, Object> event, ExpectedOutcome expected) {
        return single(d, Category.BOUNDARY, template, description, event, expected);
    }

    private Scenario aiContext(Draft d) {
        Map<String, Object> event = events.event(d.eventId(0), "request.completed", d.random);
        Map<String, Object> attributes = EventFactory.attributes(event);
        if (d.random.nextInt(10) < 7) {
            int latency = 15000 + d.random.nextInt(45001);
            int errorRate = 50 + d.random.nextInt(46);
            int failures = 5 + d.random.nextInt(16);
            attributes.put("latencyMs", latency);
            attributes.put("errorRatePercent", errorRate);
            attributes.put("consecutiveFailures", failures);
            attributes.put("status", "FAILED");
            ExpectedOutcome expected = Outcomes.flagged(CoreContract.RULE_THRESHOLD, "HIGH", CoreContract.SRC_LATENCY)
                    .toBuilder().source(CoreContract.SRC_LATENCY + "; " + CoreContract.SRC_AI_RUBRIC).build();
            return build(d, Category.AI_CONTEXT, "ai-clear-failure",
                    "latencyMs=" + latency + ", errorRatePercent=" + errorRate + ", consecutiveFailures=" + failures
                            + ", status=FAILED: an unambiguous multi-signal failure",
                    List.of(step(event, expected)), true, null, CLEAR_FAILURE_ASSESSMENTS);
        }
        int latency = 1100 + d.random.nextInt(401);
        attributes.put("latencyMs", latency);
        attributes.put("maintenanceWindow", true);
        attributes.put("note", "scheduled load test in progress");
        return build(d, Category.AI_CONTEXT, "ai-maintenance-window",
                "latencyMs=" + latency + " during a declared maintenance window and load test",
                List.of(step(event, Outcomes.flagged(CoreContract.RULE_THRESHOLD, "HIGH", CoreContract.SRC_LATENCY))),
                false, EXPLORATORY_REASON, null);
    }

    private Scenario invalid(Draft d) {
        Map<String, Object> event = events.normalEvent(d.eventId(0), d.random);
        Map<String, Object> attributes = EventFactory.attributes(event);
        ExpectedOutcome validation = Outcomes.error(400, CoreContract.ERROR_INVALID_DATA, CoreContract.SRC_VALIDATION);
        ExpectedOutcome malformed = Outcomes.error(400, CoreContract.ERROR_MALFORMED_REQUEST, CoreContract.SRC_MALFORMED);
        ExpectedOutcome invalidEvent = Outcomes.error(400, CoreContract.ERROR_INVALID_EVENT, CoreContract.SRC_INVALID_EVENT);
        return switch (d.random.nextInt(15)) {
            case 0 -> missing(d, event, "eventId", validation);
            case 1 -> missing(d, event, "eventType", validation);
            case 2 -> missing(d, event, "source", validation);
            case 3 -> missing(d, event, "occurredAt", validation);
            case 4 -> {
                event.put("eventId", "   ");
                yield single(d, Category.INVALID, "blank-eventId", "eventId contains only spaces", event, validation);
            }
            case 5 -> {
                event.put("eventId", d.idPrefix + " bad");
                yield single(d, Category.INVALID, "eventId-invalid-characters", "eventId contains a space", event, validation);
            }
            case 6 -> {
                event.put("occurredAt", "2026-13-45T99:00:00Z");
                yield single(d, Category.INVALID, "invalid-timestamp", "occurredAt is not a valid ISO-8601 instant", event, malformed);
            }
            case 7 -> {
                event.put("severity", "SEVERE");
                yield single(d, Category.INVALID, "unsupported-severity", "severity SEVERE is not a supported enum value", event, malformed);
            }
            case 8 -> {
                String json = events.toJson(event);
                yield build(d, Category.INVALID, "malformed-json", "Request body is truncated JSON",
                        List.of(rawStep(d.eventId(0), json.substring(0, json.length() / 2), ScenarioStep.JSON, malformed)), true, null, null);
            }
            case 9 -> {
                event.put("attributes", List.of(1, 2, 3));
                yield single(d, Category.INVALID, "attributes-as-array", "attributes is a JSON array instead of an object", event, malformed);
            }
            case 10 -> {
                attributes.put("nested", Map.of("depth", 1));
                yield single(d, Category.INVALID, "nested-attribute-value", "An attribute value is a JSON object", event, invalidEvent);
            }
            case 11 -> {
                attributes.put("note", null);
                yield single(d, Category.INVALID, "null-attribute-value", "An attribute value is null", event, invalidEvent);
            }
            case 12 -> {
                event.put("correlationId", "c".repeat(CoreContract.MAX_CORRELATION_ID_LENGTH + 1));
                yield single(d, Category.INVALID, "correlation-id-too-long", "correlationId has 65 characters", event, validation);
            }
            case 13 -> build(d, Category.INVALID, "unsupported-content-type", "Valid JSON sent as text/plain",
                    List.of(rawStep(d.eventId(0), events.toJson(event), "text/plain",
                            Outcomes.error(415, CoreContract.ERROR_UNSUPPORTED_MEDIA_TYPE, CoreContract.SRC_MEDIA_TYPE))),
                    true, null, null);
            default -> {
                attributes.put("bad key", 1);
                yield single(d, Category.INVALID, "invalid-attribute-name", "An attribute name contains a space", event, validation);
            }
        };
    }

    private Scenario missing(Draft d, Map<String, Object> event, String field, ExpectedOutcome expected) {
        event.remove(field);
        return single(d, Category.INVALID, "missing-" + field, "Required field " + field + " is absent", event, expected);
    }

    private Scenario duplicate(Draft d) {
        Map<String, Object> original = events.normalEvent(d.eventId(0), d.random);
        ExpectedOutcome replay = Outcomes.normal().toBuilder()
                .duplicate(true).sameCreatedAtAsPreviousStep(true).anomalous(null).source(CoreContract.SRC_DUPLICATE_REPLAY).build();
        Map<String, Object> second = EventFactory.copy(original);
        return switch (d.random.nextInt(5)) {
            case 0 -> build(d, Category.DUPLICATE, "exact-resend", "The same event is sent twice",
                    List.of(step(original, Outcomes.normal()), step(second, replay)), true, null, null);
            case 1 -> {
                second.put("receivedAt", events.getReceivedAt().plusSeconds(30).toString());
                second.put("correlationId", "retry-" + d.idPrefix);
                yield build(d, Category.DUPLICATE, "resend-new-delivery-metadata",
                        "The same event is resent with a new receivedAt and correlationId",
                        List.of(step(original, Outcomes.normal()), step(second, replay)), true, null, null);
            }
            case 2 -> {
                Map<String, Object> attributes = EventFactory.attributes(second);
                attributes.put("latencyMs", Integer.valueOf(500).equals(attributes.get("latencyMs")) ? 501 : 500);
                yield build(d, Category.DUPLICATE, "conflicting-content", "The same eventId is reused with a different latencyMs",
                        List.of(step(original, Outcomes.normal()),
                                step(second, Outcomes.error(409, CoreContract.ERROR_EVENT_ID_CONFLICT, CoreContract.SRC_DUPLICATE_CONFLICT))),
                        true, null, null);
            }
            case 3 -> {
                String otherSource = EventFactory.SOURCES.stream().filter(source -> !source.equals(original.get("source")))
                        .findFirst().orElseThrow();
                second.put("source", otherSource);
                yield build(d, Category.DUPLICATE, "same-id-other-source", "The same eventId is sent by a different source",
                        List.of(step(original, Outcomes.normal()),
                                step(second, Outcomes.normal().toBuilder().source(CoreContract.SRC_DUPLICATE_SCOPE).build())),
                        true, null, null);
            }
            default -> {
                String future = events.getReceivedAt().plus(Duration.ofHours(1)).toString();
                original.put("occurredAt", future);
                second.put("occurredAt", future);
                ExpectedOutcome rejected = Outcomes.rejected(CoreContract.RULE_TIMESTAMP, CoreContract.SRC_TIMESTAMP_SKEW);
                yield build(d, Category.DUPLICATE, "rejected-resend", "A rejected event is sent twice",
                        List.of(step(original, rejected), step(second, rejected.toBuilder().duplicate(true)
                                .sameCreatedAtAsPreviousStep(true).source(CoreContract.SRC_DUPLICATE_REPLAY).build())),
                        true, null, null);
            }
        };
    }

    private Scenario optionalFields(Draft d) {
        Map<String, Object> event = events.normalEvent(d.eventId(0), d.random);
        ExpectedOutcome normal = Outcomes.normal();
        return switch (d.random.nextInt(8)) {
            case 0 -> {
                Map<String, Object> minimal = new LinkedHashMap<>();
                for (String field : List.of("eventId", "eventType", "source", "occurredAt")) {
                    minimal.put(field, event.get(field));
                }
                yield single(d, Category.OPTIONAL_FIELDS, "required-fields-only", "Only the four required fields are sent", minimal, normal);
            }
            case 1 -> {
                String correlationId = "corr-" + d.idPrefix;
                event.put("severity", "LOW");
                event.put("correlationId", correlationId);
                yield single(d, Category.OPTIONAL_FIELDS, "all-fields", "Every documented field is present", event,
                        normal.toBuilder().correlationId(correlationId).build());
            }
            case 2 -> {
                event.put("attributes", Map.of());
                yield single(d, Category.OPTIONAL_FIELDS, "empty-attributes", "attributes is an empty object", event, normal);
            }
            case 3 -> {
                event.put("payload", null);
                yield single(d, Category.OPTIONAL_FIELDS, "null-payload", "payload is JSON null", event, normal);
            }
            case 4 -> {
                event.put("payload", List.of(1, 2, 3));
                yield single(d, Category.OPTIONAL_FIELDS, "array-payload", "payload is a JSON array", event, normal);
            }
            case 5 -> {
                event.put("payload", "plain text payload");
                yield single(d, Category.OPTIONAL_FIELDS, "primitive-payload", "payload is a JSON string", event, normal);
            }
            case 6 -> {
                event.put("severity", "low");
                yield single(d, Category.OPTIONAL_FIELDS, "lowercase-severity", "severity is the lower-case value 'low'", event, normal);
            }
            default -> {
                String correlationId = "corr-" + d.idPrefix;
                event.put("correlationId", correlationId);
                yield single(d, Category.OPTIONAL_FIELDS, "body-correlation-id", "correlationId is supplied in the body", event,
                        normal.toBuilder().correlationId(correlationId).build());
            }
        };
    }

    private Scenario sequence(Draft d) {
        String correlationId = "seq-" + runTag + "-" + d.index;
        String source = EventFactory.pick(d.random, EventFactory.SOURCES);
        Instant start = events.getReceivedAt().minusSeconds(600);
        List<ScenarioStep> steps = new ArrayList<>();
        ExpectedOutcome normal = Outcomes.normal().toBuilder().correlationId(correlationId)
                .source(CoreContract.SRC_NORMAL + "; " + CoreContract.SRC_SEQUENCE).build();
        ExpectedOutcome latencyFlag = Outcomes.flagged(CoreContract.RULE_THRESHOLD, "HIGH", CoreContract.SRC_LATENCY + "; " + CoreContract.SRC_SEQUENCE)
                .toBuilder().correlationId(correlationId).build();
        return switch (d.random.nextInt(3)) {
            case 0 -> {
                int failures = 2 + d.random.nextInt(3);
                for (int step = 0; step < failures; step++) {
                    Map<String, Object> event = sequenceEvent(d, step, source, start, correlationId);
                    EventFactory.attributes(event).put("latencyMs", 1500 + d.random.nextInt(18501));
                    steps.add(step(event, latencyFlag));
                }
                steps.add(step(sequenceEvent(d, failures, source, start, correlationId), normal));
                yield build(d, Category.EVENT_SEQUENCE, "failures-then-recovery",
                        failures + " slow requests followed by a normal recovery event, correlationId=" + correlationId,
                        steps, true, null, null);
            }
            case 1 -> {
                steps.add(step(sequenceEvent(d, 0, source, start, correlationId), normal));
                steps.add(step(sequenceEvent(d, 1, source, start, correlationId), normal));
                Map<String, Object> escalation = sequenceEvent(d, 2, source, start, correlationId);
                escalation.put("severity", "HIGH");
                steps.add(step(escalation, Outcomes.flagged(CoreContract.RULE_REPORTED_SEVERITY, "HIGH",
                        CoreContract.SRC_SEVERITY + "; " + CoreContract.SRC_SEQUENCE).toBuilder().correlationId(correlationId).build()));
                yield build(d, Category.EVENT_SEQUENCE, "normal-then-escalation",
                        "Two normal events followed by a HIGH severity event, correlationId=" + correlationId, steps, true, null, null);
            }
            default -> {
                int retries = 2 + d.random.nextInt(2);
                for (int step = 0; step < retries; step++) {
                    Map<String, Object> event = sequenceEvent(d, step, source, start, correlationId);
                    EventFactory.attributes(event).put("retryCount", step + 1);
                    steps.add(step(event, normal));
                }
                Map<String, Object> timeout = sequenceEvent(d, retries, source, start, correlationId);
                EventFactory.attributes(timeout).put("retryCount", retries + 1);
                EventFactory.attributes(timeout).put("latencyMs", 30000);
                steps.add(step(timeout, latencyFlag));
                yield build(d, Category.EVENT_SEQUENCE, "retries-then-timeout",
                        retries + " retries with normal latency followed by a 30000 ms request, correlationId=" + correlationId,
                        steps, true, null, null);
            }
        };
    }

    private Map<String, Object> sequenceEvent(Draft d, int step, String source, Instant start, String correlationId) {
        Map<String, Object> event = events.event(d.eventId(step), "request.completed", d.random);
        event.put("source", source);
        event.put("occurredAt", start.plusSeconds(60L * step).toString());
        event.put("correlationId", correlationId);
        return event;
    }

    private Scenario largePayload(Draft d) {
        Map<String, Object> event = events.normalEvent(d.eventId(0), d.random);
        Map<String, Object> attributes = EventFactory.attributes(event);
        return switch (d.random.nextInt(5)) {
            case 0 -> {
                Map<String, Object> payload = events.itemsPayload(1000 + d.random.nextInt(57001));
                event.put("payload", payload);
                yield single(d, Category.LARGE_PAYLOAD, "large-flat-payload",
                        "Payload array of " + events.payloadBytes(payload) + " bytes", event, Outcomes.normal());
            }
            case 1 -> {
                int count = 40 + d.random.nextInt(11);
                EventFactory.fillAttributes(event, count);
                yield single(d, Category.LARGE_PAYLOAD, "many-attributes", count + " attributes", event, Outcomes.normal());
            }
            case 2 -> {
                int depth = 5 + d.random.nextInt(11);
                event.put("payload", events.nestedPayload(depth));
                yield single(d, Category.LARGE_PAYLOAD, "deep-payload", "Payload nested " + depth + " levels deep", event, Outcomes.normal());
            }
            case 3 -> {
                int repeat = 1 + d.random.nextInt(200);
                event.put("payload", Map.of("text", "Café – 東京 – ✓ – Ünïcødé ".repeat(repeat)));
                yield single(d, Category.LARGE_PAYLOAD, "unicode-payload", "Unicode text repeated " + repeat + " times", event,
                        Outcomes.normal());
            }
            default -> {
                int length = 500 + d.random.nextInt(501);
                attributes.put("note", "y".repeat(length));
                attributes.put("detail", "z".repeat(length));
                attributes.put("comment", "w".repeat(length));
                yield single(d, Category.LARGE_PAYLOAD, "long-attribute-values", "Three attribute values of " + length + " characters",
                        event, Outcomes.normal());
            }
        };
    }

    private Scenario resilience(Draft d) {
        Map<String, Object> event = events.normalEvent(d.eventId(0), d.random);
        if (d.random.nextBoolean()) {
            int latency = 2000 + d.random.nextInt(8001);
            EventFactory.attributes(event).put("latencyMs", latency);
            return single(d, Category.AI_RESILIENCE, "resilience-latency",
                    "Flagged event (latencyMs=" + latency + "); checks the AI failure contract for the observed AI status", event,
                    Outcomes.flagged(CoreContract.RULE_THRESHOLD, "HIGH", CoreContract.SRC_LATENCY + "; " + CoreContract.SRC_RESILIENCE)
                            .toBuilder().aiResilienceInvariant(true).build());
        }
        event.put("severity", "CRITICAL");
        return single(d, Category.AI_RESILIENCE, "resilience-critical",
                "Flagged event (severity CRITICAL); checks the AI failure contract for the observed AI status", event,
                Outcomes.flagged(CoreContract.RULE_REPORTED_SEVERITY, "CRITICAL", CoreContract.SRC_SEVERITY + "; " + CoreContract.SRC_RESILIENCE)
                        .toBuilder().aiResilienceInvariant(true).build());
    }

    private String padded(String eventId, int length) {
        return eventId + "-" + "x".repeat(length - eventId.length() - 1);
    }

    private Scenario single(Draft d, Category category, String template, String description, Map<String, Object> event,
                            ExpectedOutcome expected) {
        return build(d, category, template, description, List.of(step(event, expected)), true, null, null);
    }

    private Scenario build(Draft d, Category category, String template, String description, List<ScenarioStep> steps,
                           boolean scorable, String unscoredReason, Set<String> acceptableAiAssessments) {
        return Scenario.builder()
                .scenarioId(d.scenarioId)
                .index(d.index)
                .category(category)
                .template(template)
                .description(description)
                .steps(steps)
                .scorable(scorable)
                .unscoredReason(unscoredReason)
                .acceptableAiAssessments(acceptableAiAssessments)
                .build();
    }

    private ScenarioStep step(Map<String, Object> event, ExpectedOutcome expected) {
        Object eventId = event.get("eventId");
        return rawStep(eventId instanceof String id ? id : null, events.toJson(event), ScenarioStep.JSON, expected);
    }

    private ScenarioStep rawStep(String eventId, String body, String contentType, ExpectedOutcome expected) {
        return ScenarioStep.builder().eventId(eventId).body(body).contentType(contentType).expected(expected).build();
    }

    private static final class Draft {
        private final int index;
        private final String scenarioId;
        private final String idPrefix;
        private final Random random;

        private Draft(int index, String scenarioId, String idPrefix, Random random) {
            this.index = index;
            this.scenarioId = scenarioId;
            this.idPrefix = idPrefix;
            this.random = random;
        }

        private String eventId(int step) {
            return idPrefix + "-" + step;
        }
    }
}
