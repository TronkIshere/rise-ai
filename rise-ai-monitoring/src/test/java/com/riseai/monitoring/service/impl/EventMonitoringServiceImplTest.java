package com.riseai.monitoring.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.riseai.monitoring.analysis.AiOutputParser;
import com.riseai.monitoring.analysis.AnalysisPromptBuilder;
import com.riseai.monitoring.analysis.RiskLevelNormalizer;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.configuration.OllamaProperties;
import com.riseai.monitoring.core.analysis.AiAnalysis;
import com.riseai.monitoring.core.analysis.AiAssessment;
import com.riseai.monitoring.core.analysis.AiEventAnalyzer;
import com.riseai.monitoring.core.analysis.AnalysisStatus;
import com.riseai.monitoring.core.analysis.RiskLevel;
import com.riseai.monitoring.core.result.MonitoringResultPublisher;
import com.riseai.monitoring.core.result.MonitoringStatus;
import com.riseai.monitoring.core.rule.RuleDecision;
import com.riseai.monitoring.deduplication.InMemoryEventDeduplicator;
import com.riseai.monitoring.dto.request.event.MonitoringEventRequest;
import com.riseai.monitoring.dto.response.monitoring.MonitoringResultResponse;
import com.riseai.monitoring.exception.ApplicationException;
import com.riseai.monitoring.exception.ErrorCode;
import com.riseai.monitoring.mapper.MonitoringResultMapper;
import com.riseai.monitoring.normalization.EventNormalizer;
import com.riseai.monitoring.observability.MonitoringMetrics;
import com.riseai.monitoring.ollama.OllamaClient;
import com.riseai.monitoring.ollama.OllamaEventAnalyzer;
import com.riseai.monitoring.result.MonitoringResultAssembler;
import com.riseai.monitoring.rise.RiseEnvelopeReader;
import com.riseai.monitoring.rise.RiseFindingEngine;
import com.riseai.monitoring.rule.AttributeThresholdRule;
import com.riseai.monitoring.rule.EventTypeRule;
import com.riseai.monitoring.rule.PayloadLimitsRule;
import com.riseai.monitoring.rule.ReportedSeverityRule;
import com.riseai.monitoring.rule.RequiredFieldsRule;
import com.riseai.monitoring.rule.RuleEngine;
import com.riseai.monitoring.rule.TimestampRule;
import com.riseai.monitoring.testsupport.FakeAiEventAnalyzer;
import com.riseai.monitoring.testsupport.MutableClock;
import com.riseai.monitoring.testsupport.RecordingResultPublisher;
import com.riseai.monitoring.testsupport.StubOllamaServer;
import com.riseai.monitoring.testsupport.TestEvents;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventMonitoringServiceImplTest {

    private MonitoringProperties properties;
    private MutableClock clock;
    private FakeAiEventAnalyzer analyzer;
    private RecordingResultPublisher publisher;
    private InMemoryEventDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        properties = TestEvents.properties();
        clock = new MutableClock(TestEvents.NOW);
        analyzer = new FakeAiEventAnalyzer();
        publisher = new RecordingResultPublisher();
        deduplicator = new InMemoryEventDeduplicator(properties, clock);
    }

    @Test
    void rulesOnlyEventCompletesWithoutCallingAi() {
        MonitoringResultResponse result = service().monitor(TestEvents.request("evt-1"));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.COMPLETED);
        assertThat(result.getRuleDecision()).isEqualTo(RuleDecision.RULES_SUFFICIENT);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.getAiAnalysis().getAnalysisStatus()).isEqualTo(AnalysisStatus.SKIPPED);
        assertThat(result.isDuplicate()).isFalse();
        assertThat(analyzer.getInvocationCount()).isZero();
        assertThat(publisher.getResults()).hasSize(1);
    }

    @Test
    void flaggedEventIsAnalyzedAndBothFindingSourcesArePreserved() {
        analyzer.returns(TestEvents.completedAnalysis(AiAssessment.LIKELY_ANOMALOUS, true));

        MonitoringResultResponse result = service().monitor(flaggedRequest("evt-1"));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.COMPLETED);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.isReviewRequired()).isTrue();
        assertThat(result.getRuleFindings()).hasSize(1);
        assertThat(result.getRuleFindings().get(0).getRuleId()).isEqualTo(AttributeThresholdRule.ID);
        assertThat(result.getAiAnalysis().getAssessment()).isEqualTo(AiAssessment.LIKELY_ANOMALOUS);
        assertThat(result.getAiAnalysis().getPossibleCauses()).containsExactly("Upstream dependency slowness");
        assertThat(analyzer.getLastReceivedFindings()).hasSize(1);
    }

    @Test
    void unavailableAiKeepsRuleFindingsAndIsInconclusive() {
        analyzer.returns(AiAnalysis.notCompleted(AnalysisStatus.UNAVAILABLE, "Ollama could not be reached", "test-model", 2));

        MonitoringResultResponse result = service().monitor(flaggedRequest("evt-1"));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.INCONCLUSIVE);
        assertThat(result.getAiAnalysis().getAnalysisStatus()).isEqualTo(AnalysisStatus.UNAVAILABLE);
        assertThat(result.getRuleFindings()).hasSize(1);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void aiTimeoutIsNeverTreatedAsSafe() {
        properties.getAi().setTrigger(MonitoringProperties.AiTrigger.ALWAYS);
        analyzer.returns(AiAnalysis.notCompleted(AnalysisStatus.TIMEOUT, "timeout", "test-model", 500));

        MonitoringResultResponse result = service().monitor(TestEvents.request("evt-1"));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.INCONCLUSIVE);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.UNKNOWN);
    }

    @Test
    void disabledAiSkipsAnalysis() {
        properties.getAi().setEnabled(false);

        MonitoringResultResponse result = service().monitor(flaggedRequest("evt-1"));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.COMPLETED_WITHOUT_AI);
        assertThat(result.getAiAnalysis().getAnalysisStatus()).isEqualTo(AnalysisStatus.DISABLED);
        assertThat(result.getRuleFindings()).hasSize(1);
        assertThat(result.getAiAnalysis().getStatusDetail()).contains("disabled");
        assertThat(analyzer.getInvocationCount()).isZero();
    }

    @Test
    void analyzerExceptionsBecomeFailedAnalyses() {
        analyzer.fails(new IllegalStateException("analyzer bug"));

        MonitoringResultResponse result = service().monitor(flaggedRequest("evt-1"));

        assertThat(result.getAiAnalysis().getAnalysisStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.INCONCLUSIVE);
        assertThat(result.getRuleFindings()).hasSize(1);
    }

    @Test
    void rejectedEventsAreNeverSentToAi() {
        MonitoringEventRequest request = flaggedRequest("evt-1");
        request.setOccurredAt(TestEvents.NOW.plus(Duration.ofHours(2)));

        MonitoringResultResponse result = service().monitor(request);

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.REJECTED);
        assertThat(result.getOverallRiskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(analyzer.getInvocationCount()).isZero();
    }

    @Test
    void duplicateEventReplaysTheOriginalResultWithoutReprocessing() {
        analyzer.returns(TestEvents.completedAnalysis(AiAssessment.NEEDS_REVIEW, true));
        EventMonitoringServiceImpl service = service();

        MonitoringResultResponse first = service.monitor(flaggedRequest("evt-1"));
        MonitoringResultResponse second = service.monitor(flaggedRequest("evt-1"));

        assertThat(second.isDuplicate()).isTrue();
        assertThat(second.getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertThat(second.getMonitoringStatus()).isEqualTo(first.getMonitoringStatus());
        assertThat(analyzer.getInvocationCount()).isEqualTo(1);
        assertThat(publisher.getResults()).hasSize(1);
    }

    @Test
    void reusedEventIdWithDifferentContentIsAConflict() {
        EventMonitoringServiceImpl service = service();
        service.monitor(TestEvents.request("evt-1"));
        MonitoringEventRequest changed = TestEvents.request("evt-1");
        changed.setPayload(JsonNodeFactory.instance.objectNode().put("path", "/other"));

        assertThatThrownBy(() -> service.monitor(changed))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EVENT_ID_CONFLICT);
    }

    @Test
    void inconclusiveResultsAreNotRetainedSoEventsCanBeRetried() {
        EventMonitoringServiceImpl service = service();
        analyzer.returns(AiAnalysis.notCompleted(AnalysisStatus.UNAVAILABLE, "offline", "test-model", 1));
        service.monitor(flaggedRequest("evt-1"));

        analyzer.returns(TestEvents.completedAnalysis(AiAssessment.CONSISTENT_WITH_EXPECTED_BEHAVIOR, false));
        MonitoringResultResponse retry = service.monitor(flaggedRequest("evt-1"));

        assertThat(retry.isDuplicate()).isFalse();
        assertThat(retry.getMonitoringStatus()).isEqualTo(MonitoringStatus.COMPLETED);
        assertThat(analyzer.getInvocationCount()).isEqualTo(2);
    }

    @Test
    void concurrentDuplicateSubmissionsAreProcessedOnce() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        analyzer.blocksUntilReleased(entered, release, TestEvents.completedAnalysis(AiAssessment.NEEDS_REVIEW, true));
        EventMonitoringServiceImpl service = service();
        ExecutorService pool = Executors.newSingleThreadExecutor();

        Future<MonitoringResultResponse> first = pool.submit(() -> service.monitor(flaggedRequest("evt-1")));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> service.monitor(flaggedRequest("evt-1")))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EVENT_IN_PROGRESS);
        release.countDown();
        MonitoringResultResponse firstResult = first.get(5, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(firstResult.getMonitoringStatus()).isEqualTo(MonitoringStatus.COMPLETED);
        assertThat(service.monitor(flaggedRequest("evt-1")).isDuplicate()).isTrue();
        assertThat(analyzer.getInvocationCount()).isEqualTo(1);
    }

    @Test
    void eventIdAndCorrelationIdStayDistinct() {
        MonitoringEventRequest request = TestEvents.request("evt-1");
        request.setCorrelationId("trace-9");

        MonitoringResultResponse result = service().monitor(request);

        assertThat(result.getEventId()).isEqualTo("evt-1");
        assertThat(result.getCorrelationId()).isEqualTo("trace-9");
    }

    @Test
    void disabledAiMakesNoNetworkCallToOllama() {
        properties.getAi().setEnabled(false);
        try (StubOllamaServer ollama = StubOllamaServer.start()) {
            OllamaProperties ollamaProperties = new OllamaProperties();
            ollamaProperties.setBaseUrl(URI.create(ollama.getBaseUrl()));
            ollamaProperties.setModel("qwen3.5:9b");
            ObjectMapper objectMapper = new ObjectMapper();
            OllamaClient client = new OllamaClient(ollamaProperties, objectMapper);
            AiEventAnalyzer realAnalyzer = new OllamaEventAnalyzer(client, ollamaProperties,
                    new AnalysisPromptBuilder(objectMapper, properties), new AiOutputParser(objectMapper, properties));

            MonitoringResultResponse result = service(List.of(publisher), realAnalyzer).monitor(flaggedRequest("evt-1"));

            assertThat(result.getAiAnalysis().getAnalysisStatus()).isEqualTo(AnalysisStatus.DISABLED);
            assertThat(ollama.getRequestPaths()).isEmpty();
            client.destroy();
        }
    }

    @Test
    void invalidEventsAreRejectedBeforeDeduplication() {
        MonitoringEventRequest request = TestEvents.request("evt-1");
        request.getAttributes().put("nested", Map.of("key", 1));

        assertThatThrownBy(() -> service().monitor(request))
                .isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_EVENT);
        assertThat(deduplicator.getTrackedEventCount()).isZero();
    }

    @Test
    void publisherFailuresDoNotAffectTheResult() {
        MonitoringResultPublisher brokenPublisher = result -> {
            throw new IllegalStateException("publisher down");
        };

        MonitoringResultResponse result = service(List.of(brokenPublisher, publisher)).monitor(TestEvents.request("evt-1"));

        assertThat(result.getMonitoringStatus()).isEqualTo(MonitoringStatus.COMPLETED);
        assertThat(publisher.getResults()).hasSize(1);
    }

    private MonitoringEventRequest flaggedRequest(String eventId) {
        MonitoringEventRequest request = TestEvents.request(eventId);
        request.getAttributes().put("latencyMs", 5000);
        return request;
    }

    private EventMonitoringServiceImpl service() {
        return service(List.of(publisher));
    }

    private EventMonitoringServiceImpl service(List<MonitoringResultPublisher> publishers) {
        return service(publishers, analyzer);
    }

    private EventMonitoringServiceImpl service(List<MonitoringResultPublisher> publishers, AiEventAnalyzer aiEventAnalyzer) {
        RuleEngine ruleEngine = new RuleEngine(List.of(new RequiredFieldsRule(properties), new TimestampRule(properties, clock),
                new EventTypeRule(properties), new PayloadLimitsRule(properties), new AttributeThresholdRule(properties),
                new ReportedSeverityRule(properties)), properties);
        return new EventMonitoringServiceImpl(
                publishers,
                new MonitoringResultAssembler(properties, new RiskLevelNormalizer()),
                new MonitoringResultMapper(),
                deduplicator,
                new MonitoringMetrics(new SimpleMeterRegistry()),
                aiEventAnalyzer,
                properties,
                new EventNormalizer(new ObjectMapper(), properties, clock),
                ruleEngine,
                new RiseFindingEngine(new RiseEnvelopeReader(properties), null, List.of(), properties, clock),
                clock);
    }
}
