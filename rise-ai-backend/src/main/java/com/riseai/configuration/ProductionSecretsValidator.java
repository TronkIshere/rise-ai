package com.riseai.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionSecretsValidator {
    static final String DEVELOPMENT_JWT_SECRET = "dev-only-change-me-rise-ai-local-jwt-secret-32bytes-min";
    static final String DEVELOPMENT_DEMO_PASSWORD = "Demo@12345";

    private final RiseProperties properties;
    private final MonitoringRelayProperties monitoring;

    @PostConstruct
    void validate() {
        if (DEVELOPMENT_JWT_SECRET.equals(properties.security().jwt().secret())) {
            throw new IllegalStateException("rise.security.jwt.secret must not use the development default in the prod profile");
        }
        if (properties.seed().enabled() && DEVELOPMENT_DEMO_PASSWORD.equals(properties.seed().demoPassword())) {
            throw new IllegalStateException("rise.seed.demo-password must not use the development default when seeding is enabled");
        }
        if (properties.seed().enabled() && !properties.simulation().donationConfirmationEnabled()) {
            throw new IllegalStateException("rise.seed.enabled requires rise.simulation.donation-confirmation-enabled=true because the seeder confirms donations through the simulation path");
        }
        if (monitoring.enabled() && !monitoring.hasServiceKey()) {
            throw new IllegalStateException("rise.monitoring.service-key is required when the monitoring relay is enabled");
        }
    }
}
