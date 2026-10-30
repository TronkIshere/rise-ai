package com.riseai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.dto.request.LoginRequest;
import com.riseai.dto.response.FindingResponse;
import com.riseai.dto.response.RulePrecisionResponse;
import com.riseai.support.FindingSchemaContract;
import com.riseai.entity.MonitoringFinding;
import com.riseai.entity.OutboxEvent;
import com.riseai.enums.FindingReviewStatus;
import com.riseai.enums.FindingSeverity;
import com.riseai.enums.OutboxStatus;
import com.riseai.enums.Role;
import com.riseai.exception.ErrorCode;
import com.riseai.mapper.RiseEvents;
import com.riseai.repository.FindingOccurrenceRepository;
import com.riseai.repository.MonitoringFindingRepository;
import com.riseai.repository.OutboxEventRepository;
import com.riseai.security.AuthUser;
import com.riseai.service.AlertDispatcher;
import com.riseai.service.AuthService;
import com.riseai.service.FindingService;
import com.riseai.service.OutboxRelay;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OutboxRelayIntegrationTest extends AbstractMySqlIntegrationTest {
    private static final String SERVICE_KEY = "test-service-key";
    private static final HttpServer MONITOR;
    private static final List<Captured> REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer ALERTS;
    private static final List<JsonNode> ALERT_BODIES = new CopyOnWriteArrayList<>();
    private static final List<String> ALERT_AUTHORIZATIONS = new CopyOnWriteArrayList<>();
    private static final String ALERT_TOKEN = "alert-token-test";
    private static volatile Function<JsonNode, StubResponse> responder = request -> StubResponse.ok(request, List.of());

    static {
        try {
            MONITOR = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ALERTS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        ObjectMapper mapper = new ObjectMapper();
        MONITOR.createContext("/api/v1/events/monitor", exchange -> {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            REQUESTS.add(new Captured(body, exchange.getRequestHeaders().getFirst("Authorization")));
            StubResponse response = responder.apply(body);
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        MONITOR.start();
        ALERTS.createContext("/alerts", exchange -> {
            ALERT_AUTHORIZATIONS.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            ALERT_BODIES.add(mapper.readTree(exchange.getRequestBody()));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        ALERTS.start();
    }

    @DynamicPropertySource
    static void monitor(DynamicPropertyRegistry registry) {
        registry.add("rise.monitoring.base-url", () -> "http://127.0.0.1:" + MONITOR.getAddress().getPort());
        registry.add("rise.monitoring.service-key", () -> SERVICE_KEY);
        registry.add("rise.monitoring.max-attempts", () -> "3");
        registry.add("rise.monitoring.alert-dispatch-interval", () -> "PT1H");
        registry.add("rise.monitoring.alert-dispatch-initial-delay", () -> "PT1H");
        registry.add("rise.monitoring.alert-webhook-token", () -> ALERT_TOKEN);
        registry.add("rise.monitoring.alert-webhook-url", () -> "http://127.0.0.1:" + ALERTS.getAddress().getPort() + "/alerts");
    }

    @Autowired
    OutboxRelay outboxRelay;
    @Autowired
    AlertDispatcher alertDispatcher;
    @Autowired
    OutboxEventRepository outboxEventRepository;
    @Autowired
    MonitoringFindingRepository findingRepository;
    @Autowired
    FindingOccurrenceRepository occurrenceRepository;
    @Autowired
    FindingService findingService;
    @Autowired
    AuthService authService;
    @Autowired
    MockMvc mvc;

    AuthUser creator;
    AuthUser admin;
    AuthUser reviewer;
    AuthUser donor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("update outbox_events set status = 'PUBLISHED', published_at = utc_timestamp(6), lease_until = null where status <> 'PUBLISHED'");
        jdbcTemplate.update("delete from alert_notifications");
        REQUESTS.clear();
        ALERT_BODIES.clear();
        ALERT_AUTHORIZATIONS.clear();
        responder = request -> StubResponse.ok(request, List.of());
        creator = user(Role.CAMPAIGN_CREATOR);
        admin = user(Role.ADMIN);
        reviewer = user(Role.ADMIN);
        donor = user(Role.DONOR);
    }

    @Test
    void deliversMappedEventsWithServiceKeyAndMarksThemPublished() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 5_000_000);
        UUID campaignId = campaign.id();

        OutboxRelay.RelayRun run = outboxRelay.relayOnce();

        assertThat(run.published()).isEqualTo(run.claimed()).isGreaterThanOrEqualTo(4);
        assertThat(REQUESTS).allSatisfy(captured -> assertThat(captured.authorization()).isEqualTo("Bearer " + SERVICE_KEY));
        JsonNode created = REQUESTS.stream().map(Captured::body)
                .filter(body -> body.path("eventType").asText().equals(RiseEvents.CAMPAIGN_CREATED)
                        && body.path("payload").path("aggregateId").asText().equals(campaignId.toString()))
                .findFirst().orElseThrow();
        assertThat(created.path("source").asText()).isEqualTo("rise-ai-backend");
        assertThat(created.path("attributes").path("targetAmount").asText()).isEqualTo("5000000.00");
        assertThat(created.path("attributes").path("actorRole").asText()).isEqualTo("CAMPAIGN_CREATOR");
        assertThat(created.path("attributes").has("campaignId")).isFalse();
        assertThat(created.path("attributes").has("creatorId")).isFalse();
        assertThat(created.path("payload").path("payload").path("campaignId").asText()).isEqualTo(campaignId.toString());
        assertThat(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).isZero();
        assertThat(outboxEventRepository.countByStatus(OutboxStatus.IN_FLIGHT)).isZero();
    }

    @Test
    void twoConcurrentRelaysNeverDeliverTheSameEventTwice() throws Exception {
        for (int i = 0; i < 6; i++) {
            activeCampaign(creator, admin, 1_000_000 + i);
        }
        long pending = outboxEventRepository.countByStatus(OutboxStatus.PENDING);
        assertThat(pending).isGreaterThan(20);

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<OutboxRelay.RelayRun>> runs = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            runs.add(pool.submit(() -> {
                start.await();
                OutboxRelay.RelayRun total = new OutboxRelay.RelayRun(0, 0, 0, 0);
                for (int round = 0; round < 4; round++) {
                    OutboxRelay.RelayRun run = outboxRelay.relayOnce();
                    total = new OutboxRelay.RelayRun(total.claimed() + run.claimed(), total.published() + run.published(),
                            total.retried() + run.retried(), total.failed() + run.failed());
                }
                return total;
            }));
        }
        start.countDown();
        long claimed = 0;
        for (java.util.concurrent.Future<OutboxRelay.RelayRun> run : runs) {
            claimed += run.get(60, java.util.concurrent.TimeUnit.SECONDS).claimed();
        }
        pool.shutdown();

        List<String> delivered = REQUESTS.stream().map(captured -> captured.body().path("eventId").asText()).toList();
        assertThat(delivered).doesNotHaveDuplicates().hasSize((int) pending);
        assertThat(claimed).isEqualTo(pending);
        assertThat(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).isZero();
        assertThat(outboxEventRepository.countByStatus(OutboxStatus.IN_FLIGHT)).isZero();
    }

    @Test
    void criticalFindingRaisesOneAlertAndRepeatsDoNot() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 3_000_000);
        String key = "c".repeat(64);
        responder = request -> StubResponse.ok(request, request.path("eventType").asText().equals(RiseEvents.CAMPAIGN_APPROVED)
                ? List.of(finding(key, "CRITICAL", campaign.id())) : List.of());
        outboxRelay.relayOnce();
        awaitAlerts(1);
        assertThat(ALERT_BODIES).hasSize(1);
        assertThat(ALERT_BODIES.get(0).path("kind").asText()).isEqualTo("CRITICAL_FINDING");
        assertThat(ALERT_BODIES.get(0).path("details").path("ruleId").asText()).isEqualTo("test-rule");

        OutboxEvent approved = outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("CAMPAIGN", campaign.id()).stream()
                .filter(event -> event.getEventType().equals(RiseEvents.CAMPAIGN_APPROVED)).findFirst().orElseThrow();
        jdbcTemplate.update("update outbox_events set status = 'PENDING', next_attempt_at = utc_timestamp(6) where id = ?", approved.getId().toString());
        outboxRelay.relayOnce();
        alertDispatcher.dispatchDue();
        assertThat(ALERT_BODIES).hasSize(1);
        assertThat(ALERT_AUTHORIZATIONS).allMatch(value -> value.equals("Bearer " + ALERT_TOKEN));
    }

    @Test
    void monitoringAndReviewActivityNeverChangeBusinessTables() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 4_000_000);
        outboxRelay.relayOnce();
        Map<String, Long> before = businessChecksums();
        assertThat(before.values()).isNotEmpty();

        String key = "d".repeat(64);
        responder = request -> StubResponse.ok(request, List.of(finding(key, "CRITICAL", campaign.id())));
        jdbcTemplate.update("update outbox_events set status = 'PENDING', next_attempt_at = utc_timestamp(6) where status = 'PUBLISHED'");
        outboxRelay.relayOnce();
        MonitoringFinding stored = findingRepository.findAll().stream().filter(f -> f.getFindingKey().equals(key)).findFirst().orElseThrow();
        findingService.review(reviewer, stored.getId(), FindingReviewStatus.ACKNOWLEDGED, "Looking");
        findingService.review(reviewer, stored.getId(), FindingReviewStatus.CONCERN_CONFIRMED, "Confirmed");

        assertThat(businessChecksums()).isEqualTo(before);
    }

    @Test
    void aiNoteIsStoredPerOccurrenceSanitizedAndNeverChangesTheFinding() throws Exception {
        ActiveCampaign campaign = activeCampaign(creator, admin, 5_000_000);
        String key = "e".repeat(64);
        Map<String, Object> withNote = new java.util.HashMap<>(finding(key, "HIGH", campaign.id()));
        withNote.put("aiNote", "Advisory\u0007 note\nwith control characters");
        responder = request -> StubResponse.ok(request, List.of(withNote));
        outboxRelay.relayOnce();

        MonitoringFinding stored = findingRepository.findAll().stream().filter(f -> f.getFindingKey().equals(key)).findFirst().orElseThrow();
        assertThat(stored.getSeverity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(stored.getExplanation()).isEqualTo("Test pattern requires review");
        assertThat(occurrenceRepository.findByFindingIdOrderByObservedAtAsc(stored.getId()))
                .isNotEmpty()
                .allSatisfy(occurrence -> assertThat(occurrence.getAiNote()).isEqualTo("Advisory note with control characters"));

        mvc.perform(get("/api/v1/admin/monitoring/findings/" + stored.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occurrences[0].aiNote").value("Advisory note with control characters"));
        mvc.perform(get("/api/v1/admin/monitoring/findings/" + stored.getId()).header("Authorization", bearer(donor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void findingWithoutAiNoteStoresNoNoteAndAnOversizedNoteIsTruncated() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 5_100_000);
        String plain = "f".repeat(64);
        String long64 = "0".repeat(64);
        Map<String, Object> oversized = new java.util.HashMap<>(findingUnchecked(long64, "LOW", campaign.id()));
        oversized.put("aiNote", "x".repeat(900));
        responder = request -> StubResponse.ok(request, List.of(finding(plain, "LOW", campaign.id()), oversized));
        outboxRelay.relayOnce();

        MonitoringFinding noNote = findingRepository.findAll().stream().filter(f -> f.getFindingKey().equals(plain)).findFirst().orElseThrow();
        MonitoringFinding longNote = findingRepository.findAll().stream().filter(f -> f.getFindingKey().equals(long64)).findFirst().orElseThrow();
        assertThat(occurrenceRepository.findByFindingIdOrderByObservedAtAsc(noNote.getId())).allSatisfy(o -> assertThat(o.getAiNote()).isNull());
        assertThat(occurrenceRepository.findByFindingIdOrderByObservedAtAsc(longNote.getId()))
                .allSatisfy(o -> assertThat(o.getAiNote()).hasSize(400));
    }

    private Map<String, Long> businessChecksums() {
        Map<String, Long> checksums = new java.util.TreeMap<>();
        for (String table : List.of("organizations", "campaigns", "campaign_milestones", "donations", "escrow_accounts",
                "disbursement_requests", "refund_requests", "escrow_ledger_entries", "evidence_records", "disputes",
                "dispute_messages", "settlement_requests", "escrow_adjustments")) {
            checksums.put(table, jdbcTemplate.queryForObject("checksum table " + table, (rs, row) -> rs.getLong(2)));
        }
        return checksums;
    }

    @Test
    void retryableFailuresBackOffAndPermanentFailuresDeadLetter() {
        responder = request -> new StubResponse(503, "{\"status\":2003,\"error\":\"busy\"}");
        activeCampaign(creator, admin, 1_000_000);

        OutboxRelay.RelayRun first = outboxRelay.relayOnce();
        assertThat(first.retried()).isEqualTo(first.claimed()).isPositive();
        List<OutboxEvent> pending = outboxEventRepository.findByStatus(OutboxStatus.PENDING, org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertThat(pending).isNotEmpty().allSatisfy(event -> {
            assertThat(event.getAttempts()).isEqualTo(1);
            assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
            assertThat(event.getLastError()).contains("2003");
        });
        assertThat(outboxRelay.relayOnce().claimed()).isZero();

        jdbcTemplate.update("update outbox_events set next_attempt_at = utc_timestamp(6) where status = 'PENDING'");
        responder = request -> new StubResponse(400, "{\"status\":1000,\"error\":\"bad\"}");
        OutboxRelay.RelayRun second = outboxRelay.relayOnce();
        assertThat(second.failed()).isEqualTo(second.claimed()).isPositive();
        assertThat(outboxEventRepository.countByStatus(OutboxStatus.FAILED)).isGreaterThanOrEqualTo(second.failed());
        awaitAlerts(second.failed());
        assertThat(ALERT_BODIES).hasSize(second.failed()).allSatisfy(alert -> {
            assertThat(alert.path("kind").asText()).isEqualTo("OUTBOX_EVENT_FAILED");
            assertThat(alert.path("details").has("eventId")).isTrue();
        });

        OutboxEvent failed = outboxEventRepository.findByStatus(OutboxStatus.FAILED, org.springframework.data.domain.Pageable.unpaged()).getContent().get(0);
        findingService.requeue(admin, failed.getId(), "Monitor fixed");
        responder = request -> StubResponse.ok(request, List.of());
        assertThat(outboxRelay.relayOnce().published()).isGreaterThanOrEqualTo(1);
        assertThat(outboxEventRepository.findById(failed.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(auditLogRepository.findAll()).anyMatch(log -> log.getAction().equals("OUTBOX_EVENT_REQUEUED") && failed.getId().equals(log.getEntityId()));
    }

    @Test
    void findingsAreStoredIdempotentlyAndEscalationReopensDismissedFindings() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 2_000_000);
        String key = "b".repeat(64);
        responder = request -> StubResponse.ok(request, request.path("eventType").asText().equals(RiseEvents.CAMPAIGN_APPROVED)
                ? List.of(finding(key, "MEDIUM", campaign.id())) : List.of());

        outboxRelay.relayOnce();

        MonitoringFinding stored = findingRepository.findAll().stream().filter(f -> f.getFindingKey().equals(key)).findFirst().orElseThrow();
        assertThat(stored.getSeverity()).isEqualTo(FindingSeverity.MEDIUM);
        assertThat(stored.getReviewStatus()).isEqualTo(FindingReviewStatus.OPEN);
        assertThat(stored.getOccurrenceCount()).isEqualTo(1);
        assertThat(stored.getCampaignId()).isEqualTo(campaign.id());

        OutboxEvent approved = outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("CAMPAIGN", campaign.id()).stream()
                .filter(event -> event.getEventType().equals(RiseEvents.CAMPAIGN_APPROVED)).findFirst().orElseThrow();
        jdbcTemplate.update("update outbox_events set status = 'PENDING', next_attempt_at = utc_timestamp(6) where id = ?", approved.getId().toString());
        outboxRelay.relayOnce();
        assertThat(findingRepository.findById(stored.getId()).orElseThrow().getOccurrenceCount()).isEqualTo(1);
        assertThat(occurrenceRepository.findByFindingIdOrderByObservedAtAsc(stored.getId())).hasSize(1);

        RulePrecisionResponse before = rulePrecision("test-rule");
        assertError(() -> findingService.review(admin, stored.getId(), FindingReviewStatus.DISMISSED, "I approved it"), ErrorCode.CONFLICT_OF_INTEREST);
        assertError(() -> findingService.review(creator, stored.getId(), FindingReviewStatus.DISMISSED, "Mine"), ErrorCode.ACCESS_DENIED);
        FindingResponse dismissed = findingService.review(reviewer, stored.getId(), FindingReviewStatus.DISMISSED, "Expected pattern");
        assertThat(dismissed.reviewStatus()).isEqualTo(FindingReviewStatus.DISMISSED);
        assertThat(dismissed.advisoryOnly()).isTrue();
        RulePrecisionResponse after = rulePrecision("test-rule");
        assertThat(after.dismissed()).isEqualTo(before.dismissed() + 1);
        assertThat(after.open()).isEqualTo(before.open() - 1);
        assertThat(after.total()).isEqualTo(before.total());
        assertThat(after.precision()).isEqualTo(java.math.BigDecimal.valueOf(after.concernConfirmed())
                .divide(java.math.BigDecimal.valueOf(after.concernConfirmed() + after.dismissed()), 4, java.math.RoundingMode.HALF_UP));
        assertError(() -> findingService.review(reviewer, stored.getId(), FindingReviewStatus.ACKNOWLEDGED, "Again"), ErrorCode.INVALID_STATE_TRANSITION);

        responder = request -> StubResponse.ok(request, List.of(finding(key, "HIGH", campaign.id())));
        campaignService.pause(admin, campaign.id(), "Investigating");
        outboxRelay.relayOnce();

        MonitoringFinding reopened = findingRepository.findById(stored.getId()).orElseThrow();
        assertThat(reopened.getSeverity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(reopened.getReviewStatus()).isEqualTo(FindingReviewStatus.OPEN);
        assertThat(reopened.getOccurrenceCount()).isEqualTo(2);
        assertThat(findingService.history(stored.getId())).extracting("toStatus").containsExactly("DISMISSED", "OPEN");
    }

    @Test
    void invalidFindingContractDeadLettersTheEventWithoutStoringAnything() {
        activeCampaign(creator, admin, 1_000_000);
        long before = findingRepository.count();
        responder = request -> StubResponse.ok(request, List.of(Map.of("contractVersion", "finding.v1", "findingId", "not-a-key")));
        assertThat(FindingSchemaContract.validate(new ObjectMapper().valueToTree(Map.of("contractVersion", "finding.v1", "findingId", "not-a-key")))).isNotEmpty();

        OutboxRelay.RelayRun run = outboxRelay.relayOnce();

        assertThat(run.failed()).isEqualTo(run.claimed()).isPositive();
        assertThat(findingRepository.count()).isEqualTo(before);
        assertThat(outboxEventRepository.findByStatus(OutboxStatus.FAILED, org.springframework.data.domain.Pageable.unpaged()).getContent())
                .anyMatch(event -> event.getLastError().startsWith("Invalid finding contract"));
    }

    @Test
    void noKeyTokenPayloadOrMonitorResponseIsLoggedAtAnyLevel() {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Logger application = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("com.riseai");
        ch.qos.logback.classic.Level previous = application.getLevel();
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        root.addAppender(appender);
        application.setLevel(ch.qos.logback.classic.Level.TRACE);
        String leakedBody = "LEAKED-RESPONSE-" + UUID.randomUUID();
        try {
            ActiveCampaign campaign = activeCampaign(creator, admin, 1_000_000);
            String key = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            responder = request -> switch (calls.incrementAndGet() % 3) {
                case 0 -> new StubResponse(400, "{\"error\":\"" + leakedBody + "\"}");
                case 1 -> new StubResponse(503, leakedBody);
                default -> StubResponse.ok(request, List.of(finding(key, "HIGH", campaign.id())));
            };
            for (int round = 0; round < 3; round++) {
                outboxRelay.relayOnce();
            }
        } finally {
            root.detachAppender(appender);
            application.setLevel(previous);
        }
        assertThat(REQUESTS).isNotEmpty();
        List<String> payloads = REQUESTS.stream().map(captured -> captured.body().path("payload").toString()).distinct().toList();
        List<String> lines = appender.list.stream().map(event -> event.getFormattedMessage() + " "
                + (event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage())).toList();
        assertThat(lines).isNotEmpty();
        assertThat(lines).noneMatch(line -> line.contains(SERVICE_KEY) || line.contains(ALERT_TOKEN) || line.contains("Bearer ")
                || line.contains(leakedBody) || line.contains("Test pattern requires review"));
        assertThat(lines).noneMatch(line -> payloads.stream().anyMatch(payload -> payload.length() > 20 && line.contains(payload)));
        assertThat(lines).noneMatch(line -> line.contains("\"payload\""));
    }

    @Test
    void findingEndpointsAreAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/admin/monitoring/findings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/monitoring/findings").header("Authorization", bearer(creator))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/monitoring/findings").header("Authorization", bearer(donor))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/monitoring/findings").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/monitoring/outbox/stats").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/monitoring/findings/precision").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/monitoring/findings/precision").header("Authorization", bearer(creator))).andExpect(status().isForbidden());
    }

    private void awaitAlerts(int expected) {
        alertDispatcher.dispatchDue();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() -> ALERT_BODIES.size() >= expected);
    }

    private RulePrecisionResponse rulePrecision(String ruleId) {
        return findingService.rulePrecision().stream().filter(row -> row.ruleId().equals(ruleId)).findFirst()
                .orElse(new RulePrecisionResponse(ruleId, 0, 0, 0, 0, 0, null));
    }

    private String bearer(AuthUser user) {
        return "Bearer " + authService.login(new LoginRequest(user.email(), PASSWORD)).accessToken();
    }

    private static Map<String, Object> finding(String key, String severity, UUID campaignId) {
        Map<String, Object> finding = findingUnchecked(key, severity, campaignId);
        assertThat(FindingSchemaContract.validate(new ObjectMapper().valueToTree(finding))).isEmpty();
        return finding;
    }

    private static Map<String, Object> findingUnchecked(String key, String severity, UUID campaignId) {
        return Map.ofEntries(Map.entry("findingId", key), Map.entry("sourceEventIds", List.of(UUID.randomUUID().toString())), Map.entry("contractVersion", "finding.v1"),
                Map.entry("detector", Map.of("type", "RULE", "id", "test-rule", "version", "1")),
                Map.entry("category", "DISBURSEMENT_PATTERN"), Map.entry("severity", severity),
                Map.entry("subject", Map.of("type", "CAMPAIGN", "id", campaignId.toString())),
                Map.entry("references", Map.of("campaignId", campaignId.toString())),
                Map.entry("facts", List.of(Map.of("name", "count", "value", 3, "sourceEventId", UUID.randomUUID().toString()))),
                Map.entry("explanation", "Test pattern requires review"), Map.entry("recommendedAction", "REVIEW_EVIDENCE"),
                Map.entry("observedAt", Instant.now().toString()));
    }

    private record Captured(JsonNode body, String authorization) {
    }

    private record StubResponse(int status, String body) {
        static StubResponse ok(JsonNode request, List<Map<String, Object>> findings) {
            try {
                Map<String, Object> data = Map.of("eventId", request.path("eventId").asText(), "monitoringStatus", "COMPLETED",
                        "duplicate", false, "findings", findings);
                return new StubResponse(200, new ObjectMapper().writeValueAsString(Map.of("code", 200, "message", "Event processed", "data", data)));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }
}
