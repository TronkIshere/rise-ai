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

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TimestampRule implements MonitoringRule {

    public static final String ID = "timestamp-validity";

    MonitoringProperties properties;
    Clock clock;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Rejects events whose timestamps are in the future or older than the accepted event age";
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
        if (event.getOccurredAt() == null || event.getReceivedAt() == null) {
            return findings;
        }
        Duration clockSkew = properties.getRules().getMaxClockSkew();
        if (event.getReceivedAt().isAfter(clock.instant().plus(clockSkew))) {
            findings.add(RuleFinding.reject(ID, "receivedAt is in the future"));
        }
        if (event.getOccurredAt().isAfter(event.getReceivedAt().plus(clockSkew))) {
            findings.add(RuleFinding.reject(ID, "occurredAt is later than receivedAt beyond the allowed clock skew"));
        }
        if (event.getOccurredAt().isBefore(event.getReceivedAt().minus(properties.getRules().getMaxEventAge()))) {
            findings.add(RuleFinding.reject(ID, "occurredAt is older than the accepted event age"));
        }
        return findings;
    }
}
