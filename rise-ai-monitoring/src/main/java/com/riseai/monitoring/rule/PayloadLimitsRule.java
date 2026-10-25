package com.riseai.monitoring.rule;

import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.rule.MonitoringRule;
import com.riseai.monitoring.core.rule.RuleCategory;
import com.riseai.monitoring.core.rule.RuleEffect;
import com.riseai.monitoring.core.rule.RuleFinding;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PayloadLimitsRule implements MonitoringRule {

    public static final String ID = "payload-limits";

    MonitoringProperties properties;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Rejects events whose payload size or attribute count exceeds the configured limits";
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
        MonitoringProperties.Limits limits = properties.getLimits();
        if (event.getPayloadBytes() > limits.getMaxPayloadBytes()) {
            findings.add(RuleFinding.reject(ID, "payload is " + event.getPayloadBytes()
                    + " bytes, the limit is " + limits.getMaxPayloadBytes() + " bytes"));
        }
        if (event.getAttributes().size() > limits.getMaxAttributes()) {
            findings.add(RuleFinding.reject(ID, "event has " + event.getAttributes().size()
                    + " attributes, the limit is " + limits.getMaxAttributes()));
        }
        return findings;
    }
}
