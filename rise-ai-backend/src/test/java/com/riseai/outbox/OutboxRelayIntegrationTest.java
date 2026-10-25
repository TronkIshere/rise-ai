package com.riseai.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.common.exception.ErrorCode;
import com.riseai.finding.FindingDtos.FindingResponse;
import com.riseai.finding.FindingOccurrenceRepository;
import com.riseai.finding.FindingReviewStatus;
import com.riseai.finding.FindingService;
import com.riseai.finding.FindingSeverity;
import com.riseai.finding.MonitoringFinding;
import com.riseai.finding.MonitoringFindingRepository;
import com.riseai.security.AuthDtos.LoginRequest;
import com.riseai.security.AuthService;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OutboxRelayIntegrationTest extends AbstractMySqlIntegrationTest {
    private static final String SERVICE_KEY = "test-service-key";
    private static final HttpServer MONITOR;
    private static final List<Captured> REQUESTS = new CopyOnWriteArrayList<>();
    private static volatile Function<JsonNode, StubResponse> responder = request -> StubResponse.ok(request, List.of());

    static {
        try {
            MONITOR = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
    }

    @DynamicPropertySource
    static void monitor(DynamicPropertyRegistry registry) {
        registry.add("rise.monitoring.base-url", () -> "http://127.0.0.1:" + MONITOR.getAddress().getPort());
        registry.add("rise.monitoring.service-key", () -> SERVICE_KEY);
        registry.add("rise.monitoring.max-attempts", () -> "3");
    }

    @Autowired
    OutboxRelay outboxRelay;
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
        REQUESTS.clear();
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

        assertError(() -> findingService.review(admin, stored.getId(), FindingReviewStatus.DISMISSED, "I approved it"), ErrorCode.CONFLICT_OF_INTEREST);
        assertError(() -> findingService.review(creator, stored.getId(), FindingReviewStatus.DISMISSED, "Mine"), ErrorCode.ACCESS_DENIED);
        FindingResponse dismissed = findingService.review(reviewer, stored.getId(), FindingReviewStatus.DISMISSED, "Expected pattern");
        assertThat(dismissed.reviewStatus()).isEqualTo(FindingReviewStatus.DISMISSED);
        assertThat(dismissed.advisoryOnly()).isTrue();
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

        OutboxRelay.RelayRun run = outboxRelay.relayOnce();

        assertThat(run.failed()).isEqualTo(run.claimed()).isPositive();
        assertThat(findingRepository.count()).isEqualTo(before);
        assertThat(outboxEventRepository.findByStatus(OutboxStatus.FAILED, org.springframework.data.domain.Pageable.unpaged()).getContent())
                .anyMatch(event -> event.getLastError().startsWith("Invalid finding contract"));
    }

    @Test
    void findingEndpointsAreAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/admin/monitoring/findings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/monitoring/findings").header("Authorization", bearer(creator))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/monitoring/findings").header("Authorization", bearer(donor))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/monitoring/findings").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/monitoring/outbox/stats").header("Authorization", bearer(admin))).andExpect(status().isOk());
    }

    private String bearer(AuthUser user) {
        return "Bearer " + authService.login(new LoginRequest(user.email(), PASSWORD)).accessToken();
    }

    private static Map<String, Object> finding(String key, String severity, UUID campaignId) {
        return Map.ofEntries(Map.entry("findingId", key), Map.entry("contractVersion", "finding.v1"),
                Map.entry("detector", Map.of("type", "RULE", "id", "test-rule", "version", "1")),
                Map.entry("category", "DISBURSEMENT_PATTERN"), Map.entry("severity", severity),
                Map.entry("subject", Map.of("type", "CAMPAIGN", "id", campaignId.toString())),
                Map.entry("references", Map.of("campaignId", campaignId.toString())),
                Map.entry("facts", List.of(Map.of("name", "count", "value", 3))),
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
