package com.riseai.dto.response;

import com.riseai.entity.RefundRequest;
import com.riseai.enums.ExternalPaymentStatus;
import com.riseai.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(UUID id, String refundCode, UUID donationId, UUID campaignId, UUID requestedBy, BigDecimal amount,
                             String currency, String reason, RefundStatus refundStatus, UUID reviewedBy, Instant reviewedAt,
                             String decisionReason, UUID recordedBy, Instant recordedAt, ExternalPaymentStatus externalPaymentStatus,
                             Instant createdAt, boolean fundsReserved) {
    public static RefundResponse from(RefundRequest r) {
        return new RefundResponse(r.getId(), r.getRefundCode(), r.getDonationId(), r.getCampaignId(), r.getRequestedBy(), r.getAmount(),
                r.getCurrency(), r.getReason(), r.getRefundStatus(), r.getReviewedBy(), r.getReviewedAt(), r.getDecisionReason(),
                r.getRecordedBy(), r.getRecordedAt(), r.getExternalPaymentStatus(), r.getCreatedAt(), r.isFundsReserved());
    }
}
