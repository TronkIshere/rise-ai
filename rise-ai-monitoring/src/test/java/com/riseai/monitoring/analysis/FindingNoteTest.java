package com.riseai.monitoring.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.configuration.OllamaProperties;
import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.core.finding.FindingCategory;
import com.riseai.monitoring.core.finding.FindingFact;
import com.riseai.monitoring.core.finding.RecommendedAction;
import com.riseai.monitoring.ollama.OllamaClient;
import com.riseai.monitoring.ollama.OllamaErrorType;
import com.riseai.monitoring.ollama.OllamaException;
import com.riseai.monitoring.ollama.dto.OllamaChatMessage;
import com.riseai.monitoring.ollama.dto.OllamaChatRequest;
import com.riseai.monitoring.ollama.dto.OllamaChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindingNoteTest {
    private static final String UUID_VALUE = "3f2b8c1e-9d4a-4e6b-8a7c-1d2e3f4a5b6c";
    private static final String HASH = "a".repeat(64);
    private static final String FREE_TEXT = "Contact Nguyen Van A at a@b.vn about invoice";

    private final MonitoringProperties properties = new MonitoringProperties();
    private final OllamaClient client = mock(OllamaClient.class);
    private final FindingNoteService service = new FindingNoteService(properties, new OllamaProperties(), client, new ObjectMapper());

    private Finding finding() {
        return Finding.builder().findingId(HASH).ruleId("rise.evidence.duplicate-file-hash").ruleVersion("1")
                .category(FindingCategory.EVIDENCE_INTEGRITY).severity(Severity.HIGH)
                .subjectType("EVIDENCE").subjectId(UUID_VALUE)
                .references(Map.of("campaignId", UUID_VALUE))
                .facts(List.of(new FindingFact("matchCount", 2, UUID_VALUE), new FindingFact("fileHash", HASH, null),
                        new FindingFact("evidenceType", "INVOICE", null), new FindingFact("amount", "60000000.00", null), new FindingFact("note", FREE_TEXT, null),
                        new FindingFact("evidenceId", UUID_VALUE, null)))
                .explanation("The same file hash was submitted twice " + FREE_TEXT)
                .recommendedAction(RecommendedAction.REVIEW_EVIDENCE).observedAt(Instant.now()).build();
    }

    private void enable() {
        properties.getAi().setEnabled(true);
        properties.getRise().setAiNotesEnabled(true);
    }

    private void modelAnswers(String content) {
        when(client.chat(any())).thenReturn(OllamaChatResponse.builder()
                .message(OllamaChatMessage.builder().role("assistant").content(content).build()).build());
    }

    @Test
    void promptContainsOnlyAllowlistedScalarFacts() {
        FindingNotePrompt prompt = FindingNotePrompt.build(finding());
        String all = prompt.systemPrompt() + prompt.userPrompt();
        assertThat(prompt.userPrompt()).contains("rule: rise.evidence.duplicate-file-hash", "fact matchCount: 2", "fact evidenceType: INVOICE", "fact amount: 60000000.00");
        assertThat(all).doesNotContain(UUID_VALUE, HASH, "Nguyen", "a@b.vn", "invoice", "fileHash", "evidenceId", "note:");
    }

    @Test
    void filterRejectsForbiddenLanguageIdentifiersAndLongText() {
        assertThat(FindingNoteFilter.accept("Two submissions share the same file hash, so a reviewer may compare them.", 400)).isPresent();
        assertThat(FindingNoteFilter.accept("This is definitely fraud.", 400)).isEmpty();
        assertThat(FindingNoteFilter.accept("Please release the funds now.", 400)).isEmpty();
        assertThat(FindingNoteFilter.accept("See " + UUID_VALUE, 400)).isEmpty();
        assertThat(FindingNoteFilter.accept("See https://example.com", 400)).isEmpty();
        assertThat(FindingNoteFilter.accept("x".repeat(401), 400)).isEmpty();
        assertThat(FindingNoteFilter.accept("   ", 400)).isEmpty();
    }

    @Test
    void validNoteIsAttachedAndNeverChangesSeverityOrFacts() {
        enable();
        modelAnswers("{\"note\":\"The same file hash appears in two submissions, so a reviewer may want to compare them.\"}");
        Finding original = finding();

        Finding annotated = service.annotate(List.of(original)).get(0);

        assertThat(annotated.getAiNote()).contains("same file hash");
        assertThat(annotated.getSeverity()).isEqualTo(original.getSeverity());
        assertThat(annotated.getFindingId()).isEqualTo(original.getFindingId());
        assertThat(annotated.getFacts()).isEqualTo(original.getFacts());
        assertThat(annotated.getRecommendedAction()).isEqualTo(original.getRecommendedAction());
        ArgumentCaptor<OllamaChatRequest> request = ArgumentCaptor.forClass(OllamaChatRequest.class);
        verify(client).chat(request.capture());
        assertThat(request.getValue().getMessages().get(1).getContent()).doesNotContain(UUID_VALUE, HASH, "Nguyen");
    }

    @Test
    void forbiddenOutputInvalidJsonAndModelFailureLeaveTheFindingIntact() {
        enable();
        Finding original = finding();
        modelAnswers("{\"note\":\"This is clear fraud by the creator.\"}");
        assertThat(service.annotate(List.of(original)).get(0).getAiNote()).isNull();
        modelAnswers("not json");
        assertThat(service.annotate(List.of(original)).get(0).getAiNote()).isNull();
        when(client.chat(any())).thenThrow(new OllamaException(OllamaErrorType.CONNECTION_FAILED, "down"));
        Finding result = service.annotate(List.of(original)).get(0);
        assertThat(result.getAiNote()).isNull();
        assertThat(result.getSeverity()).isEqualTo(original.getSeverity());
    }

    @Test
    void noNewModelCallIsStartedOnceTheNoteBudgetIsSpent() {
        enable();
        properties.getRise().setAiNoteBudget(java.time.Duration.ZERO);
        List<Finding> result = service.annotate(List.of(finding(), finding()));
        assertThat(result).hasSize(2).allSatisfy(finding -> assertThat(finding.getAiNote()).isNull());
        verify(client, never()).chat(any());
    }

    @Test
    void disabledByDefaultNeverCallsTheModel() {
        properties.getAi().setEnabled(true);
        assertThat(service.annotate(List.of(finding())).get(0).getAiNote()).isNull();
        properties.getAi().setEnabled(false);
        properties.getRise().setAiNotesEnabled(true);
        assertThat(service.annotate(List.of(finding())).get(0).getAiNote()).isNull();
        verify(client, never()).chat(any());
    }
}
