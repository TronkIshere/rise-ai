package com.riseai.dto.response;

import com.riseai.entity.SettlementRequest;
import com.riseai.enums.SettlementStatus;
import com.riseai.enums.SettlementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SettlementResponse(UUID id, String settlementCode, UUID campaignId, String currency, SettlementType settlementType,
                                 BigDecimal amount, SettlementStatus settlementStatus, String reason, UUID proposedBy, UUID decidedBy,
                                 Instant decidedAt, String decisionReason, Instant executedAt, Integer donationCount, Instant createdAt,
                                 String disclaimer) {
    public static final String SETTLEMENT_DISCLAIMER = "Simulated settlement: ledger refunds only, no external payment is executed.";

    public static SettlementResponse from(SettlementRequest s) {
        return new SettlementResponse(s.getId(), s.getSettlementCode(), s.getCampaignId(), s.getCurrency(), s.getSettlementType(),
                s.getAmount(), s.getSettlementStatus(), s.getReason(), s.getProposedBy(), s.getDecidedBy(), s.getDecidedAt(),
                s.getDecisionReason(), s.getExecutedAt(), s.getDonationCount(), s.getCreatedAt(), SETTLEMENT_DISCLAIMER);
    }
}
