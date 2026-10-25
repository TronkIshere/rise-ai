package com.riseai.monitoring.testsupport;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.riseai.monitoring.analysis.AiAssessmentOutput;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.analysis.AiAnalysis;
import com.riseai.monitoring.core.analysis.AiAssessment;
import com.riseai.monitoring.core.analysis.AiConfidence;
import com.riseai.monitoring.core.analysis.AnalysisStatus;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.dto.request.event.MonitoringEventRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestEvents {

    public static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    private TestEvents() {
    }

    public static MonitoringProperties properties() {
        MonitoringProperties properties = new MonitoringProperties();
        MonitoringProperties.AttributeThreshold threshold = new MonitoringProperties.AttributeThreshold();
        threshold.setAttribute("latencyMs");
        threshold.setMax(new BigDecimal("1000"));
        properties.getRules().getThresholds().add(threshold);
        return properties;
    }

    public static MonitoringEventRequest request(String eventId) {
        MonitoringEventRequest request = new MonitoringEventRequest();
        request.setEventId(eventId);
        request.setEventType("request.completed");
        request.setSource("edge-gateway");
        request.setOccurredAt(NOW.minusSeconds(30));
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("region", "eu-west");
        attributes.put("latencyMs", 120);
        request.setAttributes(attributes);
        request.setPayload(JsonNodeFactory.instance.objectNode().put("path", "/orders").put("status", 200));
        return request;
    }

    public static MonitoringEvent.MonitoringEventBuilder event() {
        return MonitoringEvent.builder()
                .eventId("evt-1")
                .eventType("request.completed")
                .source("edge-gateway")
                .occurredAt(NOW.minusSeconds(30))
                .receivedAt(NOW)
                .correlationId("corr-1")
                .attributes(Map.of("latencyMs", 120, "region", "eu-west"))
                .payloadBytes(40)
                .fingerprint("fingerprint-1");
    }

    public static final String VALID_ASSESSMENT_JSON = "{\"assessment\":\"NEEDS_REVIEW\",\"confidence\":\"MEDIUM\","
            + "\"explanation\":\"Latency is far above the configured maximum; the cause cannot be determined from this event.\","
            + "\"possibleCauses\":[\"Upstream dependency slowness\"],"
            + "\"missingEvidence\":[\"Recent latency baseline for this source\"],\"suggestedReview\":true}";

    public static AiAnalysis completedAnalysis(AiAssessment assessment, boolean suggestedReview) {
        return AiAnalysis.builder()
                .status(AnalysisStatus.COMPLETED)
                .schemaVersion(AiAssessmentOutput.SCHEMA_VERSION)
                .assessment(assessment)
                .confidence(AiConfidence.MEDIUM)
                .explanation("The latency value is unusual for this source.")
                .possibleCauses(List.of("Upstream dependency slowness"))
                .missingEvidence(List.of("Recent latency baseline"))
                .suggestedReview(suggestedReview)
                .modelName("test-model")
                .analysisDurationMs(12)
                .build();
    }
}
