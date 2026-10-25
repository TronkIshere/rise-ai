package com.riseai.monitoring.simulator.runner;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CoreFacts {
    boolean reachable;
    String unreachableReason;

    @Builder.Default
    List<String> problems = List.of();

    @Builder.Default
    List<String> calibration = List.of();

    Boolean aiAnalysisEnabled;
    String aiTrigger;
    String aiFailureMode;
    Boolean ruleEngineEnabled;
    Boolean deduplicationEnabled;
    String aiRuntimeState;
    String modelName;
}
