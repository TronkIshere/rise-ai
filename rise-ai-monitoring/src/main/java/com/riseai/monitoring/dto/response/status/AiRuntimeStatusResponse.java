package com.riseai.monitoring.dto.response.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.riseai.monitoring.core.analysis.AiRuntimeState;
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
