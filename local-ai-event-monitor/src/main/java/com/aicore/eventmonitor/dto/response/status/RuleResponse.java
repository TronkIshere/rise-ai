package com.aicore.eventmonitor.dto.response.status;

import com.aicore.eventmonitor.core.rule.RuleCategory;
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
public class RuleResponse {
    String id;
    String description;
    RuleCategory category;
    RuleEffect effect;
    boolean active;
}
