package com.riseai.controller;

import com.riseai.dto.request.CreateAdjustmentRequest;
import com.riseai.dto.request.ReasonRequest;
import com.riseai.dto.request.RequiredReasonRequest;
import com.riseai.dto.response.AdjustmentResponse;
import com.riseai.dto.response.PageResponse;
import com.riseai.dto.response.ResponseAPI;
import com.riseai.enums.AdjustmentStatus;
import com.riseai.security.AuthUser;
import com.riseai.service.AdjustmentService;
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
@Tag(name = "Admin - Escrow adjustments (simulated)")
public class AdjustmentController {
    private final AdjustmentService adjustmentService;

    @PostMapping("/escrow/campaigns/{campaignId}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseAPI<AdjustmentResponse> request(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId,
                                                   @Valid @RequestBody CreateAdjustmentRequest request) {
        return ResponseAPI.created(adjustmentService.request(actor, campaignId, request));
    }

    @GetMapping("/escrow/campaigns/{campaignId}/adjustments")
    public ResponseAPI<PageResponse<AdjustmentResponse>> byCampaign(@PathVariable UUID campaignId,
                                                                    @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(adjustmentService.byCampaign(campaignId, pageable)));
    }

    @GetMapping("/adjustments")
    public ResponseAPI<PageResponse<AdjustmentResponse>> list(@RequestParam(required = false) AdjustmentStatus status,
                                                              @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(adjustmentService.list(status, pageable)));
    }

    @GetMapping("/adjustments/{id}")
    public ResponseAPI<AdjustmentResponse> get(@PathVariable UUID id) {
        return ResponseAPI.ok(adjustmentService.get(id));
    }

    @PostMapping("/adjustments/{id}/approve")
    public ResponseAPI<AdjustmentResponse> approve(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                                   @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(adjustmentService.approve(actor, id, ReasonRequest.of(body)));
    }

    @PostMapping("/adjustments/{id}/reject")
    public ResponseAPI<AdjustmentResponse> reject(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                                  @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(adjustmentService.reject(actor, id, body.reason()));
    }

    @PostMapping("/adjustments/{id}/cancel")
    public ResponseAPI<AdjustmentResponse> cancel(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                                  @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(adjustmentService.cancel(actor, id, ReasonRequest.of(body)));
    }
}
