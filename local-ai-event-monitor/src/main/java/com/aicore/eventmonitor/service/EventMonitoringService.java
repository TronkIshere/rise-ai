package com.aicore.eventmonitor.service;

import com.aicore.eventmonitor.dto.request.event.MonitoringEventRequest;
import com.aicore.eventmonitor.dto.response.monitoring.MonitoringResultResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface EventMonitoringService {

    MonitoringResultResponse monitor(@NotNull @Valid MonitoringEventRequest request);
}
