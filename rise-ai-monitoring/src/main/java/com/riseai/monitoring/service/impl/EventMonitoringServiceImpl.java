package com.riseai.monitoring.service.impl;

import com.riseai.monitoring.analysis.FindingNoteService;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.constants.PlatformConstants;
import com.riseai.monitoring.core.analysis.AiAnalysis;
import com.riseai.monitoring.core.analysis.AiEventAnalyzer;
import com.riseai.monitoring.core.analysis.AnalysisStatus;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.core.result.MonitoringResult;
import com.riseai.monitoring.core.result.MonitoringResultPublisher;
import com.riseai.monitoring.core.result.MonitoringStatus;
import com.riseai.monitoring.core.rule.RuleDecision;
import com.riseai.monitoring.core.rule.RuleEvaluation;
import com.riseai.monitoring.core.rule.RuleFinding;
import com.riseai.monitoring.deduplication.DeduplicationOutcome;
import com.riseai.monitoring.deduplication.EventDeduplicator;
import com.riseai.monitoring.dto.request.event.MonitoringEventRequest;
import com.riseai.monitoring.dto.response.monitoring.MonitoringResultResponse;
import com.riseai.monitoring.exception.ApplicationException;
import com.riseai.monitoring.exception.ErrorCode;
import com.riseai.monitoring.mapper.MonitoringResultMapper;
import com.riseai.monitoring.normalization.EventNormalizer;
import com.riseai.monitoring.observability.MonitoringMetrics;
import com.riseai.monitoring.result.MonitoringResultAssembler;
import com.riseai.monitoring.rise.RiseFindingEngine;
import com.riseai.monitoring.rule.RuleEngine;
import com.riseai.monitoring.service.EventMonitoringService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventMonitoringServiceImpl implements EventMonitoringService {
    List<MonitoringResultPublisher> monitoringResultPublishers;
    MonitoringResultAssembler monitoringResultAssembler;
    MonitoringResultMapper monitoringResultMapper;
    EventDeduplicator eventDeduplicator;
    MonitoringMetrics monitoringMetrics;
    AiEventAnalyzer aiEventAnalyzer;
    MonitoringProperties properties;
    EventNormalizer eventNormalizer;
    RuleEngine ruleEngine;
    RiseFindingEngine riseFindingEngine;
    FindingNoteService findingNoteService;
    Clock clock;

    @Override
    public MonitoringResultResponse monitor(MonitoringEventRequest request) {
        long startedAt = System.nanoTime();
        monitoringMetrics.eventReceived();
        MonitoringEvent event = normalize(request);

        String previousEventId = MDC.get(PlatformConstants.EVENT_ID_MDC_KEY);
        MDC.put(PlatformConstants.EVENT_ID_MDC_KEY, event.getEventId());
        try {
            return handleDeduplicationOutcome(eventDeduplicator.register(event), event, startedAt);
        } finally {
            restoreEventId(previousEventId);
        }
    }

    private MonitoringEvent normalize(MonitoringEventRequest request) {
        try {
            return eventNormalizer.normalize(request, resolveCorrelationId());
        } catch (ApplicationException ex) {
            monitoringMetrics.eventRejected("validation");
            throw ex;
        }
    }

    private MonitoringResultResponse handleDeduplicationOutcome(DeduplicationOutcome outcome, MonitoringEvent event,
                                                                long startedAt) {
        return switch (outcome.getStatus()) {
            case NEW -> process(event, startedAt);
            case DUPLICATE -> {
                monitoringMetrics.duplicateEvent("replayed");
                yield monitoringResultMapper.toResponse(outcome.getPreviousResult(), true);
            }
            case CONFLICT -> {
                monitoringMetrics.duplicateEvent("conflict");
                throw new ApplicationException(ErrorCode.EVENT_ID_CONFLICT, event.getEventId());
            }
            case IN_PROGRESS -> {
                monitoringMetrics.duplicateEvent("in_progress");
                throw new ApplicationException(ErrorCode.EVENT_IN_PROGRESS, event.getEventId());
            }
            case CAPACITY_EXHAUSTED -> throw new ApplicationException(ErrorCode.DEDUPLICATION_CAPACITY_EXHAUSTED);
        };
    }

    private MonitoringResultResponse process(MonitoringEvent event, long startedAt) {
        MonitoringResult result = evaluate(event, startedAt);
        if (result.getMonitoringStatus() == MonitoringStatus.INCONCLUSIVE) {
            eventDeduplicator.release(event);
        } else {
            eventDeduplicator.complete(event, result);
        }
        monitoringMetrics.resultProduced(result);
        publish(result);
        return monitoringResultMapper.toResponse(result, false);
    }

    private MonitoringResult evaluate(MonitoringEvent event, long startedAt) {
        try {
            RuleEvaluation evaluation = ruleEngine.evaluate(event);
            List<Finding> findings = evaluation.getDecision() == RuleDecision.REJECT ? List.of() : riseFindingEngine.evaluate(event);
            findings = findingNoteService.annotate(findings);
            if (!findings.isEmpty()) {
                evaluation = ruleEngine.withAdditionalFindings(event, evaluation, findings.stream()
                        .map(finding -> RuleFinding.flag(finding.getRuleId(), finding.getSeverity(), finding.getExplanation()))
                        .toList());
                findings.forEach(monitoringMetrics::findingProduced);
            }
            AiAnalysis aiAnalysis = analyzeIfRequired(event, evaluation);
            return monitoringResultAssembler.assemble(event, evaluation, aiAnalysis, findings, clock.instant(),
                    (System.nanoTime() - startedAt) / 1_000_000);
        } catch (RuntimeException | Error ex) {
            eventDeduplicator.release(event);
            throw ex;
        }
    }

    private AiAnalysis analyzeIfRequired(MonitoringEvent event, RuleEvaluation evaluation) {
        return switch (evaluation.getDecision()) {
            case REJECT -> AiAnalysis.skipped("Event was rejected by deterministic rules");
            case RULES_SUFFICIENT -> AiAnalysis.skipped("Deterministic rules did not require AI analysis");
            case REQUIRES_AI_ANALYSIS -> properties.getAi().isEnabled()
                    ? analyze(event, evaluation)
                    : AiAnalysis.disabled("AI analysis is disabled by configuration");
        };
    }

    private AiAnalysis analyze(MonitoringEvent event, RuleEvaluation evaluation) {
        monitoringMetrics.aiAnalysisAttempted();
        AiAnalysis analysis = invokeAnalyzer(event, evaluation);
        monitoringMetrics.aiAnalysisFinished(analysis.getStatus());
        return analysis;
    }

    private AiAnalysis invokeAnalyzer(MonitoringEvent event, RuleEvaluation evaluation) {
        try {
            AiAnalysis analysis = aiEventAnalyzer.analyze(event, evaluation.getFindings());
            if (analysis != null && analysis.getStatus() != null) {
                return analysis;
            }
        } catch (RuntimeException ex) {
            log.error("AI analyzer failed for event {}", event.getEventId(), ex);
        }
        return AiAnalysis.notCompleted(AnalysisStatus.FAILED, "AI analysis failed unexpectedly", null, 0);
    }

    private void publish(MonitoringResult result) {
        for (MonitoringResultPublisher publisher : monitoringResultPublishers) {
            try {
                publisher.publish(result);
            } catch (RuntimeException ex) {
                log.warn("Monitoring result publisher {} failed for event {}",
                        publisher.getClass().getSimpleName(), result.getEventId(), ex);
            }
        }
    }

    private String resolveCorrelationId() {
        String inherited = MDC.get(PlatformConstants.CORRELATION_ID_MDC_KEY);
        if (inherited != null && PlatformConstants.CORRELATION_ID_PATTERN.matcher(inherited).matches()) {
            return inherited;
        }
        return UUID.randomUUID().toString();
    }

    private void restoreEventId(String previousEventId) {
        if (previousEventId == null) {
            MDC.remove(PlatformConstants.EVENT_ID_MDC_KEY);
        } else {
            MDC.put(PlatformConstants.EVENT_ID_MDC_KEY, previousEventId);
        }
    }
}
