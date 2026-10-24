package com.riseai.audit;

import com.riseai.common.api.PageResponse;
import com.riseai.common.api.ResponseAPI;
import com.riseai.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Audit")
public class AuditController {
    private final AuditService auditService;

    @GetMapping("/admin/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<PageResponse<AuditDtos.AuditLogResponse>> search(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String action,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(auditService.search(actorId, entityType, entityId, action, pageable)));
    }

    @GetMapping("/audit-logs/me")
    public ResponseAPI<PageResponse<AuditDtos.AuditLogResponse>> mine(@AuthenticationPrincipal AuthUser actor,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(auditService.byActor(AuthUser.require(actor).id(), pageable)));
    }
}
