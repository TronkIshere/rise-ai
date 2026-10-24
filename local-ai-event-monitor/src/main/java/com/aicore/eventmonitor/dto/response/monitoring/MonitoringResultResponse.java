package com.aicore.eventmonitor.dto.response.monitoring;

import com.aicore.eventmonitor.core.analysis.RiskLevel;
import com.aicore.eventmonitor.core.result.MonitoringStatus;
import com.aicore.eventmonitor.core.rule.RuleDecision;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MonitoringResultResponse {
    String eventId;
    String source;
    String eventType;
    String correlationId;
    MonitoringStatus monitoringStatus;
    boolean duplicate;
    RuleDecision ruleDecision;
    List<RuleFindingResponse> ruleFindings;
    AiAnalysisResponse aiAnalysis;
    RiskLevel overallRiskLevel;
    boolean reviewRequired;
    List<String> reviewReasons;
    Instant createdAt;
    long processingDurationMs;
}
