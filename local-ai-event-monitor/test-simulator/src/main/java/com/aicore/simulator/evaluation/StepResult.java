package com.aicore.simulator.evaluation;

import com.aicore.simulator.client.CoreResponse;
import com.aicore.simulator.client.TransportOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder(toBuilder = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StepResult {
    String eventId;
    TransportOutcome outcome;
    String transportError;
    int httpStatus;
    boolean bodyReadable;
    Integer errorCode;
    String responseEventId;
    String monitoringStatus;
    String ruleDecision;

    @Builder.Default
    List<String> ruleIds = List.of();

    String riskLevel;
    Boolean duplicate;
    Boolean reviewRequired;
    String aiStatus;
    String assessment;
    String correlationId;
    String createdAt;
    long latencyNanos;
    Boolean expectedAnomalous;
    Boolean actualAnomalous;

    public static StepResult from(String eventId, CoreResponse response) {
        StepResultBuilder builder = StepResult.builder()
                .eventId(eventId)
                .outcome(response.getOutcome())
                .transportError(response.getError())
                .httpStatus(response.getHttpStatus())
                .latencyNanos(response.getLatencyNanos());
        JsonNode json = response.getBody();
        if (!response.isReceived() || json == null || !json.isObject()) {
            return builder.build();
        }
        builder.bodyReadable(true);
        if (json.path("status").isInt()) {
            builder.errorCode(json.get("status").asInt());
        }
        JsonNode data = json.path("data");
        if (!data.isObject()) {
            return builder.build();
        }
        List<String> ruleIds = new ArrayList<>();
        for (JsonNode finding : data.path("ruleFindings")) {
            ruleIds.add(finding.path("ruleId").asText());
        }
        JsonNode ai = data.path("aiAnalysis");
        return builder
                .responseEventId(text(data, "eventId"))
                .monitoringStatus(text(data, "monitoringStatus"))
                .ruleDecision(text(data, "ruleDecision"))
                .ruleIds(List.copyOf(ruleIds))
                .riskLevel(text(data, "overallRiskLevel"))
                .duplicate(bool(data, "duplicate"))
                .reviewRequired(bool(data, "reviewRequired"))
                .aiStatus(text(ai, "analysisStatus"))
                .assessment(text(ai, "assessment"))
                .correlationId(text(data, "correlationId"))
                .createdAt(text(data, "createdAt"))
                .build();
    }

    public boolean isResponseReceived() {
        return outcome == TransportOutcome.RESPONSE;
    }

    public String summarize() {
        if (!isResponseReceived()) {
            return outcome + (transportError == null ? "" : " (" + transportError + ")");
        }
        if (!bodyReadable) {
            return "HTTP " + httpStatus + " unreadable body";
        }
        if (monitoringStatus == null) {
            return "HTTP " + httpStatus + " code=" + errorCode;
        }
        return "HTTP " + httpStatus + " " + monitoringStatus + " decision=" + ruleDecision + " rules=" + ruleIds
                + " risk=" + riskLevel + " ai=" + aiStatus + (assessment == null ? "" : "/" + assessment)
                + " duplicate=" + duplicate;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isBoolean() ? null : value.booleanValue();
    }
}
