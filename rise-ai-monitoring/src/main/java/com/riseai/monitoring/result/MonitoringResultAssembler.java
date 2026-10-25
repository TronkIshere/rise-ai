package com.riseai.monitoring.result;

import com.riseai.monitoring.analysis.RiskLevelNormalizer;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.analysis.AiAnalysis;
import com.riseai.monitoring.core.analysis.AnalysisStatus;
import com.riseai.monitoring.core.analysis.RiskLevel;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.core.result.MonitoringResult;
import com.riseai.monitoring.core.result.MonitoringStatus;
import com.riseai.monitoring.core.rule.RuleDecision;
import com.riseai.monitoring.core.rule.RuleEffect;
import com.riseai.monitoring.core.rule.RuleEvaluation;
import com.riseai.monitoring.core.rule.RuleFinding;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MonitoringResultAssembler {
    MonitoringProperties properties;
    RiskLevelNormalizer riskLevelNormalizer;

    public MonitoringResult assemble(MonitoringEvent event, RuleEvaluation evaluation, AiAnalysis aiAnalysis,
                                     Instant createdAt, long processingDurationMs) {
        return assemble(event, evaluation, aiAnalysis, List.of(), createdAt, processingDurationMs);
    }

    public MonitoringResult assemble(MonitoringEvent event, RuleEvaluation evaluation, AiAnalysis aiAnalysis, List<Finding> findings,
                                     Instant createdAt, long processingDurationMs) {
        MonitoringStatus status = resolveStatus(evaluation, aiAnalysis);
        List<String> reviewReasons = resolveReviewReasons(status, evaluation, aiAnalysis);
        return MonitoringResult.builder()
                .eventId(event.getEventId())
                .source(event.getSource())
                .eventType(event.getEventType())
                .correlationId(event.getCorrelationId())
                .monitoringStatus(status)
                .ruleDecision(evaluation.getDecision())
                .ruleFindings(evaluation.getFindings())
                .aiAnalysis(aiAnalysis)
                .overallRiskLevel(resolveRiskLevel(status, evaluation, aiAnalysis))
                .reviewRequired(!reviewReasons.isEmpty())
                .reviewReasons(reviewReasons)
                .findings(status == MonitoringStatus.REJECTED ? List.of() : List.copyOf(findings))
                .createdAt(createdAt)
                .processingDurationMs(processingDurationMs)
                .build();
    }

    private MonitoringStatus resolveStatus(RuleEvaluation evaluation, AiAnalysis aiAnalysis) {
        return switch (evaluation.getDecision()) {
            case REJECT -> MonitoringStatus.REJECTED;
            case RULES_SUFFICIENT -> MonitoringStatus.COMPLETED;
            case REQUIRES_AI_ANALYSIS -> {
                if (aiAnalysis.isCompleted()) {
                    yield MonitoringStatus.COMPLETED;
                }
                if (isIntentionallyWithoutAi(aiAnalysis)
                        || properties.getAi().getOnFailure() == MonitoringProperties.AiFailureMode.RULES_ONLY) {
                    yield MonitoringStatus.COMPLETED_WITHOUT_AI;
                }
                yield MonitoringStatus.INCONCLUSIVE;
            }
        };
    }

    private RiskLevel resolveRiskLevel(MonitoringStatus status, RuleEvaluation evaluation, AiAnalysis aiAnalysis) {
        if (status == MonitoringStatus.REJECTED) {
            return RiskLevel.UNKNOWN;
        }
        RiskLevel ruleRisk = evaluation.getFindings().stream()
                .filter(finding -> finding.getEffect() == RuleEffect.FLAG)
                .map(finding -> riskLevelNormalizer.fromSeverity(finding.getSeverity()))
                .reduce(RiskLevel::highest)
                .orElse(null);
        if (ruleRisk != null) {
            return ruleRisk;
        }
        boolean aiRequiredButMissing = evaluation.getDecision() == RuleDecision.REQUIRES_AI_ANALYSIS
                && !aiAnalysis.isCompleted() && !isIntentionallyWithoutAi(aiAnalysis);
        return aiRequiredButMissing ? RiskLevel.UNKNOWN : RiskLevel.LOW;
    }

    private List<String> resolveReviewReasons(MonitoringStatus status, RuleEvaluation evaluation, AiAnalysis aiAnalysis) {
        if (status == MonitoringStatus.REJECTED) {
            return List.of();
        }
        MonitoringProperties.Policy policy = properties.getPolicy();
        Set<String> reasons = new LinkedHashSet<>();
        for (RuleFinding finding : evaluation.getFindings()) {
            if (finding.getEffect() == RuleEffect.FLAG
                    && finding.getSeverity().compareTo(policy.getReviewRuleSeverity()) >= 0) {
                reasons.add("rule " + finding.getRuleId() + " reported " + finding.getSeverity() + " severity");
            }
        }
        if (evaluation.getDecision() == RuleDecision.REQUIRES_AI_ANALYSIS
                && !aiAnalysis.isCompleted() && !isIntentionallyWithoutAi(aiAnalysis)) {
            reasons.add("AI analysis did not complete: " + aiAnalysis.getStatus());
        }
        if (aiAnalysis.isCompleted()) {
            if (policy.getReviewAiAssessments().contains(aiAnalysis.getAssessment())) {
                reasons.add("AI assessment " + aiAnalysis.getAssessment());
            }
            if (policy.isReviewWhenAiSuggests() && Boolean.TRUE.equals(aiAnalysis.getSuggestedReview())) {
                reasons.add("AI suggested human review");
            }
        }
        return List.copyOf(new ArrayList<>(reasons));
    }

    private boolean isIntentionallyWithoutAi(AiAnalysis aiAnalysis) {
        return aiAnalysis.getStatus() == AnalysisStatus.DISABLED || aiAnalysis.getStatus() == AnalysisStatus.SKIPPED;
    }
}
