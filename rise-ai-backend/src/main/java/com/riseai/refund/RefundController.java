package com.riseai.refund;

import com.riseai.common.api.PageResponse;
import com.riseai.common.api.ReasonRequest;
import com.riseai.common.api.RequiredReasonRequest;
import com.riseai.common.api.ResponseAPI;
import com.riseai.refund.RefundDtos.CreateRefundRequest;
import com.riseai.refund.RefundDtos.RefundResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Refunds (simulated)")
public class RefundController {
    private final RefundService refundService;

    @PostMapping("/donations/{donationId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseAPI<RefundResponse> request(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID donationId, @Valid @RequestBody CreateRefundRequest request) {
        return ResponseAPI.created(refundService.request(actor, donationId, request));
    }

    @GetMapping("/refunds/me")
    public ResponseAPI<PageResponse<RefundResponse>> mine(@AuthenticationPrincipal AuthUser actor,
                                                          @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(refundService.mine(actor, pageable)));
    }

    @GetMapping("/refunds/{id}")
    public ResponseAPI<RefundResponse> get(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(refundService.get(actor, id));
    }

    @PostMapping("/refunds/{id}/cancel")
    public ResponseAPI<RefundResponse> cancel(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(refundService.cancel(actor, id));
    }

    @GetMapping("/admin/refunds")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<PageResponse<RefundResponse>> adminList(@RequestParam(required = false) RefundStatus status,
                                                               @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(refundService.adminList(status, pageable)));
    }

    @PostMapping("/admin/refunds/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<RefundResponse> approve(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest body) {
        return ResponseAPI.ok(refundService.approve(actor, id, ReasonRequest.of(body)));
    }

    @PostMapping("/admin/refunds/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<RefundResponse> reject(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(refundService.reject(actor, id, body.reason()));
    }

    @PostMapping("/admin/refunds/{id}/record")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<RefundResponse> record(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(refundService.record(actor, id));
    }
}
