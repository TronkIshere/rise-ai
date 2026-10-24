package com.aicore.eventmonitor.core.rule;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RuleEvaluation {
    RuleDecision decision;

    @Builder.Default
    List<RuleFinding> findings = List.of();

    @Builder.Default
    List<String> evaluatedRules = List.of();

    public boolean hasFlags() {
        return findings.stream().anyMatch(finding -> finding.getEffect() == RuleEffect.FLAG);
    }
}
