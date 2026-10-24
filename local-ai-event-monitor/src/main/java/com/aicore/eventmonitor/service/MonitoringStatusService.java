package com.aicore.eventmonitor.service;

import com.aicore.eventmonitor.dto.response.status.MonitoringStatusResponse;
import com.aicore.eventmonitor.dto.response.status.RuleResponse;

import java.util.List;

public interface MonitoringStatusService {

    MonitoringStatusResponse getStatus();

    List<RuleResponse> getRules();
}
