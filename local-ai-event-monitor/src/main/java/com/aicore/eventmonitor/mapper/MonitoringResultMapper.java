package com.aicore.eventmonitor.mapper;

import com.aicore.eventmonitor.core.analysis.AiAnalysis;
import com.aicore.eventmonitor.core.result.MonitoringResult;
import com.aicore.eventmonitor.core.rule.RuleFinding;
import com.aicore.eventmonitor.dto.response.monitoring.AiAnalysisResponse;
import com.aicore.eventmonitor.dto.response.monitoring.MonitoringResultResponse;
import com.aicore.eventmonitor.dto.response.monitoring.RuleFindingResponse;
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
                .createdAt(result.getCreatedAt())
                .processingDurationMs(result.getProcessingDurationMs())
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
