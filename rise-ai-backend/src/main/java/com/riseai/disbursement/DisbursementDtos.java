package com.riseai.disbursement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class DisbursementDtos {
    private DisbursementDtos() {
    }

    public record CreateDisbursementRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 2000) String reason) {
    }

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
}
