package com.aicore.simulator.scenario;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Getter
@Builder(toBuilder = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExpectedOutcome {
    int httpStatus;
    Integer errorCode;
    String monitoringStatus;
    String ruleDecision;

    @Builder.Default
    Set<String> requiredRuleIds = Set.of();

    boolean noFindings;
    String riskLevel;
    Boolean duplicate;
    Boolean reviewRequired;
    String aiStatus;
    String correlationId;
    boolean sameCreatedAtAsPreviousStep;
    boolean aiResilienceInvariant;
    Boolean anomalous;
    String source;

    public String describe() {
        List<String> parts = new ArrayList<>();
        parts.add("HTTP " + httpStatus);
        if (errorCode != null) {
            parts.add("code=" + errorCode);
        }
        if (monitoringStatus != null) {
            parts.add(monitoringStatus);
        }
        if (ruleDecision != null) {
            parts.add("decision=" + ruleDecision);
        }
        if (!requiredRuleIds.isEmpty()) {
            parts.add("rules include " + new TreeSet<>(requiredRuleIds));
        }
        if (noFindings) {
            parts.add("no findings");
        }
        if (riskLevel != null) {
            parts.add("risk=" + riskLevel);
        }
        if (duplicate != null) {
            parts.add("duplicate=" + duplicate);
        }
        if (aiStatus != null) {
            parts.add("ai=" + aiStatus);
        }
        if (aiResilienceInvariant) {
            parts.add("AI failure contract holds");
        }
        return String.join(" ", parts);
    }
}
