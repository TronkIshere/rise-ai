package com.riseai.dto.request;

import com.riseai.enums.FindingReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewFindingRequest(@NotNull FindingReviewStatus status, @NotBlank @Size(max = 2000) String note) {
}
