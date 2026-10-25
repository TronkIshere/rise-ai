package com.riseai.controller;

import com.riseai.dto.request.EvidenceRequest;
import com.riseai.dto.request.ReasonRequest;
import com.riseai.dto.request.RequiredReasonRequest;
import com.riseai.dto.response.EvidenceResponse;
import com.riseai.dto.response.PageResponse;
import com.riseai.dto.response.ResponseAPI;
import com.riseai.security.AuthUser;
import com.riseai.service.EvidenceService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Evidence")
public class EvidenceController {
    private final EvidenceService evidenceService;

    @PostMapping("/milestones/{milestoneId}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CAMPAIGN_CREATOR')")
    public ResponseAPI<EvidenceResponse> submit(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID milestoneId, @Valid @RequestBody EvidenceRequest request) {
        return ResponseAPI.created(evidenceService.submit(actor, milestoneId, null, request));
    }

    @GetMapping("/milestones/{milestoneId}/evidence")
    public ResponseAPI<PageResponse<EvidenceResponse>> listByMilestone(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID milestoneId,
                                                                       @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(evidenceService.listByMilestone(actor, milestoneId, pageable)));
    }

    @GetMapping("/evidence/{id}")
    public ResponseAPI<EvidenceResponse> get(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(evidenceService.get(actor, id));
    }

    @PostMapping("/evidence/{id}/accept")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<EvidenceResponse> accept(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(evidenceService.review(actor, id, true, ReasonRequest.of(body)));
    }

    @PostMapping("/evidence/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<EvidenceResponse> reject(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(evidenceService.review(actor, id, false, body.reason()));
    }
}
