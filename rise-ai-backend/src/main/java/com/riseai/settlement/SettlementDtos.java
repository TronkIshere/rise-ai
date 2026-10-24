package com.riseai.settlement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SettlementDtos {
    public static final String SETTLEMENT_DISCLAIMER = "Simulated settlement: ledger refunds only, no external payment is executed.";

    private SettlementDtos() {
    }

    public record ProposeSettlementRequest(@NotBlank @Size(max = 2000) String reason) {
    }

    public record SettlementResponse(UUID id, String settlementCode, UUID campaignId, String currency, SettlementType settlementType,
                                     BigDecimal amount, SettlementStatus settlementStatus, String reason, UUID proposedBy, UUID decidedBy,
                                     Instant decidedAt, String decisionReason, Instant executedAt, Integer donationCount, Instant createdAt,
                                     String disclaimer) {
        public static SettlementResponse from(SettlementRequest s) {
            return new SettlementResponse(s.getId(), s.getSettlementCode(), s.getCampaignId(), s.getCurrency(), s.getSettlementType(),
                    s.getAmount(), s.getSettlementStatus(), s.getReason(), s.getProposedBy(), s.getDecidedBy(), s.getDecidedAt(),
                    s.getDecisionReason(), s.getExecutedAt(), s.getDonationCount(), s.getCreatedAt(), SETTLEMENT_DISCLAIMER);
        }
    }
}
