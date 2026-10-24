package com.riseai.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {
    }

    public record UserResponse(UUID id, String email, String fullName, String phone, String avatarUrl, UserStatus status,
                               boolean emailVerified, Set<Role> roles, Instant createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getAvatarUrl(), u.getStatus(),
                    u.isEmailVerified(), Set.copyOf(u.getRoles()), u.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 30) @Pattern(regexp = "^[0-9+ ()-]*$") String phone,
            @Size(max = 500) @Pattern(regexp = "^(https://\\S+)?$") String avatarUrl) {
    }

    public record UpdateRolesRequest(@NotEmpty Set<Role> roles) {
    }
}
