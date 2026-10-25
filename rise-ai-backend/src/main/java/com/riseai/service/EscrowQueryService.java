package com.riseai.service;

import com.riseai.dto.response.EscrowSummary;
import com.riseai.dto.response.LedgerEntryResponse;
import com.riseai.dto.response.ReconciliationReport;
import com.riseai.enums.LedgerTransactionType;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface EscrowQueryService {
    EscrowSummary summary(AuthUser actor, UUID campaignId);

    Page<LedgerEntryResponse> ledger(AuthUser actor, UUID campaignId, LedgerTransactionType type, Pageable pageable);

    ReconciliationReport reconcile(UUID campaignId);

    List<ReconciliationReport> reconcileAll();
}
