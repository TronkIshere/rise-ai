package com.aicore.eventmonitor.analysis;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AnalysisPrompt {
    String systemPrompt;
    String userPrompt;
    Map<String, Object> outputSchema;
    boolean eventDataTruncated;
}
