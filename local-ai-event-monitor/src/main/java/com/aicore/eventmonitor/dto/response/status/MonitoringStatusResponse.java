package com.aicore.eventmonitor.dto.response.status;

import com.aicore.eventmonitor.configuration.MonitoringProperties;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MonitoringStatusResponse {
    boolean ruleEngineEnabled;
    boolean aiAnalysisEnabled;
    MonitoringProperties.AiTrigger aiTrigger;
    MonitoringProperties.AiFailureMode aiFailureMode;
    String eventPersistence;
    boolean deduplicationEnabled;
    String deduplicationStore;
    String deduplicationScope;
    String deduplicationRetention;
    int maxTrackedEvents;
    int trackedEventCount;
    AiRuntimeStatusResponse aiRuntime;
}
