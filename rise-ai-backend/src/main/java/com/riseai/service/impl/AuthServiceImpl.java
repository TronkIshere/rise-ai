package com.riseai.service.impl;

import com.riseai.configuration.RiseProperties;
import com.riseai.dto.request.LoginRequest;
import com.riseai.dto.request.RefreshRequest;
import com.riseai.dto.request.RegisterRequest;
import com.riseai.dto.response.TokenResponse;
import com.riseai.dto.response.UserResponse;
import com.riseai.entity.RefreshToken;
import com.riseai.entity.User;
import com.riseai.enums.RegistrableRole;
import com.riseai.enums.Role;
import com.riseai.enums.UserStatus;
import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;
import com.riseai.repository.RefreshTokenRepository;
import com.riseai.repository.UserRepository;
import com.riseai.security.JwtService;
import com.riseai.service.AuditService;
import com.riseai.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final RiseProperties properties;
    private volatile String dummyHash;

    @Transactional
    @Override
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setStatus(UserStatus.ACTIVE);
        user.getRoles().add(request.accountType() == RegistrableRole.CAMPAIGN_CREATOR ? Role.CAMPAIGN_CREATOR : Role.DONOR);
        userRepository.saveAndFlush(user);
        auditService.record(user.getId(), "USER_REGISTERED", "USER", user.getId(), null, UserStatus.ACTIVE.name(),
                AuditService.meta("roles", user.getRoles().toString()));
        return UserResponse.from(user);
    }

    @Transactional
    @Override
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalize(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash());
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        auditService.record(user.getId(), "USER_LOGIN", "USER", user.getId(), null, null, null);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    @Override
    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(sha256(request.refreshToken()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.REFRESH_TOKEN_INVALID));
        Instant now = Instant.now();
        if (token.getRevokedAt() != null) {
            refreshTokenRepository.revokeAllForUser(token.getUserId(), now);
            auditService.record(token.getUserId(), "REFRESH_TOKEN_REUSE_DETECTED", "USER", token.getUserId(), null, null, null);
            throw new ApplicationException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new ApplicationException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        User user = userRepository.findById(token.getUserId()).orElseThrow(() -> new ApplicationException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApplicationException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        token.setRevokedAt(now);
        return issueTokens(user);
    }

    @Transactional
    @Override
    public void logout(RefreshRequest request) {
        refreshTokenRepository.findByTokenHash(sha256(request.refreshToken()))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    auditService.record(token.getUserId(), "USER_LOGOUT", "USER", token.getUserId(), null, null, null);
                });
    }

    private TokenResponse issueTokens(User user) {
        String raw = randomToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(Instant.now().plus(properties.security().jwt().refreshTokenTtl()));
        refreshTokenRepository.save(token);
        return new TokenResponse(jwtService.issueAccessToken(user), "Bearer", jwtService.accessTokenTtlSeconds(), raw,
                token.getExpiresAt(), UserResponse.from(user));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
        }
        return dummyHash;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
