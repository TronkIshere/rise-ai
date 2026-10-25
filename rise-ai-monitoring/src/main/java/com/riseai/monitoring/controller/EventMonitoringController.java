package com.riseai.monitoring.controller;

import com.riseai.monitoring.dto.request.event.MonitoringEventRequest;
import com.riseai.monitoring.dto.response.common.ResponseAPI;
import com.riseai.monitoring.dto.response.monitoring.MonitoringResultResponse;
import com.riseai.monitoring.service.EventMonitoringService;
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
