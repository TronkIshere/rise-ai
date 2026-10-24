package com.aicore.eventmonitor.core.analysis;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AiRuntimeStatus {
    String runtime;
    AiRuntimeState state;
    String modelName;
    String detail;
}
