package com.riseai.security;

import com.riseai.user.UserDtos.UserResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {
    }

    public enum RegistrableRole {
        DONOR,
        CAMPAIGN_CREATOR
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain letters and digits") String password,
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 30) @Pattern(regexp = "^[0-9+ ()-]*$") String phone,
            @NotNull RegistrableRole accountType) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
                                Instant refreshTokenExpiresAt, UserResponse user) {
    }
}
