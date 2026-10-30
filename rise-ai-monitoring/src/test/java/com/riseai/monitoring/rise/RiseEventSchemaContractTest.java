package com.riseai.monitoring.rise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.exception.ApplicationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiseEventSchemaContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EVENT_ID = "0f8c2a3e-6b1d-4d1e-9a8a-3f6f2b1c9d10";
    private static JsonSchema schema;
    private static JsonNode schemaNode;
    private static JsonNode fixtures;
    private final RiseEnvelopeReader reader = new RiseEnvelopeReader(new MonitoringProperties());

    @BeforeAll
    static void load() throws IOException {
        schemaNode = MAPPER.readTree(Files.readString(Path.of("..", "contracts", "events", "rise-event-envelope.v1.schema.json")));
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode, new SchemaValidatorsConfig());
        fixtures = MAPPER.readTree(Files.readString(Path.of("src", "test", "resources", "contracts", "rise-event-payload-fixtures.json")));
    }

    private ObjectNode envelope(String type) {
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("eventId", EVENT_ID);
        envelope.put("eventType", type);
        envelope.put("schemaVersion", 1);
        envelope.put("occurredAt", "2026-10-25T12:14:01.123456Z");
        envelope.put("producer", "rise-ai-backend");
        envelope.put("aggregateType", fixtures.path(type).path("aggregateType").asText());
        envelope.put("aggregateId", "0f8c2a3e-6b1d-4d1e-9a8a-3f6f2b1c9d11");
        envelope.put("actorRole", "ADMIN");
        envelope.set("payload", fixtures.path(type).path("payload").deepCopy());
        return envelope;
    }

    private boolean schemaAccepts(JsonNode envelope) {
        return schema.validate(envelope).isEmpty();
    }

    private boolean readerAccepts(JsonNode envelope) {
        MonitoringEvent event = MonitoringEvent.builder().eventId(envelope.path("eventId").asText(null))
                .eventType(envelope.path("eventType").asText("").toLowerCase(Locale.ROOT)).source("rise-ai-backend")
                .occurredAt(Instant.parse("2026-10-25T12:14:01Z")).payload(envelope).build();
        try {
            return reader.read(event).isPresent();
        } catch (ApplicationException ex) {
            return false;
        }
    }

    private List<String> eventTypes() {
        List<String> types = new ArrayList<>();
        schemaNode.path("properties").path("eventType").path("enum").forEach(node -> types.add(node.asText()));
        return types;
    }

    @Test
    void theSchemaCoversTheTwentyCataloguedEventTypesAndEachHasAFixture() {
        assertThat(eventTypes()).hasSize(20).allSatisfy(type -> assertThat(fixtures.has(type)).as(type).isTrue());
    }

    @Test
    void everyPositiveFixtureIsAcceptedByTheSchemaAndByTheMonitorReader() {
        for (String type : eventTypes()) {
            ObjectNode envelope = envelope(type);
            assertThat(schema.validate(envelope)).as(type).isEmpty();
            assertThat(readerAccepts(envelope)).as(type).isTrue();
        }
    }

    @Test
    void removingAnyRequiredPayloadFieldIsRejectedBySchema() {
        int checked = 0;
        for (JsonNode branch : schemaNode.path("allOf")) {
            String type = branch.path("if").path("properties").path("eventType").path("const").asText();
            for (JsonNode field : branch.path("then").path("properties").path("payload").path("required")) {
                ObjectNode envelope = envelope(type);
                ((ObjectNode) envelope.path("payload")).remove(field.asText());
                assertThat(schemaAccepts(envelope)).as(type + " without " + field.asText()).isFalse();
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(60);
    }

    @Test
    void removingAnyRequiredEnvelopeFieldIsRejectedByTheSchemaAndTheReader() {
        for (JsonNode field : schemaNode.path("required")) {
            ObjectNode envelope = envelope("DISBURSEMENT_APPROVED");
            envelope.remove(field.asText());
            assertThat(schemaAccepts(envelope)).as(field.asText()).isFalse();
            if (!field.asText().equals("producer")) {
                assertThat(readerAccepts(envelope)).as("reader without " + field.asText()).isFalse();
            }
        }
    }

    @Test
    void unsupportedSchemaVersionsAndUnknownEventTypesAreRejected() {
        ObjectNode future = envelope("CAMPAIGN_PAUSED");
        future.put("schemaVersion", 2);
        assertThat(schemaAccepts(future)).isFalse();
        assertThat(readerAccepts(future)).isFalse();

        ObjectNode unknown = envelope("CAMPAIGN_PAUSED");
        unknown.put("eventType", "CAMPAIGN_DELETED");
        assertThat(schemaAccepts(unknown)).isFalse();
    }

    @Test
    void malformedValuesAreRejectedBySchema() {
        for (String amount : List.of("10", "1e3", "10.5", "-", " 10.00", "10.00; drop")) {
            ObjectNode envelope = envelope("DISBURSEMENT_APPROVED");
            ((ObjectNode) envelope.path("payload")).put("amount", amount);
            assertThat(schemaAccepts(envelope)).as(amount).isFalse();
        }
        ObjectNode numericAmount = envelope("DISBURSEMENT_APPROVED");
        ((ObjectNode) numericAmount.path("payload")).put("amount", 10.0);
        assertThat(schemaAccepts(numericAmount)).isFalse();

        ObjectNode badId = envelope("EVIDENCE_ACCEPTED");
        ((ObjectNode) badId.path("payload")).put("evidenceId", "not-a-uuid");
        assertThat(schemaAccepts(badId)).isFalse();

        ObjectNode badTimestamp = envelope("CAMPAIGN_PAUSED");
        badTimestamp.put("occurredAt", "yesterday");
        assertThat(schemaAccepts(badTimestamp)).isFalse();
        assertThat(readerAccepts(badTimestamp)).isFalse();

        ObjectNode badHash = envelope("MILESTONE_EVIDENCE_SUBMITTED");
        ((ObjectNode) badHash.path("payload")).put("fileHash", "xyz");
        assertThat(schemaAccepts(badHash)).isFalse();

        ObjectNode badOutcome = envelope("DISPUTE_RESOLVED");
        ((ObjectNode) badOutcome.path("payload")).put("outcome", "WHATEVER");
        assertThat(schemaAccepts(badOutcome)).isFalse();

        ObjectNode nullField = envelope("CAMPAIGN_PAUSED");
        ((ObjectNode) nullField.path("payload")).putNull("campaignId");
        assertThat(schemaAccepts(nullField)).isFalse();

        ObjectNode arrayPayload = envelope("CAMPAIGN_PAUSED");
        arrayPayload.set("payload", MAPPER.createArrayNode());
        assertThat(schemaAccepts(arrayPayload)).isFalse();
        assertThat(readerAccepts(arrayPayload)).isFalse();
    }

    @Test
    void additiveUnknownFieldsStayValidForBackwardCompatibility() {
        for (String type : eventTypes()) {
            ObjectNode envelope = envelope(type);
            envelope.put("addedInALaterMinorRevision", "x");
            ((ObjectNode) envelope.path("payload")).put("addedOptionalField", 1);
            assertThat(schemaAccepts(envelope)).as(type).isTrue();
            assertThat(readerAccepts(envelope)).as(type).isTrue();
        }
    }

    @Test
    void theReaderRejectsAMismatchBetweenRequestAndEnvelope() {
        ObjectNode envelope = envelope("CAMPAIGN_PAUSED");
        MonitoringEvent event = MonitoringEvent.builder().eventId("another-id").eventType("campaign_paused").source("rise-ai-backend")
                .occurredAt(Instant.now()).payload(envelope).build();
        assertThatThrownBy(() -> reader.read(event)).isInstanceOf(ApplicationException.class);
    }
}
