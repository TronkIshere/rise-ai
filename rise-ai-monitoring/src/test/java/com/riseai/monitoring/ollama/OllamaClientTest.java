package com.riseai.monitoring.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.configuration.OllamaProperties;
import com.riseai.monitoring.ollama.dto.OllamaChatMessage;
import com.riseai.monitoring.ollama.dto.OllamaChatRequest;
import com.riseai.monitoring.testsupport.StubOllamaServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StubOllamaServer server;
    private OllamaProperties properties;
    private OllamaClient client;

    @BeforeEach
    void setUp() {
        server = StubOllamaServer.start();
        properties = new OllamaProperties();
        properties.setBaseUrl(URI.create(server.getBaseUrl() + "/"));
        properties.setRequestTimeout(Duration.ofMillis(400));
        properties.setStatusTimeout(Duration.ofMillis(400));
        client = new OllamaClient(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        client.destroy();
        server.close();
    }

    @Test
    void sendsANonStreamingChatRequestAndReadsTheMessage() throws Exception {
        server.respondWithAnalysis("{\"ok\":true}");

        String content = client.chat(request()).getMessage().getContent();

        assertThat(content).isEqualTo("{\"ok\":true}");
        JsonNode sent = objectMapper.readTree(server.getChatRequests().get(0));
        assertThat(sent.get("model").asText()).isEqualTo("test-model");
        assertThat(sent.get("stream").asBoolean()).isFalse();
        assertThat(sent.get("format").get("type").asText()).isEqualTo("object");
        assertThat(sent.get("options").get("temperature").asDouble()).isZero();
        assertThat(sent.get("messages").get(0).get("role").asText()).isEqualTo("system");
    }

    @Test
    void reportsMissingModels() {
        server.respondWithStatus(404, "{\"error\":\"model \\\"test-model\\\" not found, try pulling it first\"}");

        assertFailure(OllamaErrorType.MODEL_NOT_FOUND);
    }

    @Test
    void reportsNonSuccessResponses() {
        server.respondWithStatus(500, "{\"error\":\"internal\"}");

        assertFailure(OllamaErrorType.HTTP_ERROR);
    }

    @Test
    void reportsUnreadableResponseBodies() {
        server.respondWithStatus(200, "this is not json");

        assertFailure(OllamaErrorType.INVALID_RESPONSE);
    }

    @Test
    void reportsResponsesWithoutAMessage() {
        server.respondWithStatus(200, "{\"done\":true}");

        assertFailure(OllamaErrorType.INVALID_RESPONSE);
    }

    @Test
    void reportsEmptyModelOutput() {
        server.respondWithAnalysis("   ");

        assertFailure(OllamaErrorType.EMPTY_OUTPUT);
    }

    @Test
    void aShorterCallerTimeoutCancelsAnInFlightRequestEarly() {
        properties.setRequestTimeout(Duration.ofSeconds(10));
        server.delayChatResponses(Duration.ofSeconds(5));
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> client.chat(request(), Duration.ofMillis(300)))
                .isInstanceOfSatisfying(OllamaException.class, ex -> assertThat(ex.getType()).isEqualTo(OllamaErrorType.TIMEOUT));

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void aLongerCallerTimeoutNeverExceedsTheConfiguredRequestTimeout() {
        server.delayChatResponses(Duration.ofSeconds(3));
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> client.chat(request(), Duration.ofSeconds(60)))
                .isInstanceOfSatisfying(OllamaException.class, ex -> assertThat(ex.getType()).isEqualTo(OllamaErrorType.TIMEOUT));

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void reportsTimeoutsWithinTheConfiguredLimit() {
        server.delayChatResponses(Duration.ofSeconds(3));
        long startedAt = System.nanoTime();

        assertFailure(OllamaErrorType.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void reportsConnectionFailures() {
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + StubOllamaServer.unusedPort()));
        OllamaClient offlineClient = new OllamaClient(properties, objectMapper);

        assertThatThrownBy(() -> offlineClient.chat(request()))
                .isInstanceOf(OllamaException.class)
                .extracting(ex -> ((OllamaException) ex).getType())
                .isEqualTo(OllamaErrorType.CONNECTION_FAILED);
        offlineClient.destroy();
    }

    @Test
    void listsInstalledModels() {
        server.installedModels("llama3.2:latest", "qwen2.5:7b");

        assertThat(client.listModelNames()).containsExactly("llama3.2:latest", "qwen2.5:7b");
    }

    @Test
    void rejectsNonHttpBaseUrls() {
        properties.setBaseUrl(URI.create("ftp://localhost:11434"));

        assertThatThrownBy(() -> new OllamaClient(properties, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ollama.base-url");
    }

    private void assertFailure(OllamaErrorType expectedType) {
        assertThatThrownBy(() -> client.chat(request()))
                .isInstanceOf(OllamaException.class)
                .extracting(ex -> ((OllamaException) ex).getType())
                .isEqualTo(expectedType);
    }

    private OllamaChatRequest request() {
        return OllamaChatRequest.builder()
                .model("test-model")
                .messages(List.of(new OllamaChatMessage("system", "rules"), new OllamaChatMessage("user", "data")))
                .stream(false)
                .format(Map.of("type", "object"))
                .options(Map.<String, Object>of("temperature", 0.0))
                .build();
    }
}
