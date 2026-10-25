package com.riseai.controller;

import com.riseai.dto.request.ProposeSettlementRequest;
import com.riseai.dto.request.ReasonRequest;
import com.riseai.dto.request.RequiredReasonRequest;
import com.riseai.dto.response.PageResponse;
import com.riseai.dto.response.ResponseAPI;
import com.riseai.dto.response.SettlementResponse;
import com.riseai.enums.SettlementStatus;
import com.riseai.security.AuthUser;
import com.riseai.service.SettlementService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Settlements (simulated)")
public class SettlementController {
    private final SettlementService settlementService;

    @PostMapping("/escrow/campaigns/{campaignId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseAPI<SettlementResponse> propose(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId,
                                                   @Valid @RequestBody ProposeSettlementRequest request) {
        return ResponseAPI.created(settlementService.propose(actor, campaignId, request.reason()));
    }

    @GetMapping("/escrow/campaigns/{campaignId}/settlements")
    public ResponseAPI<PageResponse<SettlementResponse>> byCampaign(@PathVariable UUID campaignId,
                                                                    @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(settlementService.byCampaign(campaignId, pageable)));
    }

    @GetMapping("/settlements")
    public ResponseAPI<PageResponse<SettlementResponse>> list(@RequestParam(required = false) SettlementStatus status,
                                                              @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(settlementService.list(status, pageable)));
    }

    @GetMapping("/settlements/{id}")
    public ResponseAPI<SettlementResponse> get(@PathVariable UUID id) {
        return ResponseAPI.ok(settlementService.get(id));
    }

    @PostMapping("/settlements/{id}/approve")
    public ResponseAPI<SettlementResponse> approve(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                                   @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(settlementService.approve(actor, id, ReasonRequest.of(body)));
    }

    @PostMapping("/settlements/{id}/reject")
    public ResponseAPI<SettlementResponse> reject(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                                  @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(settlementService.reject(actor, id, body.reason()));
    }

    @PostMapping("/settlements/{id}/cancel")
    public ResponseAPI<SettlementResponse> cancel(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                                  @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(settlementService.cancel(actor, id, ReasonRequest.of(body)));
    }
}
