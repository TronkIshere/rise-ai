package com.riseai.controller;

import com.riseai.dto.request.MilestoneRequest;
import com.riseai.dto.request.ReasonRequest;
import com.riseai.dto.request.RequiredReasonRequest;
import com.riseai.dto.response.MilestoneResponse;
import com.riseai.dto.response.ResponseAPI;
import com.riseai.dto.response.StatusHistoryResponse;
import com.riseai.security.AuthUser;
import com.riseai.service.MilestoneService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Milestones")
public class MilestoneController {
    private final MilestoneService milestoneService;

    @PostMapping("/campaigns/{campaignId}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<MilestoneResponse> create(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId, @Valid @RequestBody MilestoneRequest request) {
        return ResponseAPI.created(milestoneService.create(actor, campaignId, request));
    }

    @GetMapping("/campaigns/{campaignId}/milestones")
    public ResponseAPI<List<MilestoneResponse>> list(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId) {
        return ResponseAPI.ok(milestoneService.list(actor, campaignId));
    }

    @GetMapping("/milestones/{id}")
    public ResponseAPI<MilestoneResponse> get(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(milestoneService.get(actor, id));
    }

    @PutMapping("/milestones/{id}")
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<MilestoneResponse> update(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody MilestoneRequest request) {
        return ResponseAPI.ok(milestoneService.update(actor, id, request));
    }

    @DeleteMapping("/milestones/{id}")
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<Void> delete(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        milestoneService.delete(actor, id);
        return ResponseAPI.ok(null);
    }

    @GetMapping("/milestones/{id}/history")
    public ResponseAPI<List<StatusHistoryResponse>> history(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(milestoneService.history(actor, id));
    }

    @PostMapping("/milestones/{id}/start")
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<MilestoneResponse> start(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(milestoneService.start(actor, id));
    }

    @PostMapping("/milestones/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<MilestoneResponse> complete(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(milestoneService.complete(actor, id, ReasonRequest.of(body)));
    }

    @PostMapping("/milestones/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<MilestoneResponse> cancel(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(milestoneService.cancel(actor, id, body.reason()));
    }
}
