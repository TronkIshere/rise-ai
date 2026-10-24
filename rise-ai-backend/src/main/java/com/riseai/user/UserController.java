package com.riseai.user;

import com.riseai.common.api.PageResponse;
import com.riseai.common.api.RequiredReasonRequest;
import com.riseai.common.api.ResponseAPI;
import com.riseai.security.AuthUser;
import com.riseai.user.UserDtos.UpdateProfileRequest;
import com.riseai.user.UserDtos.UpdateRolesRequest;
import com.riseai.user.UserDtos.UserResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {
    private final UserService userService;

    @GetMapping("/users/me")
    public ResponseAPI<UserResponse> me(@AuthenticationPrincipal AuthUser actor) {
        return ResponseAPI.ok(userService.me(actor));
    }

    @PutMapping("/users/me")
    public ResponseAPI<UserResponse> updateMe(@AuthenticationPrincipal AuthUser actor, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseAPI.ok(userService.updateProfile(actor, request));
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<PageResponse<UserResponse>> search(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) UserStatus status,
                                                         @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseAPI.ok(PageResponse.of(userService.search(q, status, pageable)));
    }

    @GetMapping("/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<UserResponse> get(@PathVariable UUID id) {
        return ResponseAPI.ok(userService.get(id));
    }

    @PostMapping("/admin/users/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<UserResponse> suspend(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(userService.changeStatus(actor, id, UserStatus.SUSPENDED, body.reason()));
    }

    @PostMapping("/admin/users/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<UserResponse> reactivate(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody RequiredReasonRequest body) {
        return ResponseAPI.ok(userService.changeStatus(actor, id, UserStatus.ACTIVE, body.reason()));
    }

    @PutMapping("/admin/users/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseAPI<UserResponse> updateRoles(@AuthenticationPrincipal AuthUser actor, @PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest body) {
        return ResponseAPI.ok(userService.updateRoles(actor, id, body.roles()));
    }
}
