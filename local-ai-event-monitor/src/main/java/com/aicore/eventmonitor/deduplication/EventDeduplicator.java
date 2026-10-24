package com.aicore.eventmonitor.deduplication;

import com.aicore.eventmonitor.core.event.MonitoringEvent;
import com.aicore.eventmonitor.core.result.MonitoringResult;

public interface EventDeduplicator {

    DeduplicationOutcome register(MonitoringEvent event);

    void complete(MonitoringEvent event, MonitoringResult result);

    void release(MonitoringEvent event);

    int getTrackedEventCount();

    String getStoreType();
}
