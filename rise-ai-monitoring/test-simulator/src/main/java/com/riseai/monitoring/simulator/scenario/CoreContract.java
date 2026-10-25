package com.riseai.monitoring.simulator.scenario;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

public final class CoreContract {

    private CoreContract() {
    }

    public static final String MONITOR_PATH = "/api/v1/events/monitor";
    public static final String STATUS_PATH = "/api/v1/monitoring/status";
    public static final String RULES_PATH = "/api/v1/monitoring/rules";
    public static final String HEALTH_PATH = "/actuator/health";

    public static final String RULE_REQUIRED_FIELDS = "required-fields";
    public static final String RULE_TIMESTAMP = "timestamp-validity";
    public static final String RULE_EVENT_TYPE = "supported-event-type";
    public static final String RULE_PAYLOAD_LIMITS = "payload-limits";
    public static final String RULE_THRESHOLD = "attribute-threshold";
    public static final String RULE_REPORTED_SEVERITY = "reported-severity";
    public static final List<String> RULE_IDS = List.of(RULE_REQUIRED_FIELDS, RULE_TIMESTAMP, RULE_EVENT_TYPE,
            RULE_PAYLOAD_LIMITS, RULE_THRESHOLD, RULE_REPORTED_SEVERITY);

    public static final List<String> ALLOWED_EVENT_TYPES = List.of("request.completed", "job.completed", "disk.status",
            "service.heartbeat", "deployment.finished");
    public static final String UNLISTED_EVENT_TYPE = "sim.unlisted.type";
    public static final BigDecimal LATENCY_MAX = new BigDecimal("1000");
    public static final BigDecimal FREE_DISK_MIN = new BigDecimal("10");

    public static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
    public static final Duration MAX_EVENT_AGE = Duration.ofDays(7);
    public static final int MAX_PAYLOAD_BYTES = 65536;
    public static final int MAX_ATTRIBUTES = 50;
    public static final int MAX_ATTRIBUTE_VALUE_LENGTH = 1000;
    public static final int MAX_PAYLOAD_DEPTH = 20;
    public static final int MAX_EVENT_ID_LENGTH = 128;
    public static final int MAX_CORRELATION_ID_LENGTH = 64;

    public static final int ERROR_INVALID_DATA = 1000;
    public static final int ERROR_MALFORMED_REQUEST = 1001;
    public static final int ERROR_UNSUPPORTED_MEDIA_TYPE = 1004;
    public static final int ERROR_INVALID_EVENT = 2000;
    public static final int ERROR_EVENT_ID_CONFLICT = 2001;

    public static final String SRC_NORMAL = "No rule condition met under the simulator Core profile: latencyMs <= 1000, freeDiskPercent >= 10, severity below HIGH, allowed type, valid timestamps";
    public static final String SRC_LATENCY = "attribute-threshold flags latencyMs strictly above max=1000 with severity HIGH (simulator Core profile)";
    public static final String SRC_FREE_DISK = "attribute-threshold flags freeDiskPercent strictly below min=10 with severity MEDIUM (simulator Core profile)";
    public static final String SRC_NON_NUMERIC = "attribute-threshold flags a non-numeric threshold attribute with severity MEDIUM (docs/EVENT_LIFECYCLE.md)";
    public static final String SRC_SEVERITY = "reported-severity flags a reported severity >= HIGH with that severity (Core default flag-reported-severity-at=HIGH)";
    public static final String SRC_EVENT_TYPE = "supported-event-type rejects types outside allowed-event-types (simulator Core profile)";
    public static final String SRC_TIMESTAMP_SKEW = "timestamp-validity rejects occurredAt later than receivedAt + 5m (Core default max-clock-skew)";
    public static final String SRC_TIMESTAMP_AGE = "timestamp-validity rejects occurredAt earlier than receivedAt - 7d (Core default max-event-age)";
    public static final String SRC_PAYLOAD_SIZE = "payload-limits rejects payloads larger than 65536 bytes (Core default max-payload-bytes)";
    public static final String SRC_ATTRIBUTE_COUNT = "payload-limits rejects more than 50 attributes (Core default max-attributes)";
    public static final String SRC_VALIDATION = "API contract: Bean Validation failure returns HTTP 400 with code 1000 (docs/API_CONTRACT.md)";
    public static final String SRC_MALFORMED = "API contract: unreadable JSON or invalid enum/timestamp returns HTTP 400 with code 1001 (docs/API_CONTRACT.md)";
    public static final String SRC_INVALID_EVENT = "API contract: normalization failure returns HTTP 400 with code 2000 (docs/API_CONTRACT.md)";
    public static final String SRC_MEDIA_TYPE = "API contract: unsupported content type returns HTTP 415 with code 1004 (docs/API_CONTRACT.md)";
    public static final String SRC_DUPLICATE_REPLAY = "Deduplication: same source+eventId and content replays the stored result with duplicate=true (docs/EVENT_LIFECYCLE.md)";
    public static final String SRC_DUPLICATE_CONFLICT = "Deduplication: same source+eventId with different content returns HTTP 409 with code 2001";
    public static final String SRC_DUPLICATE_SCOPE = "Deduplication scope is source+eventId, so a different source is a new event";
    public static final String SRC_CORRELATION = "API contract: correlationId from the event body is echoed in the result";
    public static final String SRC_SEQUENCE = "Core evaluates each event independently; no cross-event correlation exists (docs/ARCHITECTURE.md)";
    public static final String SRC_RESILIENCE = "AI failure contract: rule findings and rule-derived risk are preserved; a non-completed AI analysis yields INCONCLUSIVE (or COMPLETED_WITHOUT_AI) with reviewRequired=true (docs/AI_AND_OLLAMA.md)";
    public static final String SRC_AI_RUBRIC = "Simulator rubric: an unambiguous multi-signal failure must be assessed NEEDS_REVIEW or LIKELY_ANOMALOUS";
}
