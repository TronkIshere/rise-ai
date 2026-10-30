package com.riseai.monitoring.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.observability.MonitoringMetrics;
import com.riseai.monitoring.rise.RiseEnvelope;
import com.riseai.monitoring.rise.RiseFindingRule;
import com.riseai.monitoring.rise.rule.DuplicateEvidenceHashRule;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"monitoring.security.service-key=" + RiseRuleFailureIsolationIntegrationTest.KEY, "monitoring.ai.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RiseRuleFailureIsolationIntegrationTest {
    static final String KEY = "rise-failure-key";
    private static final String MONITOR = "/api/v1/events/monitor";
    private static final String FLAKY_RULE = "rise.test.flaky";
    private static final String LEAKED_VALUE = "payload-value-that-must-not-be-logged";
    private static final AtomicBoolean FAILING = new AtomicBoolean();

    @TestConfiguration
    static class FlakyRuleConfiguration {
        @Bean
        RiseFindingRule flakyRule() {
            return new RiseFindingRule() {
                @Override
                public String getId() {
                    return FLAKY_RULE;
                }

                @Override
                public String getVersion() {
                    return "1";
                }

                @Override
                public String getDescription() {
                    return "Test rule that fails while switched on";
                }

                @Override
                public List<Finding> evaluate(RiseEnvelope envelope) {
                    if (FAILING.get()) {
                        throw new IllegalStateException(LEAKED_VALUE);
                    }
                    return List.of();
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcClient jdbcClient;
    @Autowired
    MeterRegistry meterRegistry;

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;
    private Instant clock = Instant.now().minus(2, ChronoUnit.HOURS);

    @BeforeEach
    void captureLogs() {
        previousLevel = rootLogger.getLevel();
        rootLogger.setLevel(Level.TRACE);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void restore() {
        FAILING.set(false);
        rootLogger.detachAppender(appender);
        appender.stop();
        rootLogger.setLevel(previousLevel);
    }

    @Test
    void aFailingRuleFailsTheEventWithoutPersistingAndRedeliveryYieldsCompleteFindingsExactlyOnce() throws Exception {
        String hash = hash();
        monitor(evidence(id(), id(), id(), hash), 200);
        Map<String, Object> second = evidence(id(), id(), id(), hash);
        String secondEventId = (String) second.get("eventId");
        double failuresBefore = failures();

        FAILING.set(true);
        mockMvc.perform(post(MONITOR).header("Authorization", "Bearer " + KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(9001));
        assertThat(failures() - failuresBefore).isEqualTo(1.0);
        assertThat(rowsFor(secondEventId)).isZero();
        assertThat(processedRowsFor(secondEventId)).isZero();

        monitor(second, 500);
        assertThat(failures() - failuresBefore).isEqualTo(2.0);
        assertThat(rowsFor(secondEventId)).isZero();

        FAILING.set(false);
        JsonNode delivered = monitor(second, 200);
        assertThat(delivered.path("duplicate").asBoolean()).isFalse();
        assertThat(delivered.path("findings")).hasSize(1);
        JsonNode finding = findingFor(delivered, DuplicateEvidenceHashRule.ID);
        assertThat(finding.path("severity").asText()).isEqualTo("HIGH");
        assertThat(rowsFor(secondEventId)).isEqualTo(1);
        assertThat(processedRowsFor(secondEventId)).isEqualTo(1);

        JsonNode replay = monitor(second, 200);
        assertThat(replay.path("duplicate").asBoolean()).isTrue();
        assertThat(findingFor(replay, DuplicateEvidenceHashRule.ID).path("findingId").asText()).isEqualTo(finding.path("findingId").asText());
        assertThat(rowsFor(secondEventId)).isEqualTo(1);
        assertThat(processedRowsFor(secondEventId)).isEqualTo(1);
    }

    @Test
    void theFailureIsCountedPerRuleAndItsMessageIsNeverLogged() throws Exception {
        double failuresBefore = failures();
        FAILING.set(true);

        monitor(evidence(id(), id(), id(), hash()), 500);

        assertThat(failures() - failuresBefore).isEqualTo(1.0);
        assertThat(appender.list).anyMatch(entry -> entry.getFormattedMessage().contains(FLAKY_RULE));
        assertThat(appender.list).noneMatch(entry -> entry.getFormattedMessage().contains(LEAKED_VALUE) || throwableMentions(entry.getThrowableProxy()));
    }

    private static boolean throwableMentions(IThrowableProxy throwable) {
        return throwable != null && (throwable.getMessage() != null && throwable.getMessage().contains(LEAKED_VALUE)
                || throwableMentions(throwable.getCause()));
    }

    private double failures() {
        return meterRegistry.find(MonitoringMetrics.RISE_RULE_FAILURES).tag("rule", FLAKY_RULE).counters().stream()
                .mapToDouble(counter -> counter.count()).sum();
    }

    private long rowsFor(String eventId) {
        return jdbcClient.sql("select count(*) from rise_evidence_hashes where event_id = ?").param(eventId).query(Long.class).single();
    }

    private long processedRowsFor(String eventId) {
        return jdbcClient.sql("select count(*) from rise_processed_events where event_id = ?").param(eventId).query(Long.class).single();
    }

    private JsonNode monitor(Map<String, Object> request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(MONITOR).header("Authorization", "Bearer " + KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static JsonNode findingFor(JsonNode result, String ruleId) {
        for (JsonNode finding : result.path("findings")) {
            if (finding.path("detector").path("id").asText().equals(ruleId)) {
                return finding;
            }
        }
        throw new AssertionError("No finding from " + ruleId + " in " + result.path("findings"));
    }

    private Map<String, Object> evidence(String campaignId, String milestoneId, String evidenceId, String hash) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceId", evidenceId);
        payload.put("milestoneId", milestoneId);
        payload.put("campaignId", campaignId);
        payload.put("evidenceType", "INVOICE");
        payload.put("fileHash", hash);
        String eventId = id();
        clock = clock.plusSeconds(1);
        String occurredAt = clock.toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", "MILESTONE_EVIDENCE_SUBMITTED");
        envelope.put("schemaVersion", 1);
        envelope.put("occurredAt", occurredAt);
        envelope.put("producer", "rise-ai-backend");
        envelope.put("aggregateType", "EVIDENCE");
        envelope.put("aggregateId", id());
        envelope.put("actorRole", "ADMIN");
        envelope.put("references", Map.of("campaignId", campaignId, "milestoneId", milestoneId, "evidenceId", evidenceId));
        envelope.put("payload", payload);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("eventId", eventId);
        request.put("eventType", "MILESTONE_EVIDENCE_SUBMITTED");
        request.put("source", "rise-ai-backend");
        request.put("occurredAt", occurredAt);
        request.put("attributes", Map.of("schemaVersion", 1, "aggregateType", "EVIDENCE"));
        request.put("payload", envelope);
        return request;
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
