package com.aicore.eventmonitor.rule;

import com.aicore.eventmonitor.configuration.MonitoringProperties;
import com.aicore.eventmonitor.core.event.MonitoringEvent;
import com.aicore.eventmonitor.core.event.Severity;
import com.aicore.eventmonitor.core.rule.MonitoringRule;
import com.aicore.eventmonitor.core.rule.RuleCategory;
import com.aicore.eventmonitor.core.rule.RuleEffect;
import com.aicore.eventmonitor.core.rule.RuleFinding;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportedSeverityRule implements MonitoringRule {

    public static final String ID = "reported-severity";

    MonitoringProperties properties;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Flags events whose source-reported severity meets the configured severity level";
    }

    @Override
    public RuleCategory getCategory() {
        return RuleCategory.MONITORING;
    }

    @Override
    public RuleEffect getEffect() {
        return RuleEffect.FLAG;
    }

    @Override
    public List<RuleFinding> evaluate(MonitoringEvent event) {
        Severity severity = event.getSeverity();
        if (severity == null || severity.compareTo(properties.getRules().getFlagReportedSeverityAt()) < 0) {
            return List.of();
        }
        return List.of(RuleFinding.flag(ID, severity, "source reported severity " + severity));
    }
}
