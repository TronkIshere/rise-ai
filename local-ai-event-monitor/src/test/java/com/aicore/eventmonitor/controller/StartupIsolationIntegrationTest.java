package com.aicore.eventmonitor.controller;

import com.aicore.eventmonitor.testsupport.StubOllamaServer;
import com.aicore.eventmonitor.testsupport.TestEvents;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StartupIsolationIntegrationTest {

    private static final StubOllamaServer OLLAMA = StubOllamaServer.start();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("ollama.base-url", OLLAMA::getBaseUrl);
        registry.add("ollama.model", () -> "qwen3.5:4b");
    }

    @AfterAll
    static void stopOllama() {
        OLLAMA.close();
    }

    @Test
    @Order(1)
    void startupMakesNoRequestsToOllamaAndDownloadsNothing() {
        assertThat(OLLAMA.getRequestPaths()).isEmpty();
    }

    @Test
    @Order(2)
    void analysisUsesTheConfiguredModelIdentifier() throws Exception {
        OLLAMA.respondWithAnalysis(TestEvents.VALID_ASSESSMENT_JSON);

        mockMvc.perform(post("/api/v1/events/monitor").contentType(MediaType.APPLICATION_JSON).content(event()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiAnalysis.modelName").value("qwen3.5:4b"));

        assertThat(OLLAMA.getChatRequests()).hasSize(1);
        assertThat(OLLAMA.getChatRequests().get(0)).contains("\"model\":\"qwen3.5:4b\"");
        assertThat(OLLAMA.getRequestPaths()).containsOnly("/api/chat");
    }

    private String event() {
        return "{\"eventId\":\"evt-" + UUID.randomUUID() + "\",\"eventType\":\"request.completed\","
                + "\"source\":\"edge-gateway\",\"occurredAt\":\"" + Instant.now().minusSeconds(5) + "\","
                + "\"attributes\":{\"latencyMs\":5000}}";
    }
}
