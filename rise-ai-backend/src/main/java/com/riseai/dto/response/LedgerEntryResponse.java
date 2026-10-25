package com.riseai.dto.response;

import com.riseai.entity.EscrowLedgerEntry;
import com.riseai.enums.LedgerDirection;
import com.riseai.enums.LedgerEntryStatus;
import com.riseai.enums.LedgerTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
