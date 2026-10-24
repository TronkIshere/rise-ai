package com.riseai.campaign;

import com.riseai.audit.AuditDtos.StatusHistoryResponse;
import com.riseai.campaign.CampaignDtos.CampaignRequest;
import com.riseai.campaign.CampaignDtos.CampaignResponse;
import com.riseai.campaign.CampaignDtos.CampaignStatistics;
import com.riseai.common.api.PageResponse;
import com.riseai.common.api.ReasonRequest;
import com.riseai.common.api.RequiredReasonRequest;
import com.riseai.common.api.ResponseAPI;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaigns")
public class CampaignController {
    private final CampaignService campaignService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<CampaignResponse> create(@AuthenticationPrincipal AuthUser actor, @Valid @RequestBody CampaignRequest request) {
        return ResponseAPI.created(campaignService.create(actor, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<CampaignResponse> update(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody CampaignRequest request) {
        return ResponseAPI.ok(campaignService.update(actor, id, request));
    }

    @GetMapping
    public ResponseAPI<PageResponse<CampaignResponse>> search(@AuthenticationPrincipal AuthUser actor,
                                                              @RequestParam(required = false) CampaignStatus status,
                                                              @RequestParam(required = false) CampaignCategory category,
                                                              @RequestParam(required = false) UUID organizationId,
                                                              @RequestParam(required = false) String q,
                                                              @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(campaignService.search(actor, status, category, organizationId, q, pageable)));
    }

    @GetMapping("/mine")
    public ResponseAPI<PageResponse<CampaignResponse>> mine(@AuthenticationPrincipal AuthUser actor,
                                                            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(campaignService.mine(actor, pageable)));
    }

    @GetMapping("/slug/{slug}")
    public ResponseAPI<CampaignResponse> getBySlug(@AuthenticationPrincipal AuthUser actor, @PathVariable String slug) {
        return ResponseAPI.ok(campaignService.getBySlug(actor, slug));
    }

    @GetMapping("/{id}")
    public ResponseAPI<CampaignResponse> get(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(campaignService.get(actor, id));
    }

    @GetMapping("/{id}/statistics")
    public ResponseAPI<CampaignStatistics> statistics(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(campaignService.statistics(actor, id));
    }

    @GetMapping("/{id}/history")
    public ResponseAPI<List<StatusHistoryResponse>> history(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(campaignService.history(actor, id));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<CampaignResponse> submit(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(campaignService.submit(actor, id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<CampaignResponse> approve(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(campaignService.approve(actor, id, ReasonRequest.of(body)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<CampaignResponse> reject(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(campaignService.reject(actor, id, body.reason()));
    }

    @PostMapping("/{id}/publish")
    public ResponseAPI<CampaignResponse> publish(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(campaignService.publish(actor, id));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<CampaignResponse> pause(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(campaignService.pause(actor, id, body.reason()));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<CampaignResponse> resume(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(campaignService.resume(actor, id, ReasonRequest.of(body)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<CampaignResponse> complete(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(campaignService.complete(actor, id, ReasonRequest.of(body)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseAPI<CampaignResponse> cancel(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(campaignService.cancel(actor, id, body.reason()));
    }
}
