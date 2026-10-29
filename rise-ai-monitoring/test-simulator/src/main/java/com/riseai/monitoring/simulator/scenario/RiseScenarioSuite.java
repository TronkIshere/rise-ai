package com.riseai.monitoring.simulator.scenario;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RiseScenarioSuite {

    public static final String EV_01 = "rise.evidence.duplicate-file-hash";
    public static final String EV_02 = "rise.evidence.financial-without-hash";
    public static final String DB_01 = "rise.disbursement.minimal-evidence-approval";
    public static final String DB_02 = "rise.disbursement.rapid-resubmission";
    public static final String DB_03 = "rise.disbursement.repeated-requests";
    public static final String DP_01 = "rise.dispute.misuse-after-release";
    public static final String AO_01 = "rise.override.manual-adjustment";
    public static final String LG_01 = "rise.ledger.reconciliation-inconsistent";
    public static final List<String> ALL_RULES = List.of(EV_01, EV_02, DB_01, DB_02, DB_03, DP_01, AO_01, LG_01);

    private static final String SOURCE = "rise-ai-backend";
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private final ObjectMapper mapper;
    private final BigDecimal approvalAmount;
    private final BigDecimal adjustmentAmount;
    private final int maxRequests;
    private final Instant base;
    private final List<Scenario> scenarios = new ArrayList<>();
    private int clockStep;

    public RiseScenarioSuite(ObjectMapper mapper, Instant now, BigDecimal approvalAmount, BigDecimal adjustmentAmount, int maxRequests) {
        this.mapper = mapper;
        this.base = now.minus(3, ChronoUnit.HOURS);
        this.approvalAmount = approvalAmount;
        this.adjustmentAmount = adjustmentAmount;
        this.maxRequests = maxRequests;
    }

    public List<Scenario> build() {
        scenarios.clear();
        evidenceDuplicate();
        evidenceWithoutHash();
        reconciliation();
        minimalEvidenceApproval();
        rapidResubmission();
        repeatedRequests();
        manualAdjustment();
        misuseDispute();
        return List.copyOf(scenarios);
    }

    private void evidenceDuplicate() {
        String hash = hash();
        add("EV-01 same hash in two campaigns", "Second submission of the same file hash is HIGH",
                step(evidence(id(), id(), hash, "INVOICE"), none(EV_01)),
                step(evidence(id(), id(), hash, "INVOICE"), finding(EV_01, "HIGH")));
        add("EV-01 unique hash", "A hash seen once raises nothing",
                step(evidence(id(), id(), hash(), "INVOICE"), none(EV_01)));
    }

    private void evidenceWithoutHash() {
        add("EV-02 invoice without hash", "INVOICE without a file hash is LOW", step(evidence(id(), id(), null, "INVOICE"), finding(EV_02, "LOW")));
        add("EV-02 receipt without hash", "RECEIPT without a file hash is LOW", step(evidence(id(), id(), null, "RECEIPT"), finding(EV_02, "LOW")));
        add("EV-02 photo without hash", "A non-financial type without a hash raises nothing", step(evidence(id(), id(), null, "PHOTO"), none(EV_02)));
        add("EV-02 invoice with hash", "A hashed invoice raises nothing", step(evidence(id(), id(), hash(), "INVOICE"), none(EV_02)));
    }

    private void reconciliation() {
        add("LG-01 inconsistent run", "INCONSISTENT reconciliation is CRITICAL", step(reconciliationRun("INCONSISTENT", 1), finding(LG_01, "CRITICAL")));
        add("LG-01 failed run", "FAILED reconciliation is HIGH", step(reconciliationRun("FAILED", 0), finding(LG_01, "HIGH")));
        add("LG-01 consistent run", "CONSISTENT reconciliation raises nothing", step(reconciliationRun("CONSISTENT", 0), none(LG_01)));
    }

    private void minimalEvidenceApproval() {
        add("DB-01 amount at threshold", "Approval at the review amount with one evidence record is MEDIUM",
                step(disbursement("DISBURSEMENT_APPROVED", id(), id(), id(), approvalAmount, Map.of("acceptedEvidenceCount", 1)), finding(DB_01, "MEDIUM")));
        add("DB-01 amount below threshold", "One cent below the review amount raises nothing",
                step(disbursement("DISBURSEMENT_APPROVED", id(), id(), id(), approvalAmount.subtract(CENT), Map.of("acceptedEvidenceCount", 1)), none(DB_01)));
        add("DB-01 more evidence", "A large approval with more than the minimum evidence raises nothing",
                step(disbursement("DISBURSEMENT_APPROVED", id(), id(), id(), approvalAmount, Map.of("acceptedEvidenceCount", 3)), none(DB_01)));
    }

    private void rapidResubmission() {
        String campaign = id();
        String milestone = id();
        add("DB-02 resubmitted within window", "A request shortly after a rejection on the same milestone is MEDIUM",
                step(disbursement("DISBURSEMENT_REQUESTED", campaign, milestone, id(), new BigDecimal("1000000.00"), Map.of()), none(DB_02)),
                step(disbursement("DISBURSEMENT_REJECTED", campaign, milestone, id(), new BigDecimal("1000000.00"), Map.of()), none(DB_02)),
                step(disbursement("DISBURSEMENT_REQUESTED", campaign, milestone, id(), new BigDecimal("1000000.00"), Map.of()), finding(DB_02, "MEDIUM")));
        String otherCampaign = id();
        String otherMilestone = id();
        Instant longAgo = base.minus(2, ChronoUnit.DAYS);
        add("DB-02 rejection outside window", "A rejection older than the window raises nothing",
                step(at(disbursement("DISBURSEMENT_REJECTED", otherCampaign, otherMilestone, id(), new BigDecimal("1000000.00"), Map.of()), longAgo), none(DB_02)),
                step(disbursement("DISBURSEMENT_REQUESTED", otherCampaign, otherMilestone, id(), new BigDecimal("1000000.00"), Map.of()), none(DB_02)));
    }

    private void repeatedRequests() {
        String campaign = id();
        String milestone = id();
        List<Step> steps = new ArrayList<>();
        for (int i = 1; i <= maxRequests; i++) {
            steps.add(step(disbursement("DISBURSEMENT_REQUESTED", campaign, milestone, id(), new BigDecimal("500000.00"), Map.of()),
                    i == maxRequests ? finding(DB_03, "LOW") : none(DB_03)));
        }
        add("DB-03 request count reaches the limit", "The request that reaches the per-milestone limit is LOW", steps.toArray(Step[]::new));
    }

    private void manualAdjustment() {
        add("AO-01 adjustment at review amount", "A manual adjustment at the review amount is MEDIUM",
                step(adjustment(adjustmentAmount), finding(AO_01, "MEDIUM")));
        add("AO-01 adjustment below review amount", "A smaller adjustment is recorded as INFO",
                step(adjustment(adjustmentAmount.subtract(CENT)), finding(AO_01, "INFO")));
    }

    private void misuseDispute() {
        String campaign = id();
        String released = id();
        add("DP-01 misuse dispute after release", "A misuse dispute on a milestone that received funds is HIGH",
                step(disbursement("DISBURSEMENT_RELEASED", campaign, released, id(), new BigDecimal("3000000.00"), Map.of("simulated", true)), none(DP_01)),
                step(dispute(campaign, released, "MISUSE_OF_FUNDS"), finding(DP_01, "HIGH")));
        add("DP-01 misuse dispute without release", "No release on the milestone raises nothing",
                step(dispute(id(), id(), "MISUSE_OF_FUNDS"), none(DP_01)));
        String campaign2 = id();
        String released2 = id();
        add("DP-01 other dispute type after release", "A non-misuse dispute raises nothing",
                step(disbursement("DISBURSEMENT_RELEASED", campaign2, released2, id(), new BigDecimal("3000000.00"), Map.of("simulated", true)), none(DP_01)),
                step(dispute(campaign2, released2, "MISSING_EVIDENCE"), none(DP_01)));
    }

    private record Step(Event event, ExpectedOutcome expected) {
    }

    private record Event(String eventId, ObjectNode body) {
    }

    private Step step(Event event, ExpectedOutcome expected) {
        return new Step(event, expected);
    }

    private void add(String template, String description, Step... steps) {
        int index = scenarios.size();
        List<ScenarioStep> built = new ArrayList<>();
        for (Step step : steps) {
            try {
                built.add(ScenarioStep.builder().eventId(step.event().eventId()).body(mapper.writeValueAsString(step.event().body()))
                        .expected(step.expected()).build());
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException(ex);
            }
        }
        scenarios.add(Scenario.builder().scenarioId(String.format("RISE-%03d", index + 1)).index(index).category(Category.RISE_RULES)
                .template(template).description(description).steps(built).build());
    }

    private static ExpectedOutcome finding(String ruleId, String severity) {
        return ExpectedOutcome.builder().httpStatus(200).requiredFindings(Map.of(ruleId, severity)).source("Rise rule " + ruleId).build();
    }

    private static ExpectedOutcome none(String ruleId) {
        return ExpectedOutcome.builder().httpStatus(200).forbiddenFindings(Set.of(ruleId)).source("Rise rule " + ruleId).build();
    }

    private Event evidence(String campaignId, String milestoneId, String hash, String type) {
        String evidenceId = id();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceId", evidenceId);
        payload.put("milestoneId", milestoneId);
        payload.put("campaignId", campaignId);
        payload.put("evidenceType", type);
        if (hash != null) {
            payload.put("fileHash", hash);
        }
        return envelope("MILESTONE_EVIDENCE_SUBMITTED", "EVIDENCE",
                Map.of("campaignId", campaignId, "milestoneId", milestoneId, "evidenceId", evidenceId), payload);
    }

    private Event reconciliationRun(String status, int inconsistent) {
        return envelope("RECONCILIATION_RUN_COMPLETED", "RECONCILIATION_RUN", Map.of(),
                Map.of("runId", id(), "runStatus", status, "accountsChecked", 4, "inconsistentAccounts", inconsistent));
    }

    private Event disbursement(String type, String campaignId, String milestoneId, String requestId, BigDecimal amount, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>(Map.of("requestId", requestId, "milestoneId", milestoneId, "campaignId", campaignId,
                "amount", amount.toPlainString(), "currency", "VND"));
        payload.putAll(extra);
        return envelope(type, "DISBURSEMENT_REQUEST",
                Map.of("campaignId", campaignId, "milestoneId", milestoneId, "disbursementRequestId", requestId), payload);
    }

    private Event adjustment(BigDecimal amount) {
        String campaign = id();
        return envelope("ADJUSTMENT_APPLIED", "ESCROW_ADJUSTMENT", Map.of("campaignId", campaign),
                Map.of("adjustmentId", id(), "campaignId", campaign, "direction", "DEBIT", "amount", amount.toPlainString(), "currency", "VND"));
    }

    private Event dispute(String campaignId, String milestoneId, String type) {
        return envelope("DISPUTE_OPENED", "DISPUTE", Map.of("campaignId", campaignId, "milestoneId", milestoneId),
                Map.of("disputeId", id(), "campaignId", campaignId, "milestoneId", milestoneId, "disputeType", type));
    }

    private Event envelope(String eventType, String aggregateType, Map<String, Object> references, Map<String, Object> payload) {
        String eventId = id();
        String occurredAt = base.plus(Duration.ofSeconds(10L * ++clockStep)).toString();
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("schemaVersion", 1);
        envelope.put("occurredAt", occurredAt);
        envelope.put("producer", SOURCE);
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", id());
        envelope.put("actorRole", "ADMIN");
        envelope.set("references", mapper.valueToTree(references));
        envelope.set("payload", mapper.valueToTree(payload));
        ObjectNode request = mapper.createObjectNode();
        request.put("eventId", eventId);
        request.put("eventType", eventType);
        request.put("source", SOURCE);
        request.put("occurredAt", occurredAt);
        request.set("attributes", mapper.valueToTree(Map.of("schemaVersion", 1, "aggregateType", aggregateType)));
        request.set("payload", envelope);
        return new Event(eventId, request);
    }

    private Event at(Event event, Instant occurredAt) {
        ObjectNode request = event.body();
        request.put("occurredAt", occurredAt.toString());
        ((ObjectNode) request.get("payload")).put("occurredAt", occurredAt.toString());
        return event;
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }
}
