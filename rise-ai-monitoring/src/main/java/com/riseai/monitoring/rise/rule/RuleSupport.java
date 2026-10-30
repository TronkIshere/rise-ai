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
import java.util.Set;
import java.util.regex.Pattern;

final class RuleSupport {
    private static final List<String> REFERENCE_NAMES = List.of("campaignId", "milestoneId", "disbursementRequestId", "donationId", "evidenceId");

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,80}$");
    private static final Set<String> DIRECTIONS = Set.of("CREDIT", "DEBIT", "NEUTRAL");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern ENUM_TEXT = Pattern.compile("^[A-Z][A-Z0-9_]{0,39}$");

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
                .facts(facts.stream().filter(fact -> fact.value() != null).toList())
                .explanation(explanation)
                .recommendedAction(action)
                .observedAt(envelope.occurredAt())
                .build();
    }

    static boolean validId(String value) {
        return value != null && SAFE_ID.matcher(value).matches();
    }

    static String enumText(String value) {
        return value != null && ENUM_TEXT.matcher(value).matches() ? value : null;
    }

    static String direction(String value) {
        return value != null && DIRECTIONS.contains(value) ? value : null;
    }

    static String currency(String value) {
        return value != null && CURRENCY.matcher(value).matches() ? value : null;
    }

    static String code(String value) {
        return validId(value) ? value : null;
    }

    static String orUnspecified(String value) {
        return value == null ? "unspecified" : value;
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
