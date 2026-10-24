package com.aicore.eventmonitor.core.analysis;

import com.aicore.eventmonitor.core.event.MonitoringEvent;
import com.aicore.eventmonitor.core.rule.RuleFinding;

import java.util.List;

public interface AiEventAnalyzer {

    String getRuntimeName();

    AiAnalysis analyze(MonitoringEvent event, List<RuleFinding> ruleFindings);

    AiRuntimeStatus checkRuntimeStatus();
}
