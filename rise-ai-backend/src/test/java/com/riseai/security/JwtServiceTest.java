package com.riseai.security;

import com.riseai.configuration.RiseProperties;
import com.riseai.entity.User;
import com.riseai.enums.Role;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-that-is-long-enough-for-hs256";

    private static RiseProperties properties(String secret, Duration ttl) {
        return new RiseProperties(
                new RiseProperties.Security(new RiseProperties.Jwt(secret, "rise-ai", ttl, Duration.ofDays(1)), List.of()),
                new RiseProperties.Escrow("VND", true), new RiseProperties.Simulation(true), new RiseProperties.Seed(false, "x"),
                new RiseProperties.Jobs(false, "-", "-"));
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("donor@test.example");
        user.getRoles().add(Role.DONOR);
        return user;
    }

    @Test
    void issuedTokenVerifiesToSubject() {
        JwtService service = new JwtService(properties(SECRET, Duration.ofMinutes(5)));
        User user = user();
        assertThat(service.verifyAccessToken(service.issueAccessToken(user))).contains(user.getId());
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = new JwtService(properties(SECRET, Duration.ofMinutes(5)));
        String token = service.issueAccessToken(user());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(service.verifyAccessToken(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService issuer = new JwtService(properties(SECRET, Duration.ofMinutes(5)));
        JwtService verifier = new JwtService(properties(SECRET + "-other", Duration.ofMinutes(5)));
        assertThat(verifier.verifyAccessToken(issuer.issueAccessToken(user()))).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = new JwtService(properties(SECRET, Duration.ofSeconds(-5)));
        assertThat(service.verifyAccessToken(service.issueAccessToken(user()))).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        JwtService service = new JwtService(properties(SECRET, Duration.ofMinutes(5)));
        assertThat(service.verifyAccessToken("not-a-jwt")).isEmpty();
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(properties("short", Duration.ofMinutes(5)))).isInstanceOf(IllegalStateException.class);
    }
}
