package com.aicore.eventmonitor.rule;

import com.aicore.eventmonitor.configuration.MonitoringProperties;
import com.aicore.eventmonitor.core.event.MonitoringEvent;
import com.aicore.eventmonitor.core.rule.MonitoringRule;
import com.aicore.eventmonitor.core.rule.RuleCategory;
import com.aicore.eventmonitor.core.rule.RuleEffect;
import com.aicore.eventmonitor.core.rule.RuleFinding;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RequiredFieldsRule implements MonitoringRule {

    public static final String ID = "required-fields";

    MonitoringProperties properties;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Rejects events missing eventId, eventType, source, occurredAt or a configured required attribute";
    }

    @Override
    public RuleCategory getCategory() {
        return RuleCategory.VALIDATION;
    }

    @Override
    public RuleEffect getEffect() {
        return RuleEffect.REJECT;
    }

    @Override
    public List<RuleFinding> evaluate(MonitoringEvent event) {
        List<RuleFinding> findings = new ArrayList<>();
        requireText(event.getEventId(), "eventId", findings);
        requireText(event.getEventType(), "eventType", findings);
        requireText(event.getSource(), "source", findings);
        if (event.getOccurredAt() == null) {
            findings.add(RuleFinding.reject(ID, "occurredAt is required"));
        }
        for (String attribute : properties.getRules().getRequiredAttributes()) {
            if (!event.getAttributes().containsKey(attribute)) {
                findings.add(RuleFinding.reject(ID, "required attribute '" + attribute + "' is missing"));
            }
        }
        return findings;
    }

    private void requireText(String value, String field, List<RuleFinding> findings) {
        if (!StringUtils.hasText(value)) {
            findings.add(RuleFinding.reject(ID, field + " is required"));
        }
    }
}
