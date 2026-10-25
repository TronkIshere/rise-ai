package com.riseai.dto.response;

import com.riseai.entity.Milestone;
import com.riseai.enums.MilestoneStatus;
import com.riseai.util.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
