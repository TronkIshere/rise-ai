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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AttributeThresholdRule implements MonitoringRule {

    public static final String ID = "attribute-threshold";

    MonitoringProperties properties;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Flags numeric attributes that fall outside configured minimum or maximum thresholds";
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
        List<RuleFinding> findings = new ArrayList<>();
        for (MonitoringProperties.AttributeThreshold threshold : properties.getRules().getThresholds()) {
            if (!appliesTo(threshold, event)) {
                continue;
            }
            Object value = event.getAttributes().get(threshold.getAttribute());
            if (value == null) {
                continue;
            }
            BigDecimal number = toDecimal(value);
            String attribute = "attribute '" + threshold.getAttribute() + "'";
            if (number == null) {
                findings.add(RuleFinding.flag(ID, Severity.MEDIUM,
                        attribute + " is not numeric and cannot be checked against its threshold"));
                continue;
            }
            if (threshold.getMax() != null && number.compareTo(threshold.getMax()) > 0) {
                findings.add(RuleFinding.flag(ID, threshold.getSeverity(), attribute + " value " + number.toPlainString()
                        + " is above the configured maximum " + threshold.getMax().toPlainString()));
            }
            if (threshold.getMin() != null && number.compareTo(threshold.getMin()) < 0) {
                findings.add(RuleFinding.flag(ID, threshold.getSeverity(), attribute + " value " + number.toPlainString()
                        + " is below the configured minimum " + threshold.getMin().toPlainString()));
            }
        }
        return findings;
    }

    private boolean appliesTo(MonitoringProperties.AttributeThreshold threshold, MonitoringEvent event) {
        return threshold.getEventTypes().isEmpty()
                || threshold.getEventTypes().stream().anyMatch(type -> type.equalsIgnoreCase(event.getEventType()));
    }

    private BigDecimal toDecimal(Object value) {
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
