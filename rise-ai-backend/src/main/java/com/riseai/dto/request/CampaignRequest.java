package com.riseai.dto.request;

import com.riseai.entity.BudgetItem;
import com.riseai.enums.CampaignCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignRequest(
        @NotNull UUID organizationId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 500) String shortDescription,
        @NotBlank @Size(max = 20000) String fullDescription,
        @Size(max = 500) @Pattern(regexp = "^(https://\\S+)?$") String coverImageUrl,
        @Size(max = 10) List<@NotBlank @Size(max = 500) @Pattern(regexp = "^https://\\S+$") String> galleryImageUrls,
        @NotNull CampaignCategory category,
        @NotBlank @Size(max = 2000) String beneficiaryDescription,
        @NotNull @DecimalMin("1000.00") @Digits(integer = 17, fraction = 2) BigDecimal targetAmount,
        @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal minimumDonationAmount,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotBlank @Size(max = 255) String location,
        @NotNull @Positive Integer beneficiaryCount,
        @NotBlank @Size(max = 5000) String problemStatement,
        @NotBlank @Size(max = 5000) String solutionDescription,
        @NotBlank @Size(max = 5000) String impactGoals,
        @Size(max = 50) List<@Valid BudgetItem> budgetBreakdown) {
}
