package com.riseai.configuration;

import com.riseai.configuration.RiseProperties.Escrow;
import com.riseai.configuration.RiseProperties.Jobs;
import com.riseai.configuration.RiseProperties.Jwt;
import com.riseai.configuration.RiseProperties.Security;
import com.riseai.configuration.RiseProperties.Seed;
import com.riseai.configuration.RiseProperties.Simulation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretsValidatorTest {
    private static final String STRONG_SECRET = "a-production-secret-that-is-long-enough-123456";

    private static RiseProperties properties(String jwtSecret, boolean seed, String demoPassword) {
        return properties(jwtSecret, seed, demoPassword, seed);
    }

    private static RiseProperties properties(String jwtSecret, boolean seed, String demoPassword, boolean simulation) {
        return new RiseProperties(new Security(new Jwt(jwtSecret, "rise-ai", Duration.ofMinutes(30), Duration.ofDays(7)), List.of()),
                new Escrow("VND", true), new Simulation(simulation), new Seed(seed, demoPassword), new Jobs(false, "-", "-"));
    }

    private static MonitoringRelayProperties monitoring(boolean enabled, String key) {
        return new MonitoringRelayProperties(enabled, null, key, null, null, null, null, null, null, null, null, null, null);
    }

    private static void validate(RiseProperties properties, MonitoringRelayProperties monitoring) {
        new ProductionSecretsValidator(properties, monitoring).validate();
    }

    @Test
    void acceptsAStrongSecretWithSeedingOff() {
        assertThatCode(() -> validate(properties(STRONG_SECRET, false, "x"), monitoring(false, null))).doesNotThrowAnyException();
    }

    @Test
    void rejectsTheDevelopmentJwtSecret() {
        assertThatThrownBy(() -> validate(properties(ProductionSecretsValidator.DEVELOPMENT_JWT_SECRET, false, "x"), monitoring(false, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("jwt.secret");
    }

    @Test
    void rejectsTheDevelopmentDemoPasswordOnlyWhenSeedingIsEnabled() {
        assertThatThrownBy(() -> validate(properties(STRONG_SECRET, true, ProductionSecretsValidator.DEVELOPMENT_DEMO_PASSWORD), monitoring(false, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("demo-password");
        assertThatCode(() -> validate(properties(STRONG_SECRET, false, ProductionSecretsValidator.DEVELOPMENT_DEMO_PASSWORD), monitoring(false, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void seedingWithoutTheSimulationPathFailsFastWithAClearMessage() {
        assertThatThrownBy(() -> validate(properties(STRONG_SECRET, true, "Another-Demo-Pass-1", false), monitoring(false, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("donation-confirmation-enabled");
        assertThatCode(() -> validate(properties(STRONG_SECRET, true, "Another-Demo-Pass-1", true), monitoring(false, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void requiresAServiceKeyWhenTheRelayIsEnabled() {
        assertThatThrownBy(() -> validate(properties(STRONG_SECRET, false, "x"), monitoring(true, " ")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("service-key");
        assertThatCode(() -> validate(properties(STRONG_SECRET, false, "x"), monitoring(true, "k"))).doesNotThrowAnyException();
    }
}
