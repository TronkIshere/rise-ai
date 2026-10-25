package com.riseai.controller;

import com.riseai.dto.request.AssignDisputeRequest;
import com.riseai.dto.request.DisputeMessageRequest;
import com.riseai.dto.request.OpenDisputeRequest;
import com.riseai.dto.request.ReasonRequest;
import com.riseai.dto.request.ResolveDisputeRequest;
import com.riseai.dto.response.DisputeMessageResponse;
import com.riseai.dto.response.DisputeResponse;
import com.riseai.dto.response.PageResponse;
import com.riseai.dto.response.ResponseAPI;
import com.riseai.dto.response.StatusHistoryResponse;
import com.riseai.enums.DisputeStatus;
import com.riseai.security.AuthUser;
import com.riseai.service.DisputeService;
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
@Tag(name = "Disputes")
public class DisputeController {
    private final DisputeService disputeService;

    @PostMapping("/campaigns/{campaignId}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseAPI<DisputeResponse> open(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId, @Valid @RequestBody OpenDisputeRequest request) {
        return ResponseAPI.created(disputeService.open(actor, campaignId, request));
    }

    @GetMapping("/campaigns/{campaignId}/disputes")
    public ResponseAPI<PageResponse<DisputeResponse>> byCampaign(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId,
                                                                 @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(disputeService.byCampaign(actor, campaignId, pageable)));
    }

    @GetMapping("/disputes/me")
    public ResponseAPI<PageResponse<DisputeResponse>> mine(@AuthenticationPrincipal AuthUser actor,
                                                           @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(disputeService.mine(actor, pageable)));
    }

    @GetMapping("/disputes/{id}")
    public ResponseAPI<DisputeResponse> get(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(disputeService.get(actor, id));
    }

    @GetMapping("/disputes/{id}/history")
    public ResponseAPI<List<StatusHistoryResponse>> history(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(disputeService.history(actor, id));
    }

    @GetMapping("/disputes/{id}/messages")
    public ResponseAPI<PageResponse<DisputeMessageResponse>> messages(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                                                      @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(disputeService.messages(actor, id, pageable)));
    }

    @PostMapping("/disputes/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseAPI<DisputeMessageResponse> addMessage(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody DisputeMessageRequest request) {
        return ResponseAPI.created(disputeService.addMessage(actor, id, request.message()));
    }

    @PostMapping("/disputes/{id}/withdraw")
    public ResponseAPI<DisputeResponse> withdraw(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(disputeService.withdraw(actor, id, ReasonRequest.of(body)));
    }

    @GetMapping("/admin/disputes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<PageResponse<DisputeResponse>> adminQueue(@RequestParam(required = false) DisputeStatus status,
                                                                 @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(disputeService.adminQueue(status, pageable)));
    }

    @PostMapping("/admin/disputes/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<DisputeResponse> assign(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody AssignDisputeRequest request) {
        return ResponseAPI.ok(disputeService.assign(actor, id, request.reviewerId()));
    }

    @PostMapping("/admin/disputes/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<DisputeResponse> resolve(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody ResolveDisputeRequest request) {
        return ResponseAPI.ok(disputeService.resolve(actor, id, request));
    }
}
