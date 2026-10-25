package com.riseai.monitoring.controller;

import com.riseai.monitoring.testsupport.StubOllamaServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OllamaOfflineIntegrationTest {

    private static final int OFFLINE_PORT = StubOllamaServer.unusedPort();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void ollamaProperties(DynamicPropertyRegistry registry) {
        registry.add("ollama.base-url", () -> "http://127.0.0.1:" + OFFLINE_PORT);
    }

    @Test
    void applicationStaysHealthyWhileOllamaIsOffline() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/api/v1/monitoring/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiRuntime.state").value("UNREACHABLE"));
    }

    @Test
    void flaggedEventIsInconclusiveButKeepsRuleFindings() throws Exception {
        mockMvc.perform(post("/api/v1/events/monitor").contentType(MediaType.APPLICATION_JSON).content(event(5000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monitoringStatus").value("INCONCLUSIVE"))
                .andExpect(jsonPath("$.data.aiAnalysis.analysisStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.ruleFindings", hasSize(1)))
                .andExpect(jsonPath("$.data.overallRiskLevel").value("HIGH"));
    }

    @Test
    void rulesOnlyEventsStillComplete() throws Exception {
        mockMvc.perform(post("/api/v1/events/monitor").contentType(MediaType.APPLICATION_JSON).content(event(120)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monitoringStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.overallRiskLevel").value("LOW"));
    }

    private String event(long latencyMs) {
        return "{\"eventId\":\"evt-" + UUID.randomUUID() + "\",\"eventType\":\"request.completed\","
                + "\"source\":\"edge-gateway\",\"occurredAt\":\"" + Instant.now().minusSeconds(5) + "\","
                + "\"attributes\":{\"latencyMs\":" + latencyMs + "}}";
    }
}
