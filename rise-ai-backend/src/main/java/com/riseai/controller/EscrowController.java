package com.riseai.controller;

import com.riseai.dto.response.EscrowSummary;
import com.riseai.dto.response.LedgerEntryResponse;
import com.riseai.dto.response.PageResponse;
import com.riseai.dto.response.ReconciliationReport;
import com.riseai.dto.response.ReconciliationRunResponse;
import com.riseai.dto.response.ResponseAPI;
import com.riseai.enums.LedgerTransactionType;
import com.riseai.enums.ReconciliationRunStatus;
import com.riseai.enums.ReconciliationTrigger;
import com.riseai.security.AuthUser;
import com.riseai.service.EscrowQueryService;
import com.riseai.service.ReconciliationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Escrow (simulated)")
public class EscrowController {
    private final EscrowQueryService escrowQueryService;
    private final ReconciliationService reconciliationService;

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

    @PostMapping("/admin/escrow/reconciliation/runs")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseAPI<ReconciliationRunResponse> runReconciliation(@AuthenticationPrincipal AuthUser actor) {
        return ResponseAPI.created(reconciliationService.run(ReconciliationTrigger.MANUAL, actor.id()));
    }

    @GetMapping("/admin/escrow/reconciliation/runs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<PageResponse<ReconciliationRunResponse>> reconciliationRuns(@RequestParam(required = false) ReconciliationRunStatus status,
                                                                                @PageableDefault(sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(reconciliationService.history(status, pageable)));
    }

    @GetMapping("/admin/escrow/reconciliation/runs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<ReconciliationRunResponse> reconciliationRun(@PathVariable UUID id) {
        return ResponseAPI.ok(reconciliationService.get(id));
    }
}
