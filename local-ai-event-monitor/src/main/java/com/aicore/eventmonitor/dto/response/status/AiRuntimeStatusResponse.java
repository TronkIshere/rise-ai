package com.aicore.eventmonitor.dto.response.status;

import com.aicore.eventmonitor.core.analysis.AiRuntimeState;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiRuntimeStatusResponse {
    String runtime;
    AiRuntimeState state;
    String modelName;
    String detail;
}
