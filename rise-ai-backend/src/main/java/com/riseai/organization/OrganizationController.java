package com.riseai.organization;

import com.riseai.common.api.PageResponse;
import com.riseai.common.api.ResponseAPI;
import com.riseai.organization.OrganizationDtos.OrganizationRequest;
import com.riseai.organization.OrganizationDtos.OrganizationResponse;
import com.riseai.organization.OrganizationDtos.VerificationDecisionRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Organizations")
public class OrganizationController {
    private final OrganizationService organizationService;

    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CAMPAIGN_CREATOR','ADMIN')")
    public ResponseAPI<OrganizationResponse> create(@AuthenticationPrincipal AuthUser actor, @Valid @RequestBody OrganizationRequest request) {
        return ResponseAPI.created(organizationService.create(actor, request));
    }

    @GetMapping("/organizations")
    public ResponseAPI<PageResponse<OrganizationResponse>> search(@RequestParam(required = false) String q,
                                                                  @RequestParam(required = false) VerificationStatus status,
                                                                  @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(organizationService.search(q, status, pageable)));
    }

    @GetMapping("/organizations/mine")
    public ResponseAPI<PageResponse<OrganizationResponse>> mine(@AuthenticationPrincipal AuthUser actor,
                                                                @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(organizationService.mine(actor, pageable)));
    }

    @GetMapping("/organizations/{id}")
    public ResponseAPI<OrganizationResponse> get(@PathVariable UUID id) {
        return ResponseAPI.ok(organizationService.get(id));
    }

    @PutMapping("/organizations/{id}")
    public ResponseAPI<OrganizationResponse> update(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody OrganizationRequest request) {
        return ResponseAPI.ok(organizationService.update(actor, id, request));
    }

    @DeleteMapping("/organizations/{id}")
    public ResponseAPI<Void> delete(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        organizationService.delete(actor, id);
        return ResponseAPI.ok(null);
    }

    @PostMapping("/organizations/{id}/submit-verification")
    public ResponseAPI<OrganizationResponse> submitVerification(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id) {
        return ResponseAPI.ok(organizationService.submitForVerification(actor, id));
    }

    @PostMapping("/admin/organizations/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<OrganizationResponse> verify(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody VerificationDecisionRequest body) {
        return ResponseAPI.ok(organizationService.verify(actor, id, body.notes()));
    }

    @PostMapping("/admin/organizations/{id}/reject-verification")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<OrganizationResponse> reject(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody VerificationDecisionRequest body) {
        return ResponseAPI.ok(organizationService.rejectVerification(actor, id, body.notes()));
    }
}
