package com.riseai.monitoring.service;

import com.riseai.monitoring.dto.request.event.MonitoringEventRequest;
import com.riseai.monitoring.dto.response.monitoring.MonitoringResultResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface EventMonitoringService {

    MonitoringResultResponse monitor(@NotNull @Valid MonitoringEventRequest request);
}
