package com.riseai.escrow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class EscrowDtos {
    public static final String SIMULATION_DISCLAIMER = "Phase 0 simulation: balances are internal MySQL records, not funds locked in a smart contract. No real money is moved.";

    private EscrowDtos() {
    }

    public record EscrowSummary(UUID campaignId, UUID escrowAccountId, String currency, EscrowStatus escrowStatus,
                                BigDecimal totalConfirmedContributions, BigDecimal totalReserved, BigDecimal totalDisbursed,
                                BigDecimal totalRefunded, BigDecimal availableBalance, BigDecimal heldBalance,
                                BigDecimal plannedMilestoneBudget, boolean simulated, String disclaimer) {
    }

    public record LedgerEntryResponse(UUID id, String transactionCode, UUID campaignId, UUID escrowAccountId, UUID donationId,
                                      UUID milestoneId, UUID disbursementId, LedgerTransactionType transactionType,
                                      LedgerDirection direction, BigDecimal amount, String currency, LedgerEntryStatus status,
                                      String referenceType, UUID referenceId, String description, UUID createdBy, Instant createdAt) {
        public static LedgerEntryResponse from(EscrowLedgerEntry e) {
            return new LedgerEntryResponse(e.getId(), e.getTransactionCode(), e.getCampaignId(), e.getEscrowAccountId(), e.getDonationId(),
                    e.getMilestoneId(), e.getDisbursementId(), e.getTransactionType(), e.getDirection(), e.getAmount(), e.getCurrency(),
                    e.getStatus(), e.getReferenceType(), e.getReferenceId(), e.getDescription(), e.getCreatedBy(), e.getCreatedAt());
        }
    }

    public record Balances(BigDecimal confirmed, BigDecimal reserved, BigDecimal disbursed, BigDecimal refunded, BigDecimal available) {
    }

    public record ReconciliationReport(UUID campaignId, UUID escrowAccountId, String currency, Balances stored, Balances computedFromLedger,
                                       long ledgerEntryCount, boolean consistent) {
    }
}
