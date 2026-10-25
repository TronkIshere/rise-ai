package com.riseai.dto.response;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
                            Instant refreshTokenExpiresAt, UserResponse user) {
}
