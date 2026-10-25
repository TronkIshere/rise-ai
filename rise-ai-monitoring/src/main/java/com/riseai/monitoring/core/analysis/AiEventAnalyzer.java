package com.riseai.monitoring.core.analysis;

import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.rule.RuleFinding;

import java.util.List;

public interface AiEventAnalyzer {

    String getRuntimeName();

    AiAnalysis analyze(MonitoringEvent event, List<RuleFinding> ruleFindings);

    AiRuntimeStatus checkRuntimeStatus();
}
