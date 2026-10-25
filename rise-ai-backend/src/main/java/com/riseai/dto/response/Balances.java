package com.riseai.dto.response;

import com.riseai.entity.EscrowAccount;
import com.riseai.enums.LedgerTransactionType;

import java.math.BigDecimal;
import java.util.Map;

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
