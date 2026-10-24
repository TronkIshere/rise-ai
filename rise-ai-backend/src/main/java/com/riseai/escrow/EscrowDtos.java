package com.riseai.escrow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EscrowDtos {
    public static final String SIMULATION_DISCLAIMER = "Phase 0 simulation: balances are internal MySQL records, not funds locked in a smart contract. No real money is moved.";

    private EscrowDtos() {
    }

    public record EscrowSummary(UUID campaignId, UUID escrowAccountId, String currency, EscrowStatus escrowStatus,
                                BigDecimal totalConfirmedContributions, BigDecimal totalReserved, BigDecimal totalDisbursed,
                                BigDecimal totalRefunded, BigDecimal availableBalance, BigDecimal heldBalance,
                                BigDecimal plannedMilestoneBudget, boolean simulated, String disclaimer,
                                BigDecimal totalRefundReserved, BigDecimal totalAdjustmentCredits, BigDecimal totalAdjustmentDebits) {
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

    public record Balances(BigDecimal confirmed, BigDecimal reserved, BigDecimal disbursed, BigDecimal refunded, BigDecimal available,
                           BigDecimal refundReserved, BigDecimal adjustmentCredits, BigDecimal adjustmentDebits) {
        public static Balances from(EscrowAccount a) {
            return new Balances(a.getTotalConfirmedContributions(), a.getTotalReserved(), a.getTotalDisbursed(), a.getTotalRefunded(),
                    a.getAvailableBalance(), a.getTotalRefundReserved(), a.getTotalAdjustmentCredits(), a.getTotalAdjustmentDebits());
        }

        public static Balances fromLedger(Map<LedgerTransactionType, BigDecimal> sums) {
            BigDecimal confirmed = sum(sums, LedgerTransactionType.CONTRIBUTION_CONFIRMED);
            BigDecimal disbursed = sum(sums, LedgerTransactionType.FUNDS_RELEASED);
            BigDecimal reserved = sum(sums, LedgerTransactionType.FUNDS_RESERVED)
                    .subtract(sum(sums, LedgerTransactionType.RESERVATION_CANCELLED)).subtract(disbursed);
            BigDecimal refunded = sum(sums, LedgerTransactionType.REFUND_RECORDED).add(sum(sums, LedgerTransactionType.SETTLEMENT_REFUND_RECORDED));
            BigDecimal refundReserved = sum(sums, LedgerTransactionType.REFUND_RESERVED).subtract(sum(sums, LedgerTransactionType.REFUND_RESERVATION_RELEASED));
            BigDecimal credits = sum(sums, LedgerTransactionType.ADJUSTMENT_CREDIT);
            BigDecimal debits = sum(sums, LedgerTransactionType.ADJUSTMENT_DEBIT);
            BigDecimal available = confirmed.add(credits).subtract(debits).subtract(reserved).subtract(refundReserved)
                    .subtract(disbursed).subtract(refunded);
            return new Balances(confirmed, reserved, disbursed, refunded, available, refundReserved, credits, debits);
        }

        public boolean matches(Balances other) {
            return equal(confirmed, other.confirmed) && equal(reserved, other.reserved) && equal(disbursed, other.disbursed)
                    && equal(refunded, other.refunded) && equal(available, other.available) && equal(refundReserved, other.refundReserved)
                    && equal(adjustmentCredits, other.adjustmentCredits) && equal(adjustmentDebits, other.adjustmentDebits);
        }

        private static BigDecimal sum(Map<LedgerTransactionType, BigDecimal> sums, LedgerTransactionType type) {
            return sums.getOrDefault(type, BigDecimal.ZERO);
        }

        private static boolean equal(BigDecimal a, BigDecimal b) {
            return a.compareTo(b) == 0;
        }
    }

    public record ReconciliationRunResponse(UUID id, String runCode, ReconciliationTrigger triggerType, UUID triggeredBy,
                                            ReconciliationRunStatus runStatus, Instant startedAt, Instant finishedAt, int accountsChecked,
                                            int inconsistentAccounts, List<ReconciliationReport> findings, String errorMessage) {
        public static ReconciliationRunResponse from(ReconciliationRun r) {
            return new ReconciliationRunResponse(r.getId(), r.getRunCode(), r.getTriggerType(), r.getTriggeredBy(), r.getRunStatus(),
                    r.getStartedAt(), r.getFinishedAt(), r.getAccountsChecked(), r.getInconsistentAccounts(),
                    r.getFindings() == null ? List.of() : r.getFindings(), r.getErrorMessage());
        }
    }

    public record ReconciliationReport(UUID campaignId, UUID escrowAccountId, String currency, Balances stored, Balances computedFromLedger,
                                       long ledgerEntryCount, boolean consistent) {
    }
}
