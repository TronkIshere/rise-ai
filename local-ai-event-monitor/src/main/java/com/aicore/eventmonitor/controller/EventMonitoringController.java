package com.aicore.eventmonitor.controller;

import com.aicore.eventmonitor.dto.request.event.MonitoringEventRequest;
import com.aicore.eventmonitor.dto.response.common.ResponseAPI;
import com.aicore.eventmonitor.dto.response.monitoring.MonitoringResultResponse;
import com.aicore.eventmonitor.service.EventMonitoringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventMonitoringController {

    private final EventMonitoringService eventMonitoringService;

    @PostMapping("/monitor")
    public ResponseAPI<MonitoringResultResponse> monitor(@Valid @RequestBody MonitoringEventRequest request) {
        MonitoringResultResponse result = eventMonitoringService.monitor(request);
        return ResponseAPI.<MonitoringResultResponse>builder()
                .code(200)
                .message(result.isDuplicate() ? "Duplicate event, returning the original result" : "Event processed")
                .data(result)
                .build();
    }
}
