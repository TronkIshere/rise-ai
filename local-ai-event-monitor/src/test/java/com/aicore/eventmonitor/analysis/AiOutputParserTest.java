package com.aicore.eventmonitor.analysis;

import com.aicore.eventmonitor.configuration.MonitoringProperties;
import com.aicore.eventmonitor.core.analysis.AiAssessment;
import com.aicore.eventmonitor.core.analysis.AiConfidence;
import com.aicore.eventmonitor.testsupport.TestEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiOutputParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MonitoringProperties properties = new MonitoringProperties();
    private final AiOutputParser parser = new AiOutputParser(objectMapper, properties);

    @Test
    void parsesAValidAssessment() {
        AiAssessmentOutput output = parser.parse(TestEvents.VALID_ASSESSMENT_JSON);

        assertThat(output.getAssessment()).isEqualTo(AiAssessment.NEEDS_REVIEW);
        assertThat(output.getConfidence()).isEqualTo(AiConfidence.MEDIUM);
        assertThat(output.getExplanation()).startsWith("Latency is far above");
        assertThat(output.getPossibleCauses()).containsExactly("Upstream dependency slowness");
        assertThat(output.getMissingEvidence()).containsExactly("Recent latency baseline for this source");
        assertThat(output.isSuggestedReview()).isTrue();
    }

    @Test
    void acceptsOutputWrappedInACodeFence() {
        assertThat(parser.parse("```json\n" + TestEvents.VALID_ASSESSMENT_JSON + "\n```").getAssessment())
                .isEqualTo(AiAssessment.NEEDS_REVIEW);
    }

    @Test
    void sanitizesControlCharactersAndTruncatesLongItems() {
        properties.getAi().setMaxItemLength(20);

        AiAssessmentOutput output = parser.parse(variant(node -> node.putArray("possibleCauses")
                .add("line\u0000one\nline two that is far too long")));

        assertThat(output.getPossibleCauses()).containsExactly("line one line two th");
    }

    @Test
    void rejectsMalformedOrNonObjectOutput() {
        assertInvalid("{\"assessment\":", "not valid JSON");
        assertInvalid(TestEvents.VALID_ASSESSMENT_JSON + " trailing text", "not valid JSON");
        assertInvalid("[1,2,3]", "not a JSON object");
        assertInvalid("   ", "no content");
    }

    @Test
    void rejectsIncompleteOutput() {
        assertInvalid(variant(node -> node.remove("confidence")), "required field 'confidence' is missing");
        assertInvalid(variant(node -> node.remove("missingEvidence")), "required field 'missingEvidence' is missing");
    }

    @Test
    void rejectsUnexpectedEnumValues() {
        assertInvalid(variant(node -> node.put("assessment", "MALICIOUS")), "assessment has an unexpected value");
        assertInvalid(variant(node -> node.put("assessment", "needs_review")), "assessment has an unexpected value");
        assertInvalid(variant(node -> node.put("confidence", "0.93")), "confidence has an unexpected value");
    }

    @Test
    void rejectsWrongTypes() {
        assertInvalid(variant(node -> node.put("suggestedReview", "true")), "suggestedReview must be a boolean");
        assertInvalid(variant(node -> node.put("possibleCauses", "none")), "possibleCauses must be an array");
        assertInvalid(variant(node -> node.putArray("missingEvidence").add(3)), "missingEvidence must contain only strings");
        assertInvalid(variant(node -> node.put("explanation", 42)), "explanation must be a string");
    }

    @Test
    void rejectsFieldsThatCouldOverrideAuthoritativeData() {
        assertInvalid(variant(node -> node.put("eventId", "evt-other")), "unexpected field 'eventId'");
        assertInvalid(variant(node -> node.put("riskLevel", "LOW")), "unexpected field 'riskLevel'");
        assertInvalid(variant(node -> node.putArray("ruleFindings")), "unexpected field 'ruleFindings'");
    }

    @Test
    void rejectsTooManyItemsAndBlankExplanations() {
        assertInvalid(variant(node -> node.putArray("possibleCauses").add("a").add("b").add("c").add("d").add("e").add("f")),
                "possibleCauses must contain at most 5 entries");
        assertInvalid(variant(node -> node.put("explanation", "  ")), "explanation must not be blank");
    }

    private String variant(Consumer<ObjectNode> change) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(TestEvents.VALID_ASSESSMENT_JSON);
            change.accept(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void assertInvalid(String content, String expectedMessage) {
        assertThatThrownBy(() -> parser.parse(content))
                .isInstanceOf(InvalidAiOutputException.class)
                .hasMessageContaining(expectedMessage);
    }
}
