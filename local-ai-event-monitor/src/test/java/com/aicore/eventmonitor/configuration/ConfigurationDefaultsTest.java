package com.aicore.eventmonitor.configuration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationDefaultsTest {

    @Test
    void applicationYamlConfiguresTheLocalModelThroughOverridableProperties() throws IOException {
        String yaml = applicationYaml();

        assertThat(yaml)
                .contains("base-url: ${OLLAMA_BASE_URL:http://localhost:11434}")
                .contains("model: ${OLLAMA_MODEL:qwen3.5:9b}")
                .contains("connect-timeout: ${OLLAMA_CONNECT_TIMEOUT:2s}")
                .contains("request-timeout: ${OLLAMA_REQUEST_TIMEOUT:60s}")
                .contains("enabled: ${MONITORING_AI_ENABLED:true}")
                .contains("include-payload: ${MONITORING_AI_INCLUDE_PAYLOAD:false}");
    }

    @Test
    void applicationYamlReferencesNoCloudAiProvider() throws IOException {
        String yaml = applicationYaml().toLowerCase(Locale.ROOT);

        assertThat(yaml).doesNotContain("openai").doesNotContain("anthropic").doesNotContain("googleapis")
                .doesNotContain("api-key").doesNotContain("api_key");
    }

    @Test
    void javaDefaultsDoNotHardcodeAModel() {
        OllamaProperties properties = new OllamaProperties();

        assertThat(properties.getModel()).isNull();
        assertThat(properties.getThink()).isFalse();
        assertThat(properties.getContextLength()).isNull();
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void monitoringDefaultsMinimizeDataSentToTheModel() {
        MonitoringProperties.Ai ai = new MonitoringProperties().getAi();

        assertThat(ai.isIncludePayload()).isFalse();
        assertThat(ai.getRedactedFieldNames()).contains("password", "token", "secret");
    }

    private String applicationYaml() throws IOException {
        try (InputStream input = ConfigurationDefaultsTest.class.getResourceAsStream("/application.yml")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
