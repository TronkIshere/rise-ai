package com.riseai.escrow;

import com.riseai.common.api.PageResponse;
import com.riseai.common.api.ResponseAPI;
import com.riseai.escrow.EscrowDtos.EscrowSummary;
import com.riseai.escrow.EscrowDtos.LedgerEntryResponse;
import com.riseai.escrow.EscrowDtos.ReconciliationReport;
import com.riseai.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Escrow (simulated)")
public class EscrowController {
    private final EscrowQueryService escrowQueryService;

    @GetMapping("/campaigns/{campaignId}/escrow")
    public ResponseAPI<EscrowSummary> summary(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId) {
        return ResponseAPI.ok(escrowQueryService.summary(actor, campaignId));
    }

    @GetMapping("/campaigns/{campaignId}/escrow/ledger")
    public ResponseAPI<PageResponse<LedgerEntryResponse>> ledger(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId,
                                                                 @RequestParam(required = false) LedgerTransactionType type,
                                                                 @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(escrowQueryService.ledger(actor, campaignId, type, pageable)));
    }

    @GetMapping("/admin/escrow/campaigns/{campaignId}/reconciliation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<ReconciliationReport> reconcile(@PathVariable UUID campaignId) {
        return ResponseAPI.ok(escrowQueryService.reconcile(campaignId));
    }

    @GetMapping("/admin/escrow/reconciliation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<List<ReconciliationReport>> reconcileAll() {
        return ResponseAPI.ok(escrowQueryService.reconcileAll());
    }
}
