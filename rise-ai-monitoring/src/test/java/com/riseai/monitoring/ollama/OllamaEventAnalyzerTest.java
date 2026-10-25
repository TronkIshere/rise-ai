package com.riseai.monitoring.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.analysis.AiAssessmentOutput;
import com.riseai.monitoring.analysis.AiOutputParser;
import com.riseai.monitoring.analysis.AnalysisPromptBuilder;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.configuration.OllamaProperties;
import com.riseai.monitoring.core.analysis.AiAnalysis;
import com.riseai.monitoring.core.analysis.AiAssessment;
import com.riseai.monitoring.core.analysis.AiConfidence;
import com.riseai.monitoring.core.analysis.AiRuntimeState;
import com.riseai.monitoring.core.analysis.AnalysisStatus;
import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.rule.RuleFinding;
import com.riseai.monitoring.testsupport.StubOllamaServer;
import com.riseai.monitoring.testsupport.TestEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaEventAnalyzerTest {

    private static final List<RuleFinding> FINDINGS =
            List.of(RuleFinding.flag("attribute-threshold", Severity.HIGH, "latencyMs is above maximum"));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MonitoringProperties monitoringProperties = new MonitoringProperties();
    private StubOllamaServer server;
    private OllamaProperties ollamaProperties;
    private OllamaClient client;

    @BeforeEach
    void setUp() {
        server = StubOllamaServer.start();
        ollamaProperties = new OllamaProperties();
        ollamaProperties.setBaseUrl(URI.create(server.getBaseUrl()));
        ollamaProperties.setModel("test-model");
        ollamaProperties.setRequestTimeout(Duration.ofMillis(400));
        ollamaProperties.setStatusTimeout(Duration.ofMillis(400));
        client = new OllamaClient(ollamaProperties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        client.destroy();
        server.close();
    }

    @Test
    void returnsAValidatedVersionedAssessment() {
        server.respondWithAnalysis(TestEvents.VALID_ASSESSMENT_JSON);

        AiAnalysis analysis = analyzer().analyze(TestEvents.event().build(), FINDINGS);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(analysis.getSchemaVersion()).isEqualTo(AiAssessmentOutput.SCHEMA_VERSION);
        assertThat(analysis.getAssessment()).isEqualTo(AiAssessment.NEEDS_REVIEW);
        assertThat(analysis.getConfidence()).isEqualTo(AiConfidence.MEDIUM);
        assertThat(analysis.getPossibleCauses()).containsExactly("Upstream dependency slowness");
        assertThat(analysis.getMissingEvidence()).containsExactly("Recent latency baseline for this source");
        assertThat(analysis.getSuggestedReview()).isTrue();
        assertThat(analysis.getModelName()).isEqualTo("test-model");
        assertThat(server.getChatRequests().get(0)).contains("latencyMs is above maximum");
    }

    @Test
    void usesTheConfiguredModelAndRuntimeOptions() throws Exception {
        ollamaProperties.setModel("qwen3.5:4b");
        server.respondWithAnalysis(TestEvents.VALID_ASSESSMENT_JSON);

        analyzer().analyze(TestEvents.event().build(), FINDINGS);
        ollamaProperties.setContextLength(8192);
        analyzer().analyze(TestEvents.event().build(), FINDINGS);

        JsonNode first = objectMapper.readTree(server.getChatRequests().get(0));
        assertThat(first.get("model").asText()).isEqualTo("qwen3.5:4b");
        assertThat(first.get("think").asBoolean()).isFalse();
        assertThat(first.get("stream").asBoolean()).isFalse();
        assertThat(first.get("format").get("additionalProperties").asBoolean()).isFalse();
        assertThat(first.get("options").has("num_ctx")).isFalse();
        assertThat(objectMapper.readTree(server.getChatRequests().get(1)).get("options").get("num_ctx").asInt()).isEqualTo(8192);
    }

    @Test
    void omitsTheThinkFlagWhenNotConfigured() throws Exception {
        ollamaProperties.setThink(null);
        server.respondWithAnalysis(TestEvents.VALID_ASSESSMENT_JSON);

        analyzer().analyze(TestEvents.event().build(), FINDINGS);

        assertThat(objectMapper.readTree(server.getChatRequests().get(0)).has("think")).isFalse();
    }

    @Test
    void marksMalformedJsonAsInvalidResponse() {
        server.respondWithAnalysis("I think this event is fine.");

        assertStatus(AnalysisStatus.INVALID_RESPONSE);
    }

    @Test
    void marksSchemaViolationsAsInvalidResponse() {
        server.respondWithAnalysis(TestEvents.VALID_ASSESSMENT_JSON.replace("NEEDS_REVIEW", "FRAUD"));

        AiAnalysis analysis = assertStatus(AnalysisStatus.INVALID_RESPONSE);
        assertThat(analysis.getStatusDetail()).contains("assessment has an unexpected value");
        assertThat(analysis.getAssessment()).isNull();
        assertThat(analysis.getSuggestedReview()).isNull();
    }

    @Test
    void marksEmptyAndUnreadableResponsesAsInvalid() {
        server.respondWithAnalysis("  ");
        assertStatus(AnalysisStatus.INVALID_RESPONSE);

        server.respondWithStatus(200, "not json at all");
        assertStatus(AnalysisStatus.INVALID_RESPONSE);
    }

    @Test
    void reportsUnavailableWhenOllamaRefusesConnections() {
        ollamaProperties.setBaseUrl(URI.create("http://127.0.0.1:" + StubOllamaServer.unusedPort()));
        client = new OllamaClient(ollamaProperties, objectMapper);

        assertStatus(AnalysisStatus.UNAVAILABLE);
    }

    @Test
    void reportsTimeouts() {
        server.delayChatResponses(Duration.ofSeconds(3));

        assertStatus(AnalysisStatus.TIMEOUT);
    }

    @Test
    void reportsUnavailableWhenTheModelIsNotInstalled() {
        server.respondWithStatus(404, "{\"error\":\"model 'test-model' not found\"}");

        assertThat(assertStatus(AnalysisStatus.UNAVAILABLE).getStatusDetail()).contains("not installed");
    }

    @Test
    void reportsFailuresForServerErrors() {
        server.respondWithStatus(500, "{\"error\":\"boom\"}");

        assertStatus(AnalysisStatus.FAILED);
    }

    @Test
    void doesNotCallOllamaWithoutAConfiguredModel() {
        ollamaProperties.setModel(" ");

        assertStatus(AnalysisStatus.UNAVAILABLE);
        assertThat(server.getRequestPaths()).isEmpty();
    }

    @Test
    void reportsRuntimeAvailabilityIncludingImplicitLatestTag() {
        ollamaProperties.setModel("qwen3.5");
        server.installedModels("qwen3.5:latest");

        assertThat(analyzer().checkRuntimeStatus().getState()).isEqualTo(AiRuntimeState.AVAILABLE);
    }

    @Test
    void reportsMissingModelsAndUnreachableRuntime() {
        ollamaProperties.setModel("qwen3.5:9b");
        server.installedModels("qwen3.5:4b");
        assertThat(analyzer().checkRuntimeStatus().getState()).isEqualTo(AiRuntimeState.MODEL_NOT_INSTALLED);
        assertThat(analyzer().checkRuntimeStatus().getDetail()).contains("ollama pull qwen3.5:9b");

        ollamaProperties.setBaseUrl(URI.create("http://127.0.0.1:" + StubOllamaServer.unusedPort()));
        client = new OllamaClient(ollamaProperties, objectMapper);
        assertThat(analyzer().checkRuntimeStatus().getState()).isEqualTo(AiRuntimeState.UNREACHABLE);

        ollamaProperties.setModel(null);
        assertThat(analyzer().checkRuntimeStatus().getState()).isEqualTo(AiRuntimeState.NOT_CONFIGURED);
    }

    @Test
    void creatingTheClientMakesNoNetworkCalls() {
        OllamaClient freshClient = new OllamaClient(ollamaProperties, objectMapper);

        assertThat(server.getRequestPaths()).isEmpty();
        freshClient.destroy();
    }

    private AiAnalysis assertStatus(AnalysisStatus expected) {
        AiAnalysis analysis = analyzer().analyze(TestEvents.event().build(), FINDINGS);
        assertThat(analysis.getStatus()).isEqualTo(expected);
        return analysis;
    }

    private OllamaEventAnalyzer analyzer() {
        return new OllamaEventAnalyzer(client, ollamaProperties,
                new AnalysisPromptBuilder(objectMapper, monitoringProperties),
                new AiOutputParser(objectMapper, monitoringProperties));
    }
}
