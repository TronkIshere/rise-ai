package com.riseai.monitoring.analysis;

import com.riseai.monitoring.core.analysis.AiAssessment;
import com.riseai.monitoring.core.analysis.AiConfidence;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AiAssessmentOutput {

    public static final String SCHEMA_VERSION = "monitoring-assessment.v1";

    public static final String FIELD_ASSESSMENT = "assessment";
    public static final String FIELD_CONFIDENCE = "confidence";
    public static final String FIELD_EXPLANATION = "explanation";
    public static final String FIELD_POSSIBLE_CAUSES = "possibleCauses";
    public static final String FIELD_MISSING_EVIDENCE = "missingEvidence";
    public static final String FIELD_SUGGESTED_REVIEW = "suggestedReview";

    public static final List<String> FIELDS = List.of(FIELD_ASSESSMENT, FIELD_CONFIDENCE, FIELD_EXPLANATION,
            FIELD_POSSIBLE_CAUSES, FIELD_MISSING_EVIDENCE, FIELD_SUGGESTED_REVIEW);

    AiAssessment assessment;
    AiConfidence confidence;
    String explanation;
    List<String> possibleCauses;
    List<String> missingEvidence;
    boolean suggestedReview;
}
