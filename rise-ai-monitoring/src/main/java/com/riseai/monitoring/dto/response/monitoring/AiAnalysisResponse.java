package com.riseai.monitoring.dto.response.monitoring;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.riseai.monitoring.core.analysis.AiAssessment;
import com.riseai.monitoring.core.analysis.AiConfidence;
import com.riseai.monitoring.core.analysis.AnalysisStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
