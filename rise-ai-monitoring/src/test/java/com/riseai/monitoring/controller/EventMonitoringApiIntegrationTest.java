package com.riseai.monitoring.controller;

import com.riseai.monitoring.testsupport.StubOllamaServer;
import com.riseai.monitoring.testsupport.TestEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventMonitoringApiIntegrationTest {

    private static final StubOllamaServer OLLAMA = StubOllamaServer.shared();
    private static final String MONITOR = "/api/v1/events/monitor";

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void ollamaProperties(DynamicPropertyRegistry registry) {
        registry.add("ollama.base-url", OLLAMA::getBaseUrl);
    }

    @BeforeEach
    void setUp() {
        OLLAMA.reset();
        OLLAMA.installedModels("test-model:latest");
    }

    @Test
    void rulesOnlyEventCompletesWithoutCallingTheModel() throws Exception {
        monitor(event(newId(), 120))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Event processed"))
                .andExpect(jsonPath("$.data.monitoringStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.ruleDecision").value("RULES_SUFFICIENT"))
                .andExpect(jsonPath("$.data.overallRiskLevel").value("LOW"))
                .andExpect(jsonPath("$.data.ruleFindings", hasSize(0)))
                .andExpect(jsonPath("$.data.aiAnalysis.analysisStatus").value("SKIPPED"))
                .andExpect(jsonPath("$.data.createdAt", notNullValue()));

        org.assertj.core.api.Assertions.assertThat(OLLAMA.getChatRequests()).isEmpty();
    }

    @Test
    void flaggedEventIsAnalyzedByTheLocalModel() throws Exception {
        OLLAMA.respondWithAnalysis(TestEvents.VALID_ASSESSMENT_JSON);

        monitor(event(newId(), 5000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monitoringStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.ruleFindings[0].ruleId").value("attribute-threshold"))
                .andExpect(jsonPath("$.data.ruleFindings[0].effect").value("FLAG"))
                .andExpect(jsonPath("$.data.aiAnalysis.analysisStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.aiAnalysis.schemaVersion").value("monitoring-assessment.v1"))
                .andExpect(jsonPath("$.data.aiAnalysis.assessment").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.aiAnalysis.confidence").value("MEDIUM"))
                .andExpect(jsonPath("$.data.aiAnalysis.possibleCauses[0]").value("Upstream dependency slowness"))
                .andExpect(jsonPath("$.data.aiAnalysis.missingEvidence[0]").value("Recent latency baseline for this source"))
                .andExpect(jsonPath("$.data.aiAnalysis.suggestedReview").value(true))
                .andExpect(jsonPath("$.data.aiAnalysis.modelName").value("test-model"))
                .andExpect(jsonPath("$.data.overallRiskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.reviewRequired").value(true));
    }

    @Test
    void malformedModelOutputIsInvalidAndRuleFindingsRemain() throws Exception {
        OLLAMA.respondWithAnalysis("The event looks fine to me.");

        monitor(event(newId(), 5000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monitoringStatus").value("INCONCLUSIVE"))
                .andExpect(jsonPath("$.data.aiAnalysis.analysisStatus").value("INVALID_RESPONSE"))
                .andExpect(jsonPath("$.data.aiAnalysis.assessment").doesNotExist())
                .andExpect(jsonPath("$.data.ruleFindings", hasSize(1)))
                .andExpect(jsonPath("$.data.overallRiskLevel").value("HIGH"));
    }

    @Test
    void modelTimeoutIsInconclusive() throws Exception {
        OLLAMA.delayChatResponses(Duration.ofSeconds(2));

        monitor(event(newId(), 5000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monitoringStatus").value("INCONCLUSIVE"))
                .andExpect(jsonPath("$.data.aiAnalysis.analysisStatus").value("TIMEOUT"));
    }

    @Test
    void missingModelIsReportedAsUnavailable() throws Exception {
        OLLAMA.respondWithStatus(404, "{\"error\":\"model 'test-model' not found\"}");

        monitor(event(newId(), 5000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiAnalysis.analysisStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.monitoringStatus").value("INCONCLUSIVE"));
    }

    @Test
    void duplicateEventReplaysTheOriginalResult() throws Exception {
        String body = event(newId(), 120);
        monitor(body).andExpect(jsonPath("$.data.duplicate").value(false));

        monitor(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Duplicate event, returning the original result"))
                .andExpect(jsonPath("$.data.duplicate").value(true))
                .andExpect(jsonPath("$.data.monitoringStatus").value("COMPLETED"));
    }

    @Test
    void reusedEventIdWithDifferentContentReturnsConflict() throws Exception {
        String eventId = newId();
        monitor(event(eventId, 120)).andExpect(status().isOk());

        monitor(event(eventId, 130))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(2001));
    }

    @Test
    void eventsRejectedByRulesReturnAStructuredResult() throws Exception {
        String body = event(newId(), 120).replaceFirst("\"occurredAt\":\"[^\"]+\"",
                "\"occurredAt\":\"" + Instant.now().plus(Duration.ofHours(3)) + "\"");

        monitor(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monitoringStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.ruleFindings[0].ruleId").value("timestamp-validity"))
                .andExpect(jsonPath("$.data.aiAnalysis.analysisStatus").value("SKIPPED"))
                .andExpect(jsonPath("$.data.overallRiskLevel").value("UNKNOWN"));
    }

    @Test
    void invalidEventsAreRejectedWithValidationErrors() throws Exception {
        monitor("{\"eventId\":\"" + newId() + "\",\"source\":\"edge-gateway\",\"occurredAt\":\"" + Instant.now() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(1000))
                .andExpect(jsonPath("$.error").value("eventType must not be blank"))
                .andExpect(jsonPath("$.path").value(MONITOR));
    }

    @Test
    void nestedAttributeValuesAreRejected() throws Exception {
        monitor(event(newId(), 120).replace("\"region\":\"eu-west\"", "\"region\":{\"name\":\"eu-west\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(2000));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        monitor("{\"eventId\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(1001));
    }

    @Test
    void oversizedRequestsAreRejected() throws Exception {
        String body = event(newId(), 120).replace("\"path\":\"/orders\"", "\"path\":\"" + "x".repeat(20000) + "\"");

        monitor(body)
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.status").value(1005));
    }

    @Test
    void correlationIdIsPropagated() throws Exception {
        mockMvc.perform(post(MONITOR).header("X-Correlation-Id", "trace-42")
                        .contentType(MediaType.APPLICATION_JSON).content(event(newId(), 120)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "trace-42"))
                .andExpect(jsonPath("$.data.correlationId").value("trace-42"));
    }

    @Test
    void statusEndpointReportsModelAvailabilityWithoutInternalConfiguration() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiAnalysisEnabled").value(true))
                .andExpect(jsonPath("$.data.aiRuntime.runtime").value("ollama"))
                .andExpect(jsonPath("$.data.aiRuntime.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.aiRuntime.modelName").value("test-model"))
                .andExpect(jsonPath("$.data.aiRuntime.baseUrl").doesNotExist())
                .andExpect(jsonPath("$.data.eventPersistence").value("NONE"))
                .andExpect(jsonPath("$.data.riseProjection").value("MINIMIZED_PROJECTION"))
                .andExpect(jsonPath("$.data.deduplicationStore").value("JDBC"))
                .andExpect(jsonPath("$.data.deduplicationRetention").value("PT24H"));
    }

    @Test
    void rulesEndpointListsActiveRules() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(14)))
                .andExpect(jsonPath("$.data[*].id", hasItems("required-fields", "timestamp-validity",
                        "supported-event-type", "payload-limits", "attribute-threshold", "reported-severity")));
    }

    @Test
    void healthAndMetricsAreExposed() throws Exception {
        monitor(event(newId(), 120)).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/metrics/riseai.monitoring.events.received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("riseai.monitoring.events.received"));
    }

    private ResultActions monitor(String body) throws Exception {
        return mockMvc.perform(post(MONITOR).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String newId() {
        return "evt-" + UUID.randomUUID();
    }

    private String event(String eventId, long latencyMs) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"request.completed\",\"source\":\"edge-gateway\","
                + "\"occurredAt\":\"" + Instant.now().minusSeconds(5) + "\",\"severity\":\"low\","
                + "\"attributes\":{\"latencyMs\":" + latencyMs + ",\"region\":\"eu-west\"},"
                + "\"payload\":{\"path\":\"/orders\",\"status\":200}}";
    }
}
