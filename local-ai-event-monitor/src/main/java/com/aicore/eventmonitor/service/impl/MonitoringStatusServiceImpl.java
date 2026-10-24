package com.aicore.eventmonitor.service.impl;

import com.aicore.eventmonitor.configuration.MonitoringProperties;
import com.aicore.eventmonitor.core.analysis.AiEventAnalyzer;
import com.aicore.eventmonitor.core.analysis.AiRuntimeState;
import com.aicore.eventmonitor.core.analysis.AiRuntimeStatus;
import com.aicore.eventmonitor.deduplication.EventDeduplicator;
import com.aicore.eventmonitor.dto.response.status.AiRuntimeStatusResponse;
import com.aicore.eventmonitor.dto.response.status.MonitoringStatusResponse;
import com.aicore.eventmonitor.dto.response.status.RuleResponse;
import com.aicore.eventmonitor.rule.RuleEngine;
import com.aicore.eventmonitor.service.MonitoringStatusService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MonitoringStatusServiceImpl implements MonitoringStatusService {

    private static final String EVENT_PERSISTENCE_NONE = "NONE";
    private static final String DEDUPLICATION_SCOPE = "source+eventId, single application instance";

    AiEventAnalyzer aiEventAnalyzer;
    RuleEngine ruleEngine;
    EventDeduplicator eventDeduplicator;
    MonitoringProperties properties;

    @Override
    public MonitoringStatusResponse getStatus() {
        MonitoringProperties.Ai ai = properties.getAi();
        return MonitoringStatusResponse.builder()
                .ruleEngineEnabled(properties.getRules().isEnabled())
                .aiAnalysisEnabled(ai.isEnabled())
                .aiTrigger(ai.getTrigger())
                .aiFailureMode(ai.getOnFailure())
                .eventPersistence(EVENT_PERSISTENCE_NONE)
                .deduplicationEnabled(properties.getDeduplication().isEnabled())
                .deduplicationStore(eventDeduplicator.getStoreType())
                .deduplicationScope(DEDUPLICATION_SCOPE)
                .deduplicationRetention(properties.getDeduplication().getRetention().toString())
                .maxTrackedEvents(properties.getDeduplication().getMaxTrackedEvents())
                .trackedEventCount(eventDeduplicator.getTrackedEventCount())
                .aiRuntime(toRuntimeResponse(ai.isEnabled() ? checkRuntime() : disabledRuntime()))
                .build();
    }

    @Override
    public List<RuleResponse> getRules() {
        return ruleEngine.getRules().stream()
                .map(rule -> RuleResponse.builder()
                        .id(rule.getId())
                        .description(rule.getDescription())
                        .category(rule.getCategory())
                        .effect(rule.getEffect())
                        .active(ruleEngine.isActive(rule))
                        .build())
                .toList();
    }

    private AiRuntimeStatus checkRuntime() {
        try {
            return aiEventAnalyzer.checkRuntimeStatus();
        } catch (RuntimeException ex) {
            log.warn("AI runtime status check failed", ex);
            return AiRuntimeStatus.builder()
                    .runtime(aiEventAnalyzer.getRuntimeName())
                    .state(AiRuntimeState.UNREACHABLE)
                    .detail("AI runtime status could not be determined")
                    .build();
        }
    }

    private AiRuntimeStatus disabledRuntime() {
        return AiRuntimeStatus.builder()
                .runtime(aiEventAnalyzer.getRuntimeName())
                .state(AiRuntimeState.DISABLED)
                .detail("AI analysis is disabled by configuration")
                .build();
    }

    private AiRuntimeStatusResponse toRuntimeResponse(AiRuntimeStatus status) {
        return AiRuntimeStatusResponse.builder()
                .runtime(status.getRuntime())
                .state(status.getState())
                .modelName(status.getModelName())
                .detail(status.getDetail())
                .build();
    }
}
