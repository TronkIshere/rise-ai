package com.riseai.dto.response;

import java.util.UUID;

public record ReconciliationReport(UUID campaignId, UUID escrowAccountId, String currency, Balances stored, Balances computedFromLedger,
                                   long ledgerEntryCount, boolean consistent) {
}
