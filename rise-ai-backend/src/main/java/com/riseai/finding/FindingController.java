package com.riseai.finding;

import com.riseai.audit.AuditDtos.StatusHistoryResponse;
import com.riseai.common.api.PageResponse;
import com.riseai.common.api.ReasonRequest;
import com.riseai.common.api.ResponseAPI;
import com.riseai.finding.FindingDtos.FindingDetailResponse;
import com.riseai.finding.FindingDtos.FindingResponse;
import com.riseai.finding.FindingDtos.OutboxEventResponse;
import com.riseai.finding.FindingDtos.OutboxStatsResponse;
import com.riseai.finding.FindingDtos.ReviewFindingRequest;
import com.riseai.outbox.OutboxStatus;
import com.riseai.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/monitoring")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - AI monitoring findings (advisory)")
public class FindingController {
    private final FindingService findingService;

    @GetMapping("/findings")
    public ResponseAPI<PageResponse<FindingResponse>> findings(@RequestParam(required = false) FindingReviewStatus status,
                                                               @RequestParam(required = false) FindingSeverity severity,
                                                               @RequestParam(required = false) String category,
                                                               @RequestParam(required = false) UUID campaignId,
                                                               @PageableDefault(sort = "lastSeenAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(findingService.search(status, severity, category, campaignId, pageable)));
    }

    @GetMapping("/findings/{id}")
    public ResponseAPI<FindingDetailResponse> finding(@PathVariable UUID id) {
        return ResponseAPI.ok(findingService.get(id));
    }

    @GetMapping("/findings/{id}/history")
    public ResponseAPI<List<StatusHistoryResponse>> history(@PathVariable UUID id) {
        return ResponseAPI.ok(findingService.history(id));
    }

    @PostMapping("/findings/{id}/review")
    public ResponseAPI<FindingResponse> review(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                               @Valid @RequestBody ReviewFindingRequest body) {
        return ResponseAPI.ok(findingService.review(actor, id, body.status(), body.note()));
    }

    @GetMapping("/outbox/stats")
    public ResponseAPI<OutboxStatsResponse> outboxStats() {
        return ResponseAPI.ok(findingService.outboxStats());
    }

    @GetMapping("/outbox")
    public ResponseAPI<PageResponse<OutboxEventResponse>> outbox(@RequestParam(required = false) OutboxStatus status,
                                                                 @PageableDefault(sort = "occurredAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(findingService.outboxEvents(status, pageable)));
    }

    @PostMapping("/outbox/{id}/requeue")
    public ResponseAPI<OutboxEventResponse> requeue(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                                    @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(findingService.requeue(actor, id, ReasonRequest.of(body)));
    }
}
