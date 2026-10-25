package com.riseai.dto.response;

import com.riseai.enums.EscrowStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record EscrowSummary(UUID campaignId, UUID escrowAccountId, String currency, EscrowStatus escrowStatus,
                            BigDecimal totalConfirmedContributions, BigDecimal totalReserved, BigDecimal totalDisbursed,
                            BigDecimal totalRefunded, BigDecimal availableBalance, BigDecimal heldBalance,
                            BigDecimal plannedMilestoneBudget, boolean simulated, String disclaimer,
                            BigDecimal totalRefundReserved, BigDecimal totalAdjustmentCredits, BigDecimal totalAdjustmentDebits) {
    public static final String SIMULATION_DISCLAIMER = "Phase 0 simulation: balances are internal MySQL records, not funds locked in a smart contract. No real money is moved.";
}
