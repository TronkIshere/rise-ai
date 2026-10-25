package com.riseai.monitoring.service;

import com.riseai.monitoring.dto.response.status.MonitoringStatusResponse;
import com.riseai.monitoring.dto.response.status.RuleResponse;

import java.util.List;

public interface MonitoringStatusService {

    MonitoringStatusResponse getStatus();

    List<RuleResponse> getRules();
}
