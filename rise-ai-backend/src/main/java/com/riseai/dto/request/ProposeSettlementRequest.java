package com.riseai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProposeSettlementRequest(@NotBlank @Size(max = 2000) String reason) {
}
