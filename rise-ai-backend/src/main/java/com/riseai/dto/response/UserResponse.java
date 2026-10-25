package com.riseai.dto.response;

import com.riseai.entity.User;
import com.riseai.enums.Role;
import com.riseai.enums.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, String phone, String avatarUrl, UserStatus status,
                           boolean emailVerified, Set<Role> roles, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getAvatarUrl(), u.getStatus(),
                u.isEmailVerified(), Set.copyOf(u.getRoles()), u.getCreatedAt());
    }
}
