package com.riseai.monitoring.rise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.finding.FindingCategory;
import com.riseai.monitoring.core.finding.RecommendedAction;
import com.riseai.monitoring.testsupport.FindingSchemaContract;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class FindingSchemaContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EVENT_ID = "0f8c2a3e-6b1d-4d1e-9a8a-3f6f2b1c9d10";

    private ObjectNode valid() {
        ObjectNode finding = MAPPER.createObjectNode();
        finding.put("findingId", "a".repeat(64));
        finding.put("contractVersion", "finding.v1");
        finding.putObject("detector").put("type", "RULE").put("id", "rise.evidence.duplicate-file-hash").put("version", "1");
        finding.put("category", "EVIDENCE_INTEGRITY");
        finding.put("severity", "HIGH");
        finding.putObject("subject").put("type", "EVIDENCE").put("id", "0f8c2a3e-6b1d-4d1e-9a8a-3f6f2b1c9d12");
        finding.putObject("references").put("campaignId", "0f8c2a3e-6b1d-4d1e-9a8a-3f6f2b1c9d13");
        finding.putArray("sourceEventIds").add(EVENT_ID);
        ArrayNode facts = finding.putArray("facts");
        facts.addObject().put("name", "fileHash").put("value", "ab".repeat(32)).put("sourceEventId", EVENT_ID);
        facts.addObject().put("name", "otherCampaignCount").put("value", 1).put("sourceEventId", EVENT_ID);
        ObjectNode list = facts.addObject().put("name", "earlierEvidenceIds").put("sourceEventId", EVENT_ID);
        list.putArray("value").add("0f8c2a3e-6b1d-4d1e-9a8a-3f6f2b1c9d14");
        finding.put("explanation", "The same file was already submitted.");
        finding.put("recommendedAction", "REVIEW_EVIDENCE");
        finding.put("observedAt", "2026-10-25T12:14:01.123456Z");
        return finding;
    }

    private boolean accepts(ObjectNode finding) {
        return FindingSchemaContract.validate(finding).isEmpty();
    }

    private boolean rejectsAfter(Consumer<ObjectNode> mutation) {
        ObjectNode finding = valid();
        mutation.accept(finding);
        return !accepts(finding);
    }

    @Test
    void aWellFormedFindingAndTheOptionalAiNoteAreAccepted() {
        assertThat(accepts(valid())).isTrue();
        ObjectNode withNote = valid();
        withNote.put("aiNote", "Reviewers may want to compare both milestones.");
        assertThat(accepts(withNote)).isTrue();
    }

    @Test
    void removingAnyRequiredFieldIsRejected() {
        for (String field : new String[]{"findingId", "contractVersion", "detector", "category", "severity", "subject", "references",
                "sourceEventIds", "facts", "explanation", "recommendedAction", "observedAt"}) {
            assertThat(rejectsAfter(f -> f.remove(field))).as(field).isTrue();
        }
        for (String field : new String[]{"type", "id", "version"}) {
            assertThat(rejectsAfter(f -> ((ObjectNode) f.get("detector")).remove(field))).as("detector." + field).isTrue();
        }
        for (String field : new String[]{"type", "id"}) {
            assertThat(rejectsAfter(f -> ((ObjectNode) f.get("subject")).remove(field))).as("subject." + field).isTrue();
        }
        for (String field : new String[]{"name", "value", "sourceEventId"}) {
            assertThat(rejectsAfter(f -> ((ObjectNode) f.get("facts").get(0)).remove(field))).as("fact." + field).isTrue();
        }
    }

    @Test
    void wrongTypesAndInvalidEnumsAreRejected() {
        assertThat(rejectsAfter(f -> f.put("severity", "SEVERE"))).isTrue();
        assertThat(rejectsAfter(f -> f.put("severity", "high"))).isTrue();
        assertThat(rejectsAfter(f -> f.put("category", "FRAUD"))).isTrue();
        assertThat(rejectsAfter(f -> f.put("recommendedAction", "RELEASE_FUNDS"))).isTrue();
        assertThat(rejectsAfter(f -> f.put("contractVersion", "finding.v2"))).isTrue();
        assertThat(rejectsAfter(f -> ((ObjectNode) f.get("detector")).put("type", "HUMAN"))).isTrue();
        assertThat(rejectsAfter(f -> f.put("severity", 3))).isTrue();
        assertThat(rejectsAfter(f -> f.put("explanation", 42))).isTrue();
        assertThat(rejectsAfter(f -> f.put("facts", "none"))).isTrue();
        assertThat(rejectsAfter(f -> f.putNull("explanation"))).isTrue();
        assertThat(rejectsAfter(f -> f.put("sourceEventIds", EVENT_ID))).isTrue();
    }

    @Test
    void malformedIdentifiersTimestampsAndReferencesAreRejected() {
        assertThat(rejectsAfter(f -> f.put("findingId", "A".repeat(64)))).isTrue();
        assertThat(rejectsAfter(f -> f.put("findingId", "a".repeat(63)))).isTrue();
        assertThat(rejectsAfter(f -> f.put("findingId", "g".repeat(64)))).isTrue();
        assertThat(rejectsAfter(f -> f.put("observedAt", "yesterday"))).isTrue();
        assertThat(rejectsAfter(f -> f.put("observedAt", "2026-10-25T12:14:01+07:00"))).isTrue();
        assertThat(rejectsAfter(f -> ((ObjectNode) f.get("references")).put("campaignId", "not-a-uuid"))).isTrue();
        assertThat(rejectsAfter(f -> ((ObjectNode) f.get("references")).put("milestoneId", 7))).isTrue();
        assertThat(rejectsAfter(f -> ((ArrayNode) f.get("sourceEventIds")).removeAll())).isTrue();
        assertThat(rejectsAfter(f -> ((ObjectNode) f.get("subject")).put("id", "has spaces"))).isTrue();
        assertThat(rejectsAfter(f -> ((ObjectNode) f.get("detector")).put("id", "x".repeat(81)))).isTrue();
    }

    @Test
    void oversizedContentIsRejected() {
        assertThat(rejectsAfter(f -> f.put("explanation", "x".repeat(2001)))).isTrue();
        assertThat(rejectsAfter(f -> f.put("aiNote", "x".repeat(401)))).isTrue();
        assertThat(rejectsAfter(f -> {
            ArrayNode ids = (ArrayNode) f.get("sourceEventIds");
            for (int i = 0; i < 21; i++) {
                ids.add(EVENT_ID);
            }
        })).isTrue();
        assertThat(rejectsAfter(f -> {
            ArrayNode facts = (ArrayNode) f.get("facts");
            for (int i = 0; i < 21; i++) {
                facts.addObject().put("name", "n" + i).put("value", i).put("sourceEventId", EVENT_ID);
            }
        })).isTrue();
        assertThat(rejectsAfter(f -> {
            ArrayNode value = (ArrayNode) f.get("facts").get(2).get("value");
            for (int i = 0; i < 25; i++) {
                value.add("id-" + i);
            }
        })).isTrue();
    }

    @Test
    void additiveUnknownFieldsStayValidForBackwardCompatibility() {
        ObjectNode finding = valid();
        finding.put("futureField", "ignored by consumers");
        ((ObjectNode) finding.get("detector")).put("model", "later-version");
        assertThat(accepts(finding)).isTrue();
    }

    @Test
    void malformedJsonTextIsReportedAsInvalid() {
        assertThat(FindingSchemaContract.validateText("[]")).isNotEmpty();
        assertThat(FindingSchemaContract.validateText("{}")).isNotEmpty();
        assertThat(FindingSchemaContract.validateText("\"text\"")).isNotEmpty();
    }

    @Test
    void theSchemaEnumsMatchTheJavaEnums() throws Exception {
        JsonNode schema = MAPPER.readTree(Files.readString(FindingSchemaContract.SCHEMA_PATH));
        assertThat(values(schema.at("/properties/severity/enum"))).containsExactlyInAnyOrder(names(Severity.values()));
        assertThat(values(schema.at("/properties/category/enum"))).containsExactlyInAnyOrder(names(FindingCategory.values()));
        assertThat(values(schema.at("/properties/recommendedAction/enum"))).containsExactlyInAnyOrder(names(RecommendedAction.values()));
    }

    private static String[] values(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(node -> result.add(node.asText()));
        return result.toArray(String[]::new);
    }

    private static String[] names(Enum<?>[] constants) {
        return Arrays.stream(constants).map(Enum::name).toArray(String[]::new);
    }
}
