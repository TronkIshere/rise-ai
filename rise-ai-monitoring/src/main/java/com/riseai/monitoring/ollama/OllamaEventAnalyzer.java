package com.riseai.monitoring.ollama;

import com.riseai.monitoring.analysis.AiAssessmentOutput;
import com.riseai.monitoring.analysis.AiOutputParser;
import com.riseai.monitoring.analysis.AnalysisPrompt;
import com.riseai.monitoring.analysis.AnalysisPromptBuilder;
import com.riseai.monitoring.analysis.InvalidAiOutputException;
import com.riseai.monitoring.configuration.OllamaProperties;
import com.riseai.monitoring.core.analysis.AiAnalysis;
import com.riseai.monitoring.core.analysis.AiEventAnalyzer;
import com.riseai.monitoring.core.analysis.AiRuntimeState;
import com.riseai.monitoring.core.analysis.AiRuntimeStatus;
import com.riseai.monitoring.core.analysis.AnalysisStatus;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.rule.RuleFinding;
import com.riseai.monitoring.ollama.dto.OllamaChatMessage;
import com.riseai.monitoring.ollama.dto.OllamaChatRequest;
import com.riseai.monitoring.ollama.dto.OllamaChatResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OllamaEventAnalyzer implements AiEventAnalyzer {

    public static final String RUNTIME_NAME = "ollama";

    OllamaClient ollamaClient;
    OllamaProperties ollamaProperties;
    AnalysisPromptBuilder analysisPromptBuilder;
    AiOutputParser aiOutputParser;

    @Override
    public String getRuntimeName() {
        return RUNTIME_NAME;
    }

    @Override
    public AiAnalysis analyze(MonitoringEvent event, List<RuleFinding> ruleFindings) {
        String model = ollamaProperties.getModel();
        if (!StringUtils.hasText(model)) {
            return AiAnalysis.notCompleted(AnalysisStatus.UNAVAILABLE, "No Ollama model is configured", null, 0);
        }

        long startedAt = System.nanoTime();
        AnalysisPrompt prompt = analysisPromptBuilder.build(event, ruleFindings);
        try {
            OllamaChatResponse response = ollamaClient.chat(OllamaChatRequest.builder()
                    .model(model)
                    .messages(List.of(
                            OllamaChatMessage.builder().role("system").content(prompt.getSystemPrompt()).build(),
                            OllamaChatMessage.builder().role("user").content(prompt.getUserPrompt()).build()))
                    .stream(false)
                    .format(prompt.getOutputSchema())
                    .think(ollamaProperties.getThink())
                    .options(options())
                    .build());

            AiAssessmentOutput output = aiOutputParser.parse(response.getMessage().getContent());
            return AiAnalysis.builder()
                    .status(AnalysisStatus.COMPLETED)
                    .statusDetail(prompt.isEventDataTruncated() ? "Event data was truncated before analysis" : null)
                    .schemaVersion(AiAssessmentOutput.SCHEMA_VERSION)
                    .assessment(output.getAssessment())
                    .confidence(output.getConfidence())
                    .explanation(output.getExplanation())
                    .possibleCauses(output.getPossibleCauses())
                    .missingEvidence(output.getMissingEvidence())
                    .suggestedReview(output.isSuggestedReview())
                    .modelName(model)
                    .analysisDurationMs(elapsedMillis(startedAt))
                    .build();
        } catch (OllamaException ex) {
            log.warn("Ollama analysis of event {} failed with {}", event.getEventId(), ex.getType());
            log.debug("Ollama failure detail: {}", ex.getMessage());
            return AiAnalysis.notCompleted(toAnalysisStatus(ex.getType()), describe(ex.getType()), model,
                    elapsedMillis(startedAt));
        } catch (InvalidAiOutputException ex) {
            log.warn("Ollama returned an invalid analysis for event {}: {}", event.getEventId(), ex.getMessage());
            return AiAnalysis.notCompleted(AnalysisStatus.INVALID_RESPONSE,
                    "Model output was rejected: " + ex.getMessage(), model, elapsedMillis(startedAt));
        }
    }

    @Override
    public AiRuntimeStatus checkRuntimeStatus() {
        String model = ollamaProperties.getModel();
        if (!StringUtils.hasText(model)) {
            return runtimeStatus(AiRuntimeState.NOT_CONFIGURED, null, "No Ollama model is configured");
        }
        try {
            boolean installed = ollamaClient.listModelNames().stream().anyMatch(name -> matchesModel(name, model));
            return installed
                    ? runtimeStatus(AiRuntimeState.AVAILABLE, model, "Ollama is reachable and the model is installed")
                    : runtimeStatus(AiRuntimeState.MODEL_NOT_INSTALLED, model, "The configured model is not installed; run: ollama pull " + model);
        } catch (OllamaException ex) {
            log.debug("Ollama status check failed: {}", ex.getMessage());
            return runtimeStatus(AiRuntimeState.UNREACHABLE, model, "Ollama could not be reached");
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

    private boolean matchesModel(String installedName, String configuredModel) {
        return installedName.equals(configuredModel)
                || (!configuredModel.contains(":") && installedName.equals(configuredModel + ":latest"));
    }

    private AiRuntimeStatus runtimeStatus(AiRuntimeState state, String model, String detail) {
        return AiRuntimeStatus.builder()
                .runtime(RUNTIME_NAME)
                .state(state)
                .modelName(model)
                .detail(detail)
                .build();
    }

    private AnalysisStatus toAnalysisStatus(OllamaErrorType type) {
        return switch (type) {
            case CONNECTION_FAILED, MODEL_NOT_FOUND -> AnalysisStatus.UNAVAILABLE;
            case TIMEOUT -> AnalysisStatus.TIMEOUT;
            case EMPTY_OUTPUT, INVALID_RESPONSE -> AnalysisStatus.INVALID_RESPONSE;
            case HTTP_ERROR, INTERRUPTED -> AnalysisStatus.FAILED;
        };
    }

    private String describe(OllamaErrorType type) {
        return switch (type) {
            case CONNECTION_FAILED -> "Ollama could not be reached";
            case MODEL_NOT_FOUND -> "The configured model is not installed in Ollama";
            case TIMEOUT -> "Ollama did not respond within the configured timeout";
            case EMPTY_OUTPUT -> "The model returned an empty response";
            case HTTP_ERROR -> "Ollama returned an error response";
            case INVALID_RESPONSE -> "Ollama returned a response that could not be read";
            case INTERRUPTED -> "The analysis request was interrupted";
        };
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
