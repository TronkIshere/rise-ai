package com.riseai.monitoring.controller;

import com.riseai.monitoring.testsupport.StubOllamaServer;
import org.junit.jupiter.api.AfterAll;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiDisabledIntegrationTest {

    private static final StubOllamaServer OLLAMA = StubOllamaServer.start();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("ollama.base-url", OLLAMA::getBaseUrl);
        registry.add("monitoring.ai.enabled", () -> "false");
    }

    @AfterAll
    static void stopOllama() {
        OLLAMA.close();
    }

    @Test
    void flaggedEventsCompleteWithoutAiAndWithoutContactingOllama() throws Exception {
        mockMvc.perform(post("/api/v1/events/monitor").contentType(MediaType.APPLICATION_JSON).content(event()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monitoringStatus").value("COMPLETED_WITHOUT_AI"))
                .andExpect(jsonPath("$.data.aiAnalysis.analysisStatus").value("DISABLED"))
                .andExpect(jsonPath("$.data.ruleFindings", hasSize(1)))
                .andExpect(jsonPath("$.data.overallRiskLevel").value("HIGH"));

        mockMvc.perform(get("/api/v1/monitoring/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiAnalysisEnabled").value(false))
                .andExpect(jsonPath("$.data.aiRuntime.state").value("DISABLED"));

        assertThat(OLLAMA.getRequestPaths()).isEmpty();
    }

    private String event() {
        return "{\"eventId\":\"evt-" + UUID.randomUUID() + "\",\"eventType\":\"request.completed\","
                + "\"source\":\"edge-gateway\",\"occurredAt\":\"" + Instant.now().minusSeconds(5) + "\","
                + "\"attributes\":{\"latencyMs\":5000}}";
    }
}
