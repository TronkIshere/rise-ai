package com.riseai.adjustment;

import com.riseai.escrow.LedgerDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AdjustmentDtos {
    private AdjustmentDtos() {
    }

    public record CreateAdjustmentRequest(
            @NotNull LedgerDirection direction,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 2000) String reason) {
    }

    public record AdjustmentResponse(UUID id, String adjustmentCode, UUID campaignId, String currency, LedgerDirection direction,
                                     BigDecimal amount, AdjustmentStatus adjustmentStatus, String reason, UUID requestedBy,
                                     UUID decidedBy, Instant decidedAt, String decisionReason, Instant createdAt) {
        public static AdjustmentResponse from(EscrowAdjustment a) {
            return new AdjustmentResponse(a.getId(), a.getAdjustmentCode(), a.getCampaignId(), a.getCurrency(), a.getDirection(),
                    a.getAmount(), a.getAdjustmentStatus(), a.getReason(), a.getRequestedBy(), a.getDecidedBy(), a.getDecidedAt(),
                    a.getDecisionReason(), a.getCreatedAt());
        }
    }
}
