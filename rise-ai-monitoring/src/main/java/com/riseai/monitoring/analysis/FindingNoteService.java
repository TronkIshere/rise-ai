package com.riseai.monitoring.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.configuration.OllamaProperties;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.ollama.OllamaClient;
import com.riseai.monitoring.ollama.dto.OllamaChatMessage;
import com.riseai.monitoring.ollama.dto.OllamaChatRequest;
import com.riseai.monitoring.ollama.dto.OllamaChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FindingNoteService {

    private final MonitoringProperties properties;
    private final OllamaProperties ollamaProperties;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return properties.getAi().isEnabled() && properties.getRise().isAiNotesEnabled();
    }

    public List<Finding> annotate(List<Finding> findings) {
        if (!isEnabled() || findings.isEmpty()) {
            return findings;
        }
        long deadline = System.nanoTime() + properties.getRise().getAiNoteBudget().toNanos();
        List<Finding> result = new ArrayList<>();
        for (Finding finding : findings) {
            if (System.nanoTime() >= deadline) {
                log.warn("AI note budget exhausted; rule {} is returned without a note", finding.getRuleId());
                result.add(finding);
            } else {
                result.add(annotate(finding));
            }
        }
        return List.copyOf(result);
    }

    Finding annotate(Finding finding) {
        return note(finding).map(note -> finding.toBuilder().aiNote(note).build()).orElse(finding);
    }

    public Optional<String> note(Finding finding) {
        try {
            FindingNotePrompt prompt = FindingNotePrompt.build(finding);
            OllamaChatResponse response = ollamaClient.chat(OllamaChatRequest.builder()
                    .model(ollamaProperties.getModel())
                    .messages(List.of(
                            OllamaChatMessage.builder().role("system").content(prompt.systemPrompt()).build(),
                            OllamaChatMessage.builder().role("user").content(prompt.userPrompt()).build()))
                    .stream(false)
                    .format(prompt.outputSchema())
                    .think(ollamaProperties.getThink())
                    .options(options())
                    .build());
            JsonNode root = objectMapper.readTree(response.getMessage().getContent());
            Optional<String> accepted = FindingNoteFilter.accept(root.path("note").asText(null), properties.getRise().getAiNoteMaxLength());
            if (accepted.isEmpty()) {
                log.warn("AI note for rule {} was rejected by the output filter", finding.getRuleId());
            }
            return accepted;
        } catch (Exception ex) {
            log.warn("AI note for rule {} unavailable: {}", finding.getRuleId(), ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Map<String, Object> options() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", ollamaProperties.getTemperature());
        options.put("num_predict", ollamaProperties.getMaxOutputTokens());
        if (ollamaProperties.getContextLength() != null) {
            options.put("num_ctx", ollamaProperties.getContextLength());
        }
        return options;
    }
}
