package com.riseai.service;

import com.riseai.dto.request.UpdateProfileRequest;
import com.riseai.dto.response.UserResponse;
import com.riseai.entity.User;
import com.riseai.enums.Role;
import com.riseai.enums.UserStatus;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;
import java.util.UUID;

public interface UserService {
    UserResponse me(AuthUser actor);

    UserResponse updateProfile(AuthUser actor, UpdateProfileRequest request);

    Page<UserResponse> search(String q, UserStatus status, Pageable pageable);

    UserResponse get(UUID id);

    UserResponse changeStatus(AuthUser actor, UUID id, UserStatus target, String reason);

    UserResponse updateRoles(AuthUser actor, UUID id, Set<Role> roles);

    User load(UUID id);
}
