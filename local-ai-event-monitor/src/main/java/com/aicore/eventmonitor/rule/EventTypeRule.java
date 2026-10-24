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

import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventTypeRule implements MonitoringRule {

    public static final String ID = "supported-event-type";

    MonitoringProperties properties;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Rejects event types outside the configured allow list; all types are accepted when the list is empty";
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
        List<String> allowedEventTypes = properties.getRules().getAllowedEventTypes();
        if (allowedEventTypes.isEmpty() || event.getEventType() == null
                || allowedEventTypes.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(event.getEventType()))) {
            return List.of();
        }
        return List.of(RuleFinding.reject(ID, "event type '" + event.getEventType() + "' is not supported"));
    }
}
