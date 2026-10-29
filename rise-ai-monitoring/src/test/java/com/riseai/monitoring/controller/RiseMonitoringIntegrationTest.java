package com.riseai.monitoring.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.rise.RiseFindingEngine;
import com.riseai.monitoring.rise.rule.DuplicateEvidenceHashRule;
import com.riseai.monitoring.rise.rule.FinancialEvidenceWithoutHashRule;
import com.riseai.monitoring.rise.rule.ManualAdjustmentRule;
import com.riseai.monitoring.rise.rule.MinimalEvidenceApprovalRule;
import com.riseai.monitoring.rise.rule.MisuseDisputeAfterReleaseRule;
import com.riseai.monitoring.rise.rule.RapidResubmissionRule;
import com.riseai.monitoring.rise.rule.ReconciliationInconsistencyRule;
import com.riseai.monitoring.rise.rule.RepeatedRequestsRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"monitoring.security.service-key=" + RiseMonitoringIntegrationTest.KEY, "monitoring.ai.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RiseMonitoringIntegrationTest {
    static final String KEY = "rise-test-key";
    private static final String MONITOR = "/api/v1/events/monitor";
    private static final List<String> FORBIDDEN_TERMS = List.of("fraud", "theft", "stole", "criminal", "misuse confirmed", "definitely", "suspicious person");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    RiseFindingEngine engine;
    @Autowired
    JdbcClient jdbcClient;

    private final List<JsonNode> allFindings = new ArrayList<>();
    private Instant clock = Instant.now().minus(2, ChronoUnit.HOURS);

    @Test
    void serviceKeyIsRequiredForApiAndMetricsButNotHealth() throws Exception {
        Map<String, Object> request = request("RECONCILIATION_RUN_COMPLETED", "RECONCILIATION_RUN", Map.of(),
                Map.of("runId", id(), "runStatus", "CONSISTENT"));
        mockMvc.perform(post(MONITOR).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(1006));
        mockMvc.perform(post(MONITOR).header("Authorization", "Bearer wrong").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/monitoring/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + KEY)).andExpect(status().isOk());
    }

    @Test
    void duplicateEvidenceHashAcrossCampaignsIsHighAndRedeliveryIsIdempotent() throws Exception {
        String hash = hash();
        String campaignA = id();
        String campaignB = id();
        JsonNode first = monitor(evidence(campaignA, id(), id(), hash, "INVOICE"));
        assertThat(rules(first)).doesNotContain(DuplicateEvidenceHashRule.ID);

        Map<String, Object> second = evidence(campaignB, id(), id(), hash, "INVOICE");
        JsonNode result = monitor(second);
        JsonNode finding = finding(result, DuplicateEvidenceHashRule.ID);
        assertThat(finding.path("severity").asText()).isEqualTo("HIGH");
        assertThat(finding.path("category").asText()).isEqualTo("EVIDENCE_INTEGRITY");
        assertThat(finding.path("contractVersion").asText()).isEqualTo("finding.v1");
        assertThat(finding.path("findingId").asText()).matches("^[0-9a-f]{64}$");
        assertThat(finding.path("references").path("campaignId").asText()).isEqualTo(campaignB);
        assertThat(result.path("overallRiskLevel").asText()).isEqualTo("HIGH");
        assertThat(result.path("reviewRequired").asBoolean()).isTrue();

        JsonNode replay = monitor(second);
        assertThat(replay.path("duplicate").asBoolean()).isTrue();
        assertThat(finding(replay, DuplicateEvidenceHashRule.ID).path("findingId").asText()).isEqualTo(finding.path("findingId").asText());

        JsonNode sameCampaign = monitor(evidence(campaignA, id(), id(), hash, "RECEIPT"));
        assertThat(finding(sameCampaign, DuplicateEvidenceHashRule.ID).path("severity").asText()).isEqualTo("HIGH");
    }

    @Test
    void reevaluatingAfterLostDeduplicationProducesTheSameFindingsWithoutNewProjectionRows() throws Exception {
        String hash = hash();
        String campaign = id();
        monitor(evidence(campaign, id(), id(), hash, "INVOICE"));
        Map<String, Object> second = evidence(campaign, id(), id(), hash, "INVOICE");
        monitor(second);
        long rows = jdbcClient.sql("select count(*) from rise_evidence_hashes where file_hash = ?").param(hash).query(Long.class).single();

        MonitoringEvent event = MonitoringEvent.builder()
                .eventId((String) second.get("eventId"))
                .eventType(((String) second.get("eventType")).toLowerCase(Locale.ROOT))
                .source("rise-ai-backend")
                .occurredAt(Instant.parse((String) second.get("occurredAt")))
                .payload(objectMapper.valueToTree(second.get("payload")))
                .build();
        List<Finding> again = engine.evaluate(event);

        assertThat(again).extracting(Finding::getRuleId).contains(DuplicateEvidenceHashRule.ID);
        assertThat(again.get(0).getSeverity().name()).isEqualTo("MEDIUM");
        assertThat(jdbcClient.sql("select count(*) from rise_evidence_hashes where file_hash = ?").param(hash).query(Long.class).single())
                .isEqualTo(rows);
    }

    @Test
    void financialEvidenceWithoutHashIsLowDataQuality() throws Exception {
        JsonNode result = monitor(evidence(id(), id(), id(), null, "RECEIPT"));
        JsonNode finding = finding(result, FinancialEvidenceWithoutHashRule.ID);
        assertThat(finding.path("severity").asText()).isEqualTo("LOW");
        assertThat(finding.path("category").asText()).isEqualTo("DATA_QUALITY");

        assertThat(rules(monitor(evidence(id(), id(), id(), null, "PHOTO")))).isEmpty();
    }

    @Test
    void rapidResubmissionAndRepeatedRequestsAreFlaggedOnlyInCausalOrder() throws Exception {
        String campaign = id();
        String milestone = id();
        monitor(disbursement("DISBURSEMENT_REQUESTED", campaign, milestone, id(), "1000000.00", Map.of()));
        monitor(disbursement("DISBURSEMENT_REJECTED", campaign, milestone, id(), "1000000.00", Map.of()));
        JsonNode second = monitor(disbursement("DISBURSEMENT_REQUESTED", campaign, milestone, id(), "1000000.00", Map.of()));
        assertThat(finding(second, RapidResubmissionRule.ID).path("severity").asText()).isEqualTo("MEDIUM");
        assertThat(rules(second)).doesNotContain(RepeatedRequestsRule.ID);
        JsonNode third = monitor(disbursement("DISBURSEMENT_REQUESTED", campaign, milestone, id(), "500000.00", Map.of()));
        JsonNode repeated = finding(third, RepeatedRequestsRule.ID);
        assertThat(repeated.path("severity").asText()).isEqualTo("LOW");
        assertThat(repeated.path("subject").path("type").asText()).isEqualTo("MILESTONE");

        String otherMilestone = id();
        Instant rejectionTime = next();
        Instant requestTime = next();
        clock = requestTime.minusSeconds(1);
        JsonNode requestDeliveredFirst = monitor(disbursement("DISBURSEMENT_REQUESTED", campaign, otherMilestone, id(), "1.00", Map.of()));
        clock = rejectionTime.minusSeconds(1);
        JsonNode rejectionDeliveredLate = monitor(disbursement("DISBURSEMENT_REJECTED", campaign, otherMilestone, id(), "1.00", Map.of()));
        clock = requestTime;
        assertThat(rules(requestDeliveredFirst)).doesNotContain(RapidResubmissionRule.ID);
        assertThat(rules(rejectionDeliveredLate)).isEmpty();
    }

    @Test
    void minimalEvidenceApprovalDependsOnAmountAndEvidenceCount() throws Exception {
        JsonNode large = monitor(disbursement("DISBURSEMENT_APPROVED", id(), id(), id(), "60000000.00", Map.of("acceptedEvidenceCount", 1)));
        assertThat(finding(large, MinimalEvidenceApprovalRule.ID).path("severity").asText()).isEqualTo("MEDIUM");
        assertThat(rules(monitor(disbursement("DISBURSEMENT_APPROVED", id(), id(), id(), "1000000.00", Map.of("acceptedEvidenceCount", 1)))))
                .doesNotContain(MinimalEvidenceApprovalRule.ID);
        assertThat(rules(monitor(disbursement("DISBURSEMENT_APPROVED", id(), id(), id(), "60000000.00", Map.of("acceptedEvidenceCount", 3)))))
                .doesNotContain(MinimalEvidenceApprovalRule.ID);
    }

    @Test
    void reconciliationInconsistencyIsCriticalAndConsistentRunIsQuiet() throws Exception {
        JsonNode inconsistent = monitor(request("RECONCILIATION_RUN_COMPLETED", "RECONCILIATION_RUN", Map.of(),
                Map.of("runId", id(), "runCode", "REC-TEST", "runStatus", "INCONSISTENT", "accountsChecked", 4, "inconsistentAccounts", 1)));
        JsonNode finding = finding(inconsistent, ReconciliationInconsistencyRule.ID);
        assertThat(finding.path("severity").asText()).isEqualTo("CRITICAL");
        assertThat(finding.path("recommendedAction").asText()).isEqualTo("CHECK_RECONCILIATION");
        assertThat(inconsistent.path("overallRiskLevel").asText()).isEqualTo("CRITICAL");

        JsonNode consistent = monitor(request("RECONCILIATION_RUN_COMPLETED", "RECONCILIATION_RUN", Map.of(),
                Map.of("runId", id(), "runStatus", "CONSISTENT", "accountsChecked", 4, "inconsistentAccounts", 0)));
        assertThat(rules(consistent)).isEmpty();
        assertThat(consistent.path("overallRiskLevel").asText()).isEqualTo("LOW");
        assertThat(consistent.path("monitoringStatus").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void manualAdjustmentSeverityFollowsTheReviewAmount() throws Exception {
        String campaign = id();
        JsonNode large = monitor(request("ADJUSTMENT_APPLIED", "ESCROW_ADJUSTMENT", Map.of("campaignId", campaign),
                Map.of("adjustmentId", id(), "campaignId", campaign, "direction", "DEBIT", "amount", "20000000.00", "currency", "VND")));
        assertThat(finding(large, ManualAdjustmentRule.ID).path("severity").asText()).isEqualTo("MEDIUM");
        JsonNode small = monitor(request("ADJUSTMENT_APPLIED", "ESCROW_ADJUSTMENT", Map.of("campaignId", campaign),
                Map.of("adjustmentId", id(), "campaignId", campaign, "direction", "CREDIT", "amount", "100000.00", "currency", "VND")));
        JsonNode info = finding(small, ManualAdjustmentRule.ID);
        assertThat(info.path("severity").asText()).isEqualTo("INFO");
        assertThat(info.path("recommendedAction").asText()).isEqualTo("NONE_INFORMATIONAL");
    }

    @Test
    void misuseDisputeIsHighOnlyAfterFundsWereReleased() throws Exception {
        String campaign = id();
        String released = id();
        monitor(disbursement("DISBURSEMENT_RELEASED", campaign, released, id(), "3000000.00", Map.of("simulated", true)));
        JsonNode afterRelease = monitor(dispute(campaign, released, "MISUSE_OF_FUNDS"));
        JsonNode finding = finding(afterRelease, MisuseDisputeAfterReleaseRule.ID);
        assertThat(finding.path("severity").asText()).isEqualTo("HIGH");
        assertThat(finding.path("facts").toString()).contains("3000000.00");

        assertThat(rules(monitor(dispute(campaign, id(), "MISUSE_OF_FUNDS")))).isEmpty();
        assertThat(rules(monitor(dispute(campaign, released, "MISSING_EVIDENCE")))).isEmpty();
    }

    @Test
    void malformedRiseEnvelopesAreRejectedWithInvalidEvent() throws Exception {
        Map<String, Object> wrongVersion = request("CAMPAIGN_CREATED", "CAMPAIGN", Map.of(), Map.of("campaignId", id()));
        castPayload(wrongVersion).put("schemaVersion", 2);
        expectInvalid(wrongVersion);

        Map<String, Object> mismatched = request("CAMPAIGN_CREATED", "CAMPAIGN", Map.of(), Map.of("campaignId", id()));
        castPayload(mismatched).put("eventId", id());
        expectInvalid(mismatched);

        Map<String, Object> noEnvelope = request("CAMPAIGN_CREATED", "CAMPAIGN", Map.of(), Map.of("campaignId", id()));
        noEnvelope.remove("payload");
        expectInvalid(noEnvelope);
    }

    @Test
    void findingTextsAvoidAccusatoryLanguage() throws Exception {
        duplicateEvidenceHashAcrossCampaignsIsHighAndRedeliveryIsIdempotent();
        financialEvidenceWithoutHashIsLowDataQuality();
        minimalEvidenceApprovalDependsOnAmountAndEvidenceCount();
        reconciliationInconsistencyIsCriticalAndConsistentRunIsQuiet();
        manualAdjustmentSeverityFollowsTheReviewAmount();
        misuseDisputeIsHighOnlyAfterFundsWereReleased();
        rapidResubmissionAndRepeatedRequestsAreFlaggedOnlyInCausalOrder();

        assertThat(allFindings).hasSizeGreaterThanOrEqualTo(8);
        for (JsonNode finding : allFindings) {
            String text = finding.path("explanation").asText().toLowerCase(Locale.ROOT);
            assertThat(FORBIDDEN_TERMS).noneMatch(text::contains);
        }
    }

    private void expectInvalid(Map<String, Object> request) throws Exception {
        mockMvc.perform(post(MONITOR).header("Authorization", "Bearer " + KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(2000));
    }

    private JsonNode monitor(Map<String, Object> request) throws Exception {
        MvcResult result = mockMvc.perform(post(MONITOR).header("Authorization", "Bearer " + KEY).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("monitoringStatus").asText()).isNotEqualTo("REJECTED");
        data.path("findings").forEach(allFindings::add);
        return data;
    }

    private static List<String> rules(JsonNode result) {
        List<String> rules = new ArrayList<>();
        result.path("findings").forEach(finding -> rules.add(finding.path("detector").path("id").asText()));
        return rules;
    }

    private static JsonNode finding(JsonNode result, String ruleId) {
        for (JsonNode finding : result.path("findings")) {
            if (finding.path("detector").path("id").asText().equals(ruleId)) {
                return finding;
            }
        }
        throw new AssertionError("No finding from " + ruleId + " in " + result.path("findings"));
    }

    @Test
    void concurrentIdenticalRequestsProduceOneAnalysisAndNoDuplicateProjectionRows() throws Exception {
        Map<String, Object> event = evidence(id(), id(), id(), hash(), "INVOICE");
        String body = objectMapper.writeValueAsString(event);
        String eventId = (String) event.get("eventId");
        int threads = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<org.springframework.mock.web.MockHttpServletResponse>> calls = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            calls.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(post(MONITOR).header("Authorization", "Bearer " + KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
            }));
        }
        start.countDown();
        int fresh = 0;
        for (java.util.concurrent.Future<org.springframework.mock.web.MockHttpServletResponse> call : calls) {
            org.springframework.mock.web.MockHttpServletResponse response = call.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(response.getStatus()).isIn(200, 409);
            if (response.getStatus() == 200 && !objectMapper.readTree(response.getContentAsString()).path("data").path("duplicate").asBoolean()) {
                fresh++;
            }
        }
        pool.shutdown();
        assertThat(fresh).isEqualTo(1);
        assertThat(jdbcClient.sql("select count(*) from rise_evidence_hashes where event_id = ?").param(eventId).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbcClient.sql("select count(*) from rise_processed_events where event_id = ?").param(eventId).query(Integer.class).single()).isEqualTo(1);
    }

    private Map<String, Object> evidence(String campaignId, String milestoneId, String evidenceId, String hash, String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceId", evidenceId);
        payload.put("milestoneId", milestoneId);
        payload.put("campaignId", campaignId);
        payload.put("evidenceType", type);
        if (hash != null) {
            payload.put("fileHash", hash);
        }
        return request("MILESTONE_EVIDENCE_SUBMITTED", "EVIDENCE", Map.of("campaignId", campaignId, "milestoneId", milestoneId, "evidenceId", evidenceId), payload);
    }

    private Map<String, Object> disbursement(String type, String campaignId, String milestoneId, String requestId, String amount,
                                             Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>(Map.of("requestId", requestId, "milestoneId", milestoneId, "campaignId", campaignId,
                "amount", amount, "currency", "VND"));
        payload.putAll(extra);
        return request(type, "DISBURSEMENT_REQUEST", Map.of("campaignId", campaignId, "milestoneId", milestoneId, "disbursementRequestId", requestId), payload);
    }

    private Map<String, Object> dispute(String campaignId, String milestoneId, String type) {
        String disputeId = id();
        return request("DISPUTE_OPENED", "DISPUTE", Map.of("campaignId", campaignId, "milestoneId", milestoneId),
                Map.of("disputeId", disputeId, "campaignId", campaignId, "milestoneId", milestoneId, "disputeType", type));
    }

    private Map<String, Object> request(String eventType, String aggregateType, Map<String, Object> references, Map<String, Object> payload) {
        String eventId = id();
        String occurredAt = next().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("schemaVersion", 1);
        envelope.put("occurredAt", occurredAt);
        envelope.put("producer", "rise-ai-backend");
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", id());
        envelope.put("actorRole", "ADMIN");
        envelope.put("references", references);
        envelope.put("payload", payload);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("eventId", eventId);
        request.put("eventType", eventType);
        request.put("source", "rise-ai-backend");
        request.put("occurredAt", occurredAt);
        request.put("attributes", Map.of("schemaVersion", 1, "aggregateType", aggregateType));
        request.put("payload", envelope);
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castPayload(Map<String, Object> request) {
        return (Map<String, Object>) request.get("payload");
    }

    private Instant next() {
        clock = clock.plusSeconds(1);
        return clock;
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
