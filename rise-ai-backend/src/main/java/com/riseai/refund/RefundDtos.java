package com.riseai.refund;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class RefundDtos {
    private RefundDtos() {
    }

    public record CreateRefundRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 2000) String reason) {
    }

    public record RefundResponse(UUID id, String refundCode, UUID donationId, UUID campaignId, UUID requestedBy, BigDecimal amount,
                                 String currency, String reason, RefundStatus refundStatus, UUID reviewedBy, Instant reviewedAt,
                                 String decisionReason, UUID recordedBy, Instant recordedAt, ExternalPaymentStatus externalPaymentStatus,
                                 Instant createdAt) {
        public static RefundResponse from(RefundRequest r) {
            return new RefundResponse(r.getId(), r.getRefundCode(), r.getDonationId(), r.getCampaignId(), r.getRequestedBy(), r.getAmount(),
                    r.getCurrency(), r.getReason(), r.getRefundStatus(), r.getReviewedBy(), r.getReviewedAt(), r.getDecisionReason(),
                    r.getRecordedBy(), r.getRecordedAt(), r.getExternalPaymentStatus(), r.getCreatedAt());
        }
    }
}
