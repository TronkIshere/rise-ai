package com.riseai.milestone;

import com.riseai.common.Money;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class MilestoneDtos {
    private MilestoneDtos() {
    }

    public record MilestoneRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal plannedAmount,
            @NotNull Instant startAt,
            @NotNull Instant dueAt,
            @NotBlank @Size(max = 5000) String releaseConditions,
            @NotBlank @Size(max = 5000) String expectedDeliverables) {
    }

    public record MilestoneResponse(UUID id, UUID campaignId, String title, String description, Integer sequenceNumber,
                                    BigDecimal plannedAmount, BigDecimal reservedAmount, BigDecimal releasedAmount,
                                    BigDecimal remainingAmount, BigDecimal releasedPercent, Instant startAt, Instant dueAt,
                                    String releaseConditions, String expectedDeliverables, MilestoneStatus milestoneStatus,
                                    Instant submittedAt, Instant reviewedAt, Instant completedAt, Instant createdAt, Long evidenceCount) {
        public static MilestoneResponse from(Milestone m, Long evidenceCount) {
            return new MilestoneResponse(m.getId(), m.getCampaignId(), m.getTitle(), m.getDescription(), m.getSequenceNumber(),
                    m.getPlannedAmount(), m.getReservedAmount(), m.getReleasedAmount(), m.remainingAmount(),
                    Money.percent(m.getReleasedAmount(), m.getPlannedAmount()), m.getStartAt(), m.getDueAt(), m.getReleaseConditions(),
                    m.getExpectedDeliverables(), m.getMilestoneStatus(), m.getSubmittedAt(), m.getReviewedAt(), m.getCompletedAt(),
                    m.getCreatedAt(), evidenceCount);
        }
    }
}
