package com.riseai.service.impl;

import com.riseai.dto.request.UpdateProfileRequest;
import com.riseai.dto.response.UserResponse;
import com.riseai.entity.User;
import com.riseai.enums.Role;
import com.riseai.enums.UserStatus;
import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;
import com.riseai.repository.RefreshTokenRepository;
import com.riseai.repository.UserRepository;
import com.riseai.security.AuthUser;
import com.riseai.service.AuditService;
import com.riseai.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @Override
    public UserResponse me(AuthUser actor) {
        return UserResponse.from(load(AuthUser.require(actor).id()));
    }

    @Transactional
    @Override
    public UserResponse updateProfile(AuthUser actor, UpdateProfileRequest request) {
        User user = load(AuthUser.require(actor).id());
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setAvatarUrl(request.avatarUrl());
        auditService.record(actor.id(), "USER_PROFILE_UPDATED", "USER", user.getId(), null, null, null);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<UserResponse> search(String q, UserStatus status, Pageable pageable) {
        String keyword = q == null || q.isBlank() ? null : q.trim();
        return userRepository.search(keyword, status, pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    @Override
    public UserResponse get(UUID id) {
        return UserResponse.from(load(id));
    }

    @Transactional
    @Override
    public UserResponse changeStatus(AuthUser actor, UUID id, UserStatus target, String reason) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        if (actor.is(id)) {
            throw new ApplicationException(ErrorCode.SELF_ROLE_CHANGE_NOT_ALLOWED);
        }
        User user = load(id);
        if (user.getStatus() == target) {
            throw new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "User is already " + target);
        }
        UserStatus previous = user.getStatus();
        user.setStatus(target);
        if (target == UserStatus.SUSPENDED) {
            refreshTokenRepository.revokeAllForUser(id, Instant.now());
        }
        auditService.transition(actor.id(), target == UserStatus.SUSPENDED ? "USER_SUSPENDED" : "USER_REACTIVATED", "USER", id, previous, target, reason);
        return UserResponse.from(user);
    }

    @Transactional
    @Override
    public UserResponse updateRoles(AuthUser actor, UUID id, Set<Role> roles) {
        AuthUser.require(actor).requireRole(Role.ADMIN);
        if (actor.is(id)) {
            throw new ApplicationException(ErrorCode.SELF_ROLE_CHANGE_NOT_ALLOWED);
        }
        User user = load(id);
        String previous = user.getRoles().toString();
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        auditService.record(actor.id(), "USER_ROLES_CHANGED", "USER", id, null, null,
                AuditService.meta("previousRoles", previous, "newRoles", roles.toString()));
        return UserResponse.from(user);
    }

    @Override
    public User load(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> ApplicationException.notFound("User"));
    }
}
