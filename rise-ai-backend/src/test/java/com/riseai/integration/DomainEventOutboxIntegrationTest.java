package com.riseai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.dto.request.CreateDisbursementRequest;
import com.riseai.dto.request.EvidenceRequest;
import com.riseai.entity.OutboxEvent;
import com.riseai.enums.EvidenceType;
import com.riseai.enums.OutboxStatus;
import com.riseai.enums.ReconciliationTrigger;
import com.riseai.enums.Role;
import com.riseai.exception.ErrorCode;
import com.riseai.mapper.RiseEvents;
import com.riseai.repository.OutboxEventRepository;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventOutboxIntegrationTest extends AbstractMySqlIntegrationTest {
    private static final Set<String> ENVELOPE_FIELDS = Set.of("eventId", "eventType", "schemaVersion", "occurredAt", "producer",
            "aggregateType", "aggregateId", "actorId", "actorRole", "correlationId", "references", "payload");
    private static final Set<String> FORBIDDEN_PAYLOAD_FIELDS = Set.of("title", "description", "reason", "requestReason", "note",
            "reviewNote", "resolution", "fileUrl", "externalReference", "donorMessage", "email", "fullName", "contactEmail",
            "contactPhone", "address", "beneficiaryDescription", "ipAddress", "idempotencyKey", "simulatedReference");

    @Autowired
    OutboxEventRepository outboxEventRepository;
    @Autowired
    ObjectMapper objectMapper;

    AuthUser creator;
    AuthUser admin;
    AuthUser donor;

    @BeforeEach
    void users() {
        creator = user(Role.CAMPAIGN_CREATOR);
        admin = user(Role.ADMIN);
        donor = user(Role.DONOR);
    }

    @Test
    void milestoneLifecycleProducesOneMinimizedEventPerCommittedAction() throws Exception {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID m1 = campaign.milestones().get(0);
        UUID donation = confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        milestoneService.start(creator, m1);
        String hash = "a".repeat(64);
        UUID evidence = evidenceService.submit(creator, m1, null, new EvidenceRequest(EvidenceType.RECEIPT, "Receipt for rice",
                "Sensitive free-text description", "sim://evidence/receipt.pdf", hash, null)).id();
        evidenceService.review(admin, evidence, true, "Looks fine");
        UUID request = approvedRequest(creator, admin, m1, 4_000_000);
        disbursementService.release(admin, request);

        List<String> campaignEvents = types(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("CAMPAIGN", campaign.id()));
        assertThat(campaignEvents).containsExactly(RiseEvents.CAMPAIGN_CREATED, RiseEvents.CAMPAIGN_SUBMITTED_FOR_REVIEW, RiseEvents.CAMPAIGN_APPROVED);
        assertThat(types(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("MILESTONE", m1)))
                .containsExactly(RiseEvents.MILESTONE_CREATED);
        assertThat(types(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("DONATION", donation)))
                .containsExactly(RiseEvents.DONATION_CONFIRMED);
        assertThat(types(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("EVIDENCE", evidence)))
                .containsExactly(RiseEvents.MILESTONE_EVIDENCE_SUBMITTED, RiseEvents.EVIDENCE_ACCEPTED);
        assertThat(types(outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("DISBURSEMENT_REQUEST", request)))
                .containsExactly(RiseEvents.DISBURSEMENT_REQUESTED, RiseEvents.DISBURSEMENT_APPROVED, RiseEvents.DISBURSEMENT_RELEASED);

        OutboxEvent submitted = outboxEventRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("EVIDENCE", evidence).get(0);
        JsonNode envelope = objectMapper.readTree(submitted.getEnvelope());
        assertThat(fieldNames(envelope)).isSubsetOf(ENVELOPE_FIELDS);
        assertThat(envelope.path("eventId").asText()).isEqualTo(submitted.getEventId().toString());
        assertThat(envelope.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.path("producer").asText()).isEqualTo("rise-ai-backend");
        assertThat(envelope.path("actorRole").asText()).isEqualTo("CAMPAIGN_CREATOR");
        assertThat(envelope.path("references").path("campaignId").asText()).isEqualTo(campaign.id().toString());
        assertThat(envelope.path("payload").path("fileHash").asText()).isEqualTo(hash);
        assertThat(envelope.path("payload").path("evidenceType").asText()).isEqualTo("RECEIPT");
        assertThat(submitted.getEnvelope()).doesNotContain("Sensitive free-text description", "Receipt for rice", "sim://evidence");

        JsonNode approved = objectMapper.readTree(outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("DISBURSEMENT_REQUEST", request).get(1).getEnvelope());
        assertThat(approved.path("payload").path("acceptedEvidenceCount").asLong()).isEqualTo(1);
        assertThat(approved.path("payload").path("amount").asText()).isEqualTo("4000000.00");
        JsonNode released = objectMapper.readTree(outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("DISBURSEMENT_REQUEST", request).get(2).getEnvelope());
        assertThat(released.path("payload").path("ledgerTransactionCode").asText()).isNotBlank();
        assertThat(released.path("payload").path("simulated").asBoolean()).isTrue();

        for (OutboxEvent event : outboxEventRepository.findAll()) {
            JsonNode payload = objectMapper.readTree(event.getEnvelope()).path("payload");
            assertThat(fieldNames(payload)).as("payload of %s", event.getEventType()).doesNotContainAnyElementsOf(FORBIDDEN_PAYLOAD_FIELDS);
            assertThat(event.getStatus()).isIn(OutboxStatus.values());
        }
    }

    @Test
    void rejectedBusinessOperationWritesNoEvent() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID m1 = campaign.milestones().get(0);
        confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        long before = outboxEventRepository.count();

        assertError(() -> disbursementService.create(creator, m1, new CreateDisbursementRequest(vnd(20_000_000), "Too much")),
                ErrorCode.MILESTONE_BUDGET_EXCEEDED);

        assertThat(outboxEventRepository.count()).isEqualTo(before);
    }

    @Test
    void reconciliationRunPublishesSystemEventInsideItsTransaction() throws Exception {
        reconciliationService.run(ReconciliationTrigger.SCHEDULED, null);

        List<OutboxEvent> events = outboxEventRepository.findByEventTypeOrderByOccurredAtAsc(RiseEvents.RECONCILIATION_RUN_COMPLETED);
        assertThat(events).isNotEmpty();
        JsonNode envelope = objectMapper.readTree(events.get(events.size() - 1).getEnvelope());
        assertThat(envelope.path("actorRole").asText()).isEqualTo("SYSTEM");
        assertThat(envelope.has("actorId")).isFalse();
        assertThat(envelope.path("payload").path("runStatus").asText()).isIn("CONSISTENT", "INCONSISTENT", "FAILED");
        assertThat(envelope.path("payload").has("accountsChecked")).isTrue();
    }

    private static List<String> types(List<OutboxEvent> events) {
        return events.stream().map(OutboxEvent::getEventType).toList();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }
}
