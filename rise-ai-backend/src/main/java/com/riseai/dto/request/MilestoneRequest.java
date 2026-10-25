package com.riseai.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record MilestoneRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal plannedAmount,
        @NotNull Instant startAt,
        @NotNull Instant dueAt,
        @NotBlank @Size(max = 5000) String releaseConditions,
        @NotBlank @Size(max = 5000) String expectedDeliverables) {
}
