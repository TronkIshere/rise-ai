package com.riseai.monitoring.deduplication;

import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.dto.response.monitoring.MonitoringResultResponse;

public interface EventDeduplicator {

    DeduplicationOutcome register(MonitoringEvent event);

    void complete(MonitoringEvent event, MonitoringResultResponse response);

    void release(MonitoringEvent event);

    int getTrackedEventCount();

    String getStoreType();

    String getScope();
}
