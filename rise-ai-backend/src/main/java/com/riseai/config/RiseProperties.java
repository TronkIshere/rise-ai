package com.riseai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "rise")
public record RiseProperties(Security security, Escrow escrow, Simulation simulation, Seed seed) {

    public record Security(Jwt jwt, List<String> corsAllowedOrigins) {
    }

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Escrow(String currency, boolean disputeAutoHold) {
    }

    public record Simulation(boolean donationConfirmationEnabled) {
    }

    public record Seed(boolean enabled, String demoPassword) {
    }
}
