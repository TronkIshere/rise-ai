package com.riseai.disbursement;

import com.riseai.audit.AuditDtos.StatusHistoryResponse;
import com.riseai.common.api.PageResponse;
import com.riseai.common.api.ReasonRequest;
import com.riseai.common.api.RequiredReasonRequest;
import com.riseai.common.api.ResponseAPI;
import com.riseai.disbursement.DisbursementDtos.CreateDisbursementRequest;
import com.riseai.disbursement.DisbursementDtos.DisbursementResponse;
import com.riseai.evidence.EvidenceDtos.EvidenceRequest;
import com.riseai.evidence.EvidenceDtos.EvidenceResponse;
import com.riseai.evidence.EvidenceService;
import com.riseai.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Disbursements")
public class DisbursementController {
    private final DisbursementService disbursementService;
    private final EvidenceService evidenceService;

    @PostMapping("/milestones/{milestoneId}/disbursements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<DisbursementResponse> create(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID milestoneId, @Valid @RequestBody CreateDisbursementRequest request) {
        return ResponseAPI.created(disbursementService.create(actor, milestoneId, request));
    }

    @GetMapping("/campaigns/{campaignId}/disbursements")
    public ResponseAPI<PageResponse<DisbursementResponse>> listByCampaign(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId,
                                                                          @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(disbursementService.listByCampaign(actor, campaignId, pageable)));
    }

    @GetMapping("/disbursements/{id}")
    public ResponseAPI<DisbursementResponse> get(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(disbursementService.get(actor, id));
    }

    @GetMapping("/disbursements/{id}/history")
    public ResponseAPI<List<StatusHistoryResponse>> history(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(disbursementService.history(actor, id));
    }

    @GetMapping("/disbursements/{id}/evidence")
    public ResponseAPI<PageResponse<EvidenceResponse>> evidence(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                                                @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(evidenceService.listByRequest(actor, id, pageable)));
    }

    @PostMapping("/disbursements/{id}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<EvidenceResponse> addEvidence(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody EvidenceRequest request) {
        return ResponseAPI.created(disbursementService.addEvidence(actor, id, request));
    }

    @PostMapping("/disbursements/{id}/resubmit")
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<DisbursementResponse> resubmit(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(disbursementService.resubmit(actor, id, ReasonRequest.of(body)));
    }

    @PostMapping("/disbursements/{id}/cancel")
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<DisbursementResponse> cancel(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(disbursementService.cancel(actor, id, body.reason()));
    }

    @PostMapping("/disbursements/{id}/start-review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<DisbursementResponse> startReview(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(disbursementService.startReview(actor, id));
    }

    @PostMapping("/disbursements/{id}/request-evidence")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<DisbursementResponse> requestEvidence(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(disbursementService.requestMoreEvidence(actor, id, body.reason()));
    }

    @PostMapping("/disbursements/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<DisbursementResponse> approve(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(disbursementService.approve(actor, id, ReasonRequest.of(body)));
    }

    @PostMapping("/disbursements/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<DisbursementResponse> reject(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(disbursementService.reject(actor, id, body.reason()));
    }

    @PostMapping("/disbursements/{id}/release")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<DisbursementResponse> release(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(disbursementService.release(actor, id));
    }

    @GetMapping("/admin/disbursements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<PageResponse<DisbursementResponse>> adminQueue(@RequestParam(required = false) DisbursementStatus status,
                                                                      @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(disbursementService.adminQueue(status, pageable)));
    }
}
