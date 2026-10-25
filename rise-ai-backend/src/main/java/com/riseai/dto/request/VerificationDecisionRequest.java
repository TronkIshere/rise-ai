package com.riseai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificationDecisionRequest(@NotBlank @Size(max = 1000) String notes) {
}
