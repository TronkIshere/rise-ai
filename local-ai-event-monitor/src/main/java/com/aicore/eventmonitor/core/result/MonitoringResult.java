package com.aicore.eventmonitor.core.result;

import com.aicore.eventmonitor.core.analysis.AiAnalysis;
import com.aicore.eventmonitor.core.analysis.RiskLevel;
import com.aicore.eventmonitor.core.rule.RuleDecision;
import com.aicore.eventmonitor.core.rule.RuleFinding;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MonitoringResult {
    String eventId;
    String source;
    String eventType;
    String correlationId;
    MonitoringStatus monitoringStatus;
    RuleDecision ruleDecision;

    @Builder.Default
    List<RuleFinding> ruleFindings = List.of();

    AiAnalysis aiAnalysis;
    RiskLevel overallRiskLevel;
    boolean reviewRequired;

    @Builder.Default
    List<String> reviewReasons = List.of();

    Instant createdAt;
    long processingDurationMs;
}
