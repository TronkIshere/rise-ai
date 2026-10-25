package com.riseai.monitoring.rule;

import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.rule.MonitoringRule;
import com.riseai.monitoring.core.rule.RuleCategory;
import com.riseai.monitoring.core.rule.RuleEffect;
import com.riseai.monitoring.core.rule.RuleFinding;
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
