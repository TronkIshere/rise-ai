package com.aicore.eventmonitor.dto.response.monitoring;

import com.aicore.eventmonitor.core.analysis.AiAssessment;
import com.aicore.eventmonitor.core.analysis.AiConfidence;
import com.aicore.eventmonitor.core.analysis.AnalysisStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiAnalysisResponse {
    AnalysisStatus analysisStatus;
    String statusDetail;
    String schemaVersion;
    AiAssessment assessment;
    AiConfidence confidence;
    String explanation;
    List<String> possibleCauses;
    List<String> missingEvidence;
    Boolean suggestedReview;
    String modelName;
    long analysisDurationMs;
}
