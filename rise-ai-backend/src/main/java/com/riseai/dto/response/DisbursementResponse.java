package com.riseai.dto.response;

import com.riseai.entity.DisbursementRequest;
import com.riseai.enums.DisbursementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DisbursementResponse(UUID id, String requestCode, UUID campaignId, UUID milestoneId, UUID requestedBy,
                                   BigDecimal requestedAmount, String currency, String requestReason, DisbursementStatus requestStatus,
                                   Instant submittedAt, UUID reviewedBy, Instant reviewedAt, String decisionReason, UUID releasedBy,
                                   Instant releasedAt, Instant createdAt, Instant updatedAt, boolean simulated) {
    public static DisbursementResponse from(DisbursementRequest r) {
        return new DisbursementResponse(r.getId(), r.getRequestCode(), r.getCampaignId(), r.getMilestoneId(), r.getRequestedBy(),
                r.getRequestedAmount(), r.getCurrency(), r.getRequestReason(), r.getRequestStatus(), r.getSubmittedAt(),
                r.getReviewedBy(), r.getReviewedAt(), r.getDecisionReason(), r.getReleasedBy(), r.getReleasedAt(),
                r.getCreatedAt(), r.getUpdatedAt(), true);
    }
}
