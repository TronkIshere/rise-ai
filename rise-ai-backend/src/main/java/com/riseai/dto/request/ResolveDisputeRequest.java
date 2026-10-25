package com.riseai.dto.request;

import com.riseai.enums.ResolutionOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveDisputeRequest(@NotNull ResolutionOutcome outcome, @NotBlank @Size(max = 5000) String resolution) {
}
