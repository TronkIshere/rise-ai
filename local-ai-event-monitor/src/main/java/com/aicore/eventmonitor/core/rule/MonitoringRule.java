package com.aicore.eventmonitor.core.rule;

import com.aicore.eventmonitor.core.event.MonitoringEvent;

import java.util.List;

public interface MonitoringRule {

    String getId();

    String getDescription();

    RuleCategory getCategory();

    RuleEffect getEffect();

    List<RuleFinding> evaluate(MonitoringEvent event);
}
