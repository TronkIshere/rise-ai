package com.riseai.donation;

import com.riseai.common.api.PageResponse;
import com.riseai.common.api.RequiredReasonRequest;
import com.riseai.common.api.ResponseAPI;
import com.riseai.donation.DonationDtos.ConfirmDonationRequest;
import com.riseai.donation.DonationDtos.CreateDonationRequest;
import com.riseai.donation.DonationDtos.DonationResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Donations (simulated)")
public class DonationController {
    private final DonationService donationService;

    @PostMapping("/campaigns/{campaignId}/donations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DONOR')")
    public ResponseAPI<DonationResponse> create(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId,
                                                @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                                @Valid @RequestBody CreateDonationRequest request) {
        return ResponseAPI.created(donationService.create(actor, campaignId, request, idempotencyKey));
    }

    @GetMapping("/campaigns/{campaignId}/donations")
    public ResponseAPI<PageResponse<DonationResponse>> publicList(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId,
                                                                  @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(donationService.publicList(actor, campaignId, pageable)));
    }

    @GetMapping("/campaigns/{campaignId}/donations/manage")
    public ResponseAPI<PageResponse<DonationResponse>> manageList(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID campaignId,
                                                                  @RequestParam(required = false) DonationStatus status,
                                                                  @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(donationService.manageList(actor, campaignId, status, pageable)));
    }

    @GetMapping("/donations/me")
    public ResponseAPI<PageResponse<DonationResponse>> mine(@AuthenticationPrincipal AuthUser actor,
                                                            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(donationService.mine(actor, pageable)));
    }

    @GetMapping("/donations/{id}")
    public ResponseAPI<DonationResponse> get(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(donationService.get(actor, id));
    }

    @PostMapping("/donations/{id}/cancel")
    public ResponseAPI<DonationResponse> cancel(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(donationService.cancel(actor, id));
    }

    @GetMapping("/admin/donations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<PageResponse<DonationResponse>> adminList(@RequestParam(required = false) DonationStatus status,
                                                                 @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(donationService.adminList(status, pageable)));
    }

    @PostMapping("/admin/donations/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<DonationResponse> confirm(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id,
                                                 @Valid @RequestBody(required = false) ConfirmDonationRequest body) {
        return ResponseAPI.ok(donationService.confirm(actor, id, body == null ? null : body.simulatedReference()));
    }

    @PostMapping("/admin/donations/{id}/fail")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<DonationResponse> fail(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(donationService.fail(actor, id, body.reason()));
    }
}
