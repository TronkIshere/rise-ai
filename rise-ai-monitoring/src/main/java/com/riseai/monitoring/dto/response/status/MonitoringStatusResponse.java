package com.riseai.monitoring.dto.response.status;

import com.riseai.monitoring.configuration.MonitoringProperties;
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
    String riseProjection;
    boolean deduplicationEnabled;
    String deduplicationStore;
    String deduplicationScope;
    String deduplicationRetention;
    int maxTrackedEvents;
    int trackedEventCount;
    AiRuntimeStatusResponse aiRuntime;
}
