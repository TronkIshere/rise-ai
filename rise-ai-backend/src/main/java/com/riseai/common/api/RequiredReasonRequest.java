package com.riseai.common.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequiredReasonRequest(@NotBlank @Size(max = 2000) String reason) {
}
