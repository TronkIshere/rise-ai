package com.aicore.eventmonitor.dto.response.monitoring;

import com.aicore.eventmonitor.core.event.Severity;
import com.aicore.eventmonitor.core.rule.RuleEffect;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RuleFindingResponse {
    String ruleId;
    RuleEffect effect;
    Severity severity;
    String message;
}
