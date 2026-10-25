package com.riseai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisputeMessageRequest(@NotBlank @Size(max = 5000) String message) {
}
