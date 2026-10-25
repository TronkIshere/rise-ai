package com.riseai.monitoring.core.rule;

import com.riseai.monitoring.core.event.MonitoringEvent;

import java.util.List;

public interface MonitoringRule {

    String getId();

    String getDescription();

    RuleCategory getCategory();

    RuleEffect getEffect();

    List<RuleFinding> evaluate(MonitoringEvent event);
}
