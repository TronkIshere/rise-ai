package com.aicore.eventmonitor.result;

import com.aicore.eventmonitor.analysis.RiskLevelNormalizer;
import com.aicore.eventmonitor.configuration.MonitoringProperties;
import com.aicore.eventmonitor.core.analysis.AiAnalysis;
import com.aicore.eventmonitor.core.analysis.AiAssessment;
import com.aicore.eventmonitor.core.analysis.AnalysisStatus;
import com.aicore.eventmonitor.core.analysis.RiskLevel;
import com.aicore.eventmonitor.core.event.Severity;
import com.aicore.eventmonitor.core.result.MonitoringResult;
import com.aicore.eventmonitor.core.result.MonitoringStatus;
import com.aicore.eventmonitor.core.rule.RuleDecision;
import com.aicore.eventmonitor.core.rule.RuleEvaluation;
import com.aicore.eventmonitor.core.rule.RuleFinding;
import com.aicore.eventmonitor.testsupport.TestEvents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringResultAssemblerTest {

    private static final RuleFinding HIGH_FLAG = RuleFinding.flag("attribute-threshold", Severity.HIGH, "above maximum");
    private static final RuleFinding MEDIUM_FLAG = RuleFinding.flag("attribute-threshold", Severity.MEDIUM, "not numeric");

    private final MonitoringProperties properties = new MonitoringProperties();
    private final MonitoringResultAssembler assembler = new MonitoringResultAssembler(properties, new RiskLevelNormalizer());

    @Test
    void rulesOnlyResultIsCompletedWithLowRiskAndNoReview() {
        MonitoringResult result = assemble(evaluation(RuleDecision.RULES_SUFFICIENT), AiAnalysis.skipped("not required"));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.COMPLETED);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.isReviewRequired()).isFalse();
        assertThat(result.getEventId()).isEqualTo("evt-1");
        assertThat(result.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void rejectedResultHasUnknownRiskAndNoReview() {
        MonitoringResult result = assemble(evaluation(RuleDecision.REJECT, RuleFinding.reject("timestamp-validity", "future")),
                AiAnalysis.skipped("rejected"));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.REJECTED);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(result.isReviewRequired()).isFalse();
    }

    @Test
    void riskLevelIsDeterminedByRulesWhateverTheAiReturns() {
        List<AiAnalysis> aiOutcomes = List.of(
                TestEvents.completedAnalysis(AiAssessment.LIKELY_ANOMALOUS, true),
                TestEvents.completedAnalysis(AiAssessment.CONSISTENT_WITH_EXPECTED_BEHAVIOR, false),
                AiAnalysis.notCompleted(AnalysisStatus.TIMEOUT, "timeout", "test-model", 500),
                AiAnalysis.notCompleted(AnalysisStatus.INVALID_RESPONSE, "bad", "test-model", 5),
                AiAnalysis.notCompleted(AnalysisStatus.UNAVAILABLE, "offline", "test-model", 1),
                AiAnalysis.disabled("disabled"));

        for (AiAnalysis aiOutcome : aiOutcomes) {
            assertThat(assemble(evaluation(RuleDecision.REQUIRES_AI_ANALYSIS, MEDIUM_FLAG), aiOutcome).getOverallRiskLevel())
                    .isEqualTo(RiskLevel.MEDIUM);
        }
    }

    @Test
    void ruleFindingsArePreservedWhenAiFails() {
        MonitoringResult result = assemble(evaluation(RuleDecision.REQUIRES_AI_ANALYSIS, HIGH_FLAG),
                AiAnalysis.notCompleted(AnalysisStatus.TIMEOUT, "timeout", "test-model", 500));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.INCONCLUSIVE);
        assertThat(result.getRuleFindings()).containsExactly(HIGH_FLAG);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.getReviewReasons()).containsExactly(
                "rule attribute-threshold reported HIGH severity", "AI analysis did not complete: TIMEOUT");
    }

    @Test
    void missingAiOutputWithoutRuleFindingsIsNeverReportedAsLowRisk() {
        MonitoringResult result = assemble(evaluation(RuleDecision.REQUIRES_AI_ANALYSIS),
                AiAnalysis.notCompleted(AnalysisStatus.UNAVAILABLE, "offline", "test-model", 2));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.INCONCLUSIVE);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(result.isReviewRequired()).isTrue();
    }

    @Test
    void aiAssessmentInfluencesReviewButNotRisk() {
        MonitoringResult result = assemble(evaluation(RuleDecision.REQUIRES_AI_ANALYSIS),
                TestEvents.completedAnalysis(AiAssessment.LIKELY_ANOMALOUS, false));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.COMPLETED);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.getReviewReasons()).containsExactly("AI assessment LIKELY_ANOMALOUS");
    }

    @Test
    void reassuringAiAssessmentDoesNotCreateAReview() {
        MonitoringResult result = assemble(evaluation(RuleDecision.REQUIRES_AI_ANALYSIS, MEDIUM_FLAG),
                TestEvents.completedAnalysis(AiAssessment.CONSISTENT_WITH_EXPECTED_BEHAVIOR, false));

        assertThat(result.isReviewRequired()).isFalse();
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void aiReviewSuggestionFollowsConfiguredPolicy() {
        AiAnalysis suggestion = TestEvents.completedAnalysis(AiAssessment.CONSISTENT_WITH_EXPECTED_BEHAVIOR, true);
        assertThat(assemble(evaluation(RuleDecision.REQUIRES_AI_ANALYSIS), suggestion).getReviewReasons())
                .containsExactly("AI suggested human review");

        properties.getPolicy().setReviewWhenAiSuggests(false);
        assertThat(assemble(evaluation(RuleDecision.REQUIRES_AI_ANALYSIS), suggestion).isReviewRequired()).isFalse();
    }

    @Test
    void rulesOnlyFailureModeCompletesWithoutAiButStaysExplicitAboutTheGap() {
        properties.getAi().setOnFailure(MonitoringProperties.AiFailureMode.RULES_ONLY);

        MonitoringResult result = assemble(evaluation(RuleDecision.REQUIRES_AI_ANALYSIS),
                AiAnalysis.notCompleted(AnalysisStatus.UNAVAILABLE, "offline", "test-model", 3));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.COMPLETED_WITHOUT_AI);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(result.getReviewReasons()).containsExactly("AI analysis did not complete: UNAVAILABLE");
    }

    @Test
    void disabledAiCompletesWithoutAiUsingRuleRisk() {
        MonitoringResult result = assemble(evaluation(RuleDecision.REQUIRES_AI_ANALYSIS, HIGH_FLAG),
                AiAnalysis.disabled("AI analysis is disabled by configuration"));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.COMPLETED_WITHOUT_AI);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.getReviewReasons()).containsExactly("rule attribute-threshold reported HIGH severity");
    }

    @Test
    void ruleSeverityBelowTheReviewThresholdDoesNotRequireReview() {
        MonitoringResult result = assemble(evaluation(RuleDecision.REQUIRES_AI_ANALYSIS, MEDIUM_FLAG),
                AiAnalysis.disabled("disabled"));

        assertThat(result.isReviewRequired()).isFalse();
    }

    private MonitoringResult assemble(RuleEvaluation evaluation, AiAnalysis analysis) {
        return assembler.assemble(TestEvents.event().build(), evaluation, analysis, TestEvents.NOW, 15);
    }

    private RuleEvaluation evaluation(RuleDecision decision, RuleFinding... findings) {
        return RuleEvaluation.builder().decision(decision).findings(List.of(findings)).build();
    }
}
