package com.aicore.eventmonitor.analysis;

import com.aicore.eventmonitor.configuration.MonitoringProperties;
import com.aicore.eventmonitor.core.analysis.AiAssessment;
import com.aicore.eventmonitor.core.analysis.AiConfidence;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AiOutputParser {

    private static final String CODE_FENCE = "```";
    private static final int MAX_REPORTED_FIELD_NAME_LENGTH = 40;

    ObjectMapper objectMapper;
    MonitoringProperties properties;

    public AiAssessmentOutput parse(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidAiOutputException("the model returned no content");
        }
        JsonNode root = readJson(stripCodeFence(content.trim()));
        if (root == null || !root.isObject()) {
            throw new InvalidAiOutputException("the model output is not a JSON object");
        }
        rejectUnexpectedFields(root);

        MonitoringProperties.Ai ai = properties.getAi();
        String explanation = sanitize(requireText(root, AiAssessmentOutput.FIELD_EXPLANATION), ai.getMaxExplanationLength());
        if (explanation.isEmpty()) {
            throw new InvalidAiOutputException("explanation must not be blank");
        }
        JsonNode suggestedReview = root.get(AiAssessmentOutput.FIELD_SUGGESTED_REVIEW);
        if (suggestedReview == null || !suggestedReview.isBoolean()) {
            throw new InvalidAiOutputException("suggestedReview must be a boolean");
        }

        return AiAssessmentOutput.builder()
                .assessment(requireEnum(root, AiAssessmentOutput.FIELD_ASSESSMENT, AiAssessment.class))
                .confidence(requireEnum(root, AiAssessmentOutput.FIELD_CONFIDENCE, AiConfidence.class))
                .explanation(explanation)
                .possibleCauses(requireTextList(root, AiAssessmentOutput.FIELD_POSSIBLE_CAUSES, ai))
                .missingEvidence(requireTextList(root, AiAssessmentOutput.FIELD_MISSING_EVIDENCE, ai))
                .suggestedReview(suggestedReview.booleanValue())
                .build();
    }

    private void rejectUnexpectedFields(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!AiAssessmentOutput.FIELDS.contains(name)) {
                throw new InvalidAiOutputException("unexpected field '" + sanitize(name, MAX_REPORTED_FIELD_NAME_LENGTH) + "'");
            }
        }
        for (String field : AiAssessmentOutput.FIELDS) {
            if (!root.has(field)) {
                throw new InvalidAiOutputException("required field '" + field + "' is missing");
            }
        }
    }

    private <E extends Enum<E>> E requireEnum(JsonNode root, String field, Class<E> type) {
        String value = requireText(root, field);
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new InvalidAiOutputException(field + " has an unexpected value"));
    }

    private String requireText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual()) {
            throw new InvalidAiOutputException(field + " must be a string");
        }
        return node.asText();
    }

    private List<String> requireTextList(JsonNode root, String field, MonitoringProperties.Ai ai) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            throw new InvalidAiOutputException(field + " must be an array");
        }
        if (node.size() > ai.getMaxListItems()) {
            throw new InvalidAiOutputException(field + " must contain at most " + ai.getMaxListItems() + " entries");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new InvalidAiOutputException(field + " must contain only strings");
            }
            String text = sanitize(item.asText(), ai.getMaxItemLength());
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private JsonNode readJson(String content) {
        try {
            return objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(content);
        } catch (JsonProcessingException ex) {
            throw new InvalidAiOutputException("the model output is not valid JSON");
        }
    }

    private String stripCodeFence(String content) {
        if (!content.startsWith(CODE_FENCE)) {
            return content;
        }
        int firstLineEnd = content.indexOf('\n');
        if (firstLineEnd < 0) {
            return content;
        }
        String body = content.substring(firstLineEnd + 1);
        int closingFence = body.lastIndexOf(CODE_FENCE);
        return closingFence < 0 ? body : body.substring(0, closingFence);
    }

    private String sanitize(String text, int maxLength) {
        String cleaned = text.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength).trim();
    }
}
