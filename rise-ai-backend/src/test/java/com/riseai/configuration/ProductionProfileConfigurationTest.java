package com.riseai.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionProfileConfigurationTest {

    private PropertySource<?> load(String name) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
        assertThat(sources).hasSize(1);
        return sources.get(0);
    }

    @Test
    void productionProfileDisablesDemoAndSimulationFeaturesAndRequiresSecrets() throws IOException {
        PropertySource<?> prod = load("application-prod.yml");

        assertThat(prod.getProperty("rise.seed.enabled")).isEqualTo(false);
        assertThat(prod.getProperty("rise.simulation.donation-confirmation-enabled")).isEqualTo(false);
        assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
        assertThat(prod.getProperty("logging.level.com.riseai")).isEqualTo("INFO");
        assertThat(String.valueOf(prod.getProperty("rise.security.jwt.secret"))).isEqualTo("${RISE_JWT_SECRET}");
        assertThat(String.valueOf(prod.getProperty("rise.security.cors-allowed-origins"))).isEqualTo("${RISE_CORS_ALLOWED_ORIGINS}");
    }

    @Test
    void defaultProfileStaysDevelopmentFriendlyAndShutsDownGracefully() throws IOException {
        PropertySource<?> defaults = load("application.yml");

        assertThat(defaults.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(defaults.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,info");
        assertThat(defaults.getProperty("server.error.include-stacktrace")).isEqualTo("never");
    }
}
