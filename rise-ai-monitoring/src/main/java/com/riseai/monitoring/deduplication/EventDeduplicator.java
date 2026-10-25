package com.riseai.monitoring.deduplication;

import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.result.MonitoringResult;

public interface EventDeduplicator {

    DeduplicationOutcome register(MonitoringEvent event);

    void complete(MonitoringEvent event, MonitoringResult result);

    void release(MonitoringEvent event);

    int getTrackedEventCount();

    String getStoreType();
}
