package com.riseai.escrow;

import java.math.BigDecimal;
import java.util.UUID;

public interface EscrowEngine {
    EscrowAccount openAccount(UUID campaignId, String currency);

    void changeStatus(UUID campaignId, String currency, EscrowStatus status, UUID actorId, String reason);

    EscrowLedgerEntry recordContribution(UUID campaignId, String currency, UUID donationId, BigDecimal amount, UUID actorId);

    EscrowLedgerEntry reserve(UUID campaignId, String currency, UUID milestoneId, UUID disbursementId, BigDecimal amount, UUID actorId);

    EscrowLedgerEntry cancelReservation(UUID campaignId, String currency, UUID milestoneId, UUID disbursementId, BigDecimal amount, UUID actorId);

    EscrowLedgerEntry release(UUID campaignId, String currency, UUID milestoneId, UUID disbursementId, BigDecimal amount, UUID actorId);

    EscrowLedgerEntry recordRefund(UUID campaignId, String currency, UUID donationId, UUID refundRequestId, BigDecimal amount, UUID actorId);

    EscrowLedgerEntry reserveRefund(UUID campaignId, String currency, UUID donationId, UUID refundRequestId, BigDecimal amount, UUID actorId);

    EscrowLedgerEntry releaseRefundReservation(UUID campaignId, String currency, UUID donationId, UUID refundRequestId, BigDecimal amount, UUID actorId);

    EscrowLedgerEntry recordSettlementRefund(UUID campaignId, String currency, UUID settlementId, UUID donationId, BigDecimal amount, UUID actorId);

    EscrowLedgerEntry recordAdjustment(UUID campaignId, String currency, UUID adjustmentId, LedgerDirection direction, BigDecimal amount, UUID actorId);
}
