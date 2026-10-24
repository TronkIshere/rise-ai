package com.aicore.eventmonitor.controller;

import com.aicore.eventmonitor.dto.response.common.ResponseAPI;
import com.aicore.eventmonitor.dto.response.status.MonitoringStatusResponse;
import com.aicore.eventmonitor.dto.response.status.RuleResponse;
import com.aicore.eventmonitor.service.MonitoringStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
public class MonitoringStatusController {

    private final MonitoringStatusService monitoringStatusService;

    @GetMapping("/status")
    public ResponseAPI<MonitoringStatusResponse> getStatus() {
        return ResponseAPI.<MonitoringStatusResponse>builder()
                .code(200)
                .message("Monitoring status retrieved")
                .data(monitoringStatusService.getStatus())
                .build();
    }

    @GetMapping("/rules")
    public ResponseAPI<List<RuleResponse>> getRules() {
        return ResponseAPI.<List<RuleResponse>>builder()
                .code(200)
                .message("Monitoring rules retrieved")
                .data(monitoringStatusService.getRules())
                .build();
    }
}
