package com.riseai.monitoring.mapper;

import com.riseai.monitoring.core.analysis.AiAnalysis;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.core.result.MonitoringResult;
import com.riseai.monitoring.core.rule.RuleFinding;
import com.riseai.monitoring.dto.response.monitoring.AiAnalysisResponse;
import com.riseai.monitoring.dto.response.monitoring.FindingResponse;
import com.riseai.monitoring.dto.response.monitoring.MonitoringResultResponse;
import com.riseai.monitoring.dto.response.monitoring.RuleFindingResponse;
import org.springframework.stereotype.Component;

@Component
public class MonitoringResultMapper {

    public MonitoringResultResponse toResponse(MonitoringResult result, boolean duplicate) {
        return MonitoringResultResponse.builder()
                .eventId(result.getEventId())
                .source(result.getSource())
                .eventType(result.getEventType())
                .correlationId(result.getCorrelationId())
                .monitoringStatus(result.getMonitoringStatus())
                .duplicate(duplicate)
                .ruleDecision(result.getRuleDecision())
                .ruleFindings(result.getRuleFindings().stream().map(this::toFindingResponse).toList())
                .aiAnalysis(toAnalysisResponse(result.getAiAnalysis()))
                .overallRiskLevel(result.getOverallRiskLevel())
                .reviewRequired(result.isReviewRequired())
                .reviewReasons(result.getReviewReasons())
                .findings(result.getFindings().stream().map(this::toMonitoringFindingResponse).toList())
                .createdAt(result.getCreatedAt())
                .processingDurationMs(result.getProcessingDurationMs())
                .build();
    }

    private FindingResponse toMonitoringFindingResponse(Finding finding) {
        return FindingResponse.builder()
                .findingId(finding.getFindingId())
                .contractVersion(Finding.CONTRACT_VERSION)
                .detector(new FindingResponse.Detector(Finding.RULE_DETECTOR, finding.getRuleId(), finding.getRuleVersion()))
                .category(finding.getCategory())
                .severity(finding.getSeverity())
                .subject(new FindingResponse.Subject(finding.getSubjectType(), finding.getSubjectId()))
                .references(finding.getReferences())
                .sourceEventIds(finding.getSourceEventIds())
                .facts(finding.getFacts().stream().map(fact -> new FindingResponse.Fact(fact.name(), fact.value(), fact.sourceEventId())).toList())
                .explanation(finding.getExplanation())
                .recommendedAction(finding.getRecommendedAction())
                .observedAt(finding.getObservedAt())
                .aiNote(finding.getAiNote())
                .build();
    }

    private RuleFindingResponse toFindingResponse(RuleFinding finding) {
        return RuleFindingResponse.builder()
                .ruleId(finding.getRuleId())
                .effect(finding.getEffect())
                .severity(finding.getSeverity())
                .message(finding.getMessage())
                .build();
    }

    private AiAnalysisResponse toAnalysisResponse(AiAnalysis analysis) {
        return AiAnalysisResponse.builder()
                .analysisStatus(analysis.getStatus())
                .statusDetail(analysis.getStatusDetail())
                .schemaVersion(analysis.getSchemaVersion())
                .assessment(analysis.getAssessment())
                .confidence(analysis.getConfidence())
                .explanation(analysis.getExplanation())
                .possibleCauses(analysis.isCompleted() ? analysis.getPossibleCauses() : null)
                .missingEvidence(analysis.isCompleted() ? analysis.getMissingEvidence() : null)
                .suggestedReview(analysis.getSuggestedReview())
                .modelName(analysis.getModelName())
                .analysisDurationMs(analysis.getAnalysisDurationMs())
                .build();
    }
}
