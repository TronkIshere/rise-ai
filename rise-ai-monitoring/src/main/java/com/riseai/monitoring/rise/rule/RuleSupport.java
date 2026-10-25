package com.riseai.monitoring.rise.rule;

import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.core.finding.FindingCategory;
import com.riseai.monitoring.core.finding.FindingFact;
import com.riseai.monitoring.core.finding.RecommendedAction;
import com.riseai.monitoring.rise.RiseEnvelope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RuleSupport {
    private static final List<String> REFERENCE_NAMES = List.of("campaignId", "milestoneId", "disbursementRequestId", "donationId", "evidenceId");

    private RuleSupport() {
    }

    static Finding finding(String ruleId, String version, RiseEnvelope envelope, FindingCategory category, Severity severity,
                           String subjectType, String subjectId, String discriminator, List<FindingFact> facts, String explanation,
                           RecommendedAction action) {
        return Finding.builder()
                .findingId(Finding.idFor(ruleId, subjectType, subjectId, discriminator))
                .ruleId(ruleId)
                .ruleVersion(version)
                .category(category)
                .severity(severity)
                .subjectType(subjectType)
                .subjectId(subjectId)
                .references(references(envelope))
                .sourceEventIds(List.of(envelope.eventId()))
                .facts(List.copyOf(facts))
                .explanation(explanation)
                .recommendedAction(action)
                .observedAt(envelope.occurredAt())
                .build();
    }

    static FindingFact fact(String name, Object value, RiseEnvelope envelope) {
        return new FindingFact(name, value, envelope.eventId());
    }

    private static Map<String, String> references(RiseEnvelope envelope) {
        Map<String, String> references = new LinkedHashMap<>();
        for (String name : REFERENCE_NAMES) {
            String value = envelope.reference(name);
            if (value != null) {
                references.put(name, value);
            }
        }
        return Map.copyOf(references);
    }
}
