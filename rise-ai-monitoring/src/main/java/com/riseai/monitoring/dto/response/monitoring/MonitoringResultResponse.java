package com.riseai.monitoring.dto.response.monitoring;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.riseai.monitoring.core.analysis.RiskLevel;
import com.riseai.monitoring.core.result.MonitoringStatus;
import com.riseai.monitoring.core.rule.RuleDecision;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
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
    List<FindingResponse> findings;
    Instant createdAt;
    long processingDurationMs;
}
