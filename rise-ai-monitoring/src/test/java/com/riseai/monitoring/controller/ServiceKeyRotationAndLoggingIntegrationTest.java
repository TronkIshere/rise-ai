package com.riseai.monitoring.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "monitoring.security.service-key=" + ServiceKeyRotationAndLoggingIntegrationTest.NEW_KEY,
        "monitoring.security.previous-service-key=" + ServiceKeyRotationAndLoggingIntegrationTest.OLD_KEY,
        "monitoring.ai.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceKeyRotationAndLoggingIntegrationTest {
    static final String NEW_KEY = "new-key-4f8a1c";
    static final String OLD_KEY = "old-key-9b2e7d";
    private static final String SECRET_VALUE = "secret-attribute-value-731";
    private static final String SECRET_PAYLOAD = "secret-payload-note-552";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger root;
    private Level originalLevel;

    @BeforeEach
    void captureLogs() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        originalLevel = root.getLevel();
        root.setLevel(Level.DEBUG);
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        root.detachAppender(appender);
        root.setLevel(originalLevel);
    }

    @Test
    void currentAndPreviousKeysAreAcceptedDuringOverlapAndOthersAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/status").header("Authorization", "Bearer " + NEW_KEY)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/monitoring/status").header("Authorization", "Bearer " + OLD_KEY)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/monitoring/status").header("Authorization", "Bearer retired-key")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/monitoring/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void noKeyOrEventContentIsLoggedAtAnyLevel() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("eventId", eventId);
        request.put("eventType", "EVIDENCE_SUBMITTED");
        request.put("source", "external-app");
        request.put("occurredAt", Instant.now().minusSeconds(30).toString());
        request.put("attributes", Map.of("note", SECRET_VALUE));
        request.put("payload", Map.of("memo", SECRET_PAYLOAD));
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/events/monitor").header("Authorization", "Bearer " + NEW_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/events/monitor").header("Authorization", "Bearer wrong-key-000")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());

        List<String> lines = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertThat(lines).isNotEmpty();
        assertThat(lines).noneMatch(line -> line.contains(NEW_KEY) || line.contains(OLD_KEY) || line.contains("wrong-key-000")
                || line.contains(SECRET_VALUE) || line.contains(SECRET_PAYLOAD));
    }
}
