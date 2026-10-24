package com.aicore.eventmonitor.core.rule;

import com.aicore.eventmonitor.core.event.Severity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RuleFinding {
    String ruleId;
    RuleEffect effect;
    Severity severity;
    String message;

    public static RuleFinding reject(String ruleId, String message) {
        return RuleFinding.builder()
                .ruleId(ruleId)
                .effect(RuleEffect.REJECT)
                .severity(Severity.HIGH)
                .message(message)
                .build();
    }

    public static RuleFinding flag(String ruleId, Severity severity, String message) {
        return RuleFinding.builder()
                .ruleId(ruleId)
                .effect(RuleEffect.FLAG)
                .severity(severity)
                .message(message)
                .build();
    }
}
