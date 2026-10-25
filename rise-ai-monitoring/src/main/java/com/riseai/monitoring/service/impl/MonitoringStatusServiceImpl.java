package com.riseai.monitoring.service.impl;

import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.analysis.AiEventAnalyzer;
import com.riseai.monitoring.core.analysis.AiRuntimeState;
import com.riseai.monitoring.core.analysis.AiRuntimeStatus;
import com.riseai.monitoring.deduplication.EventDeduplicator;
import com.riseai.monitoring.dto.response.status.AiRuntimeStatusResponse;
import com.riseai.monitoring.dto.response.status.MonitoringStatusResponse;
import com.riseai.monitoring.dto.response.status.RuleResponse;
import com.riseai.monitoring.rule.RuleEngine;
import com.riseai.monitoring.service.MonitoringStatusService;
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
    private static final String RISE_PROJECTION_ENABLED = "MINIMIZED_PROJECTION";
    private static final String RISE_PROJECTION_DISABLED = "DISABLED";
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
                .riseProjection(properties.getRise().isEnabled() ? RISE_PROJECTION_ENABLED : RISE_PROJECTION_DISABLED)
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
