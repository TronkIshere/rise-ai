package com.riseai.monitoring.core.analysis;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AiAnalysis {
    AnalysisStatus status;
    String statusDetail;
    String schemaVersion;
    AiAssessment assessment;
    AiConfidence confidence;
    String explanation;

    @Builder.Default
    List<String> possibleCauses = List.of();

    @Builder.Default
    List<String> missingEvidence = List.of();

    Boolean suggestedReview;
    String modelName;
    long analysisDurationMs;

    public static AiAnalysis skipped(String statusDetail) {
        return AiAnalysis.builder()
                .status(AnalysisStatus.SKIPPED)
                .statusDetail(statusDetail)
                .build();
    }

    public static AiAnalysis disabled(String statusDetail) {
        return AiAnalysis.builder()
                .status(AnalysisStatus.DISABLED)
                .statusDetail(statusDetail)
                .build();
    }

    public static AiAnalysis notCompleted(AnalysisStatus status, String statusDetail, String modelName,
                                          long analysisDurationMs) {
        return AiAnalysis.builder()
                .status(status)
                .statusDetail(statusDetail)
                .modelName(modelName)
                .analysisDurationMs(analysisDurationMs)
                .build();
    }

    public boolean isCompleted() {
        return status == AnalysisStatus.COMPLETED;
    }
}
