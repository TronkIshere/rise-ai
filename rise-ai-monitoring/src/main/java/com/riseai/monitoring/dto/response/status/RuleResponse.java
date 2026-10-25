package com.riseai.monitoring.dto.response.status;

import com.riseai.monitoring.core.rule.RuleCategory;
import com.riseai.monitoring.core.rule.RuleEffect;
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
