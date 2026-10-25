package com.riseai.dto.response;

import com.riseai.entity.EscrowAdjustment;
import com.riseai.enums.AdjustmentStatus;
import com.riseai.enums.LedgerDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdjustmentResponse(UUID id, String adjustmentCode, UUID campaignId, String currency, LedgerDirection direction,
                                 BigDecimal amount, AdjustmentStatus adjustmentStatus, String reason, UUID requestedBy,
                                 UUID decidedBy, Instant decidedAt, String decisionReason, Instant createdAt) {
    public static AdjustmentResponse from(EscrowAdjustment a) {
        return new AdjustmentResponse(a.getId(), a.getAdjustmentCode(), a.getCampaignId(), a.getCurrency(), a.getDirection(),
                a.getAmount(), a.getAdjustmentStatus(), a.getReason(), a.getRequestedBy(), a.getDecidedBy(), a.getDecidedAt(),
                a.getDecisionReason(), a.getCreatedAt());
    }
}
