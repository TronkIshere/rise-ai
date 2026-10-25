package com.riseai.monitoring.rule;

import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.rule.MonitoringRule;
import com.riseai.monitoring.core.rule.RuleCategory;
import com.riseai.monitoring.core.rule.RuleDecision;
import com.riseai.monitoring.core.rule.RuleEffect;
import com.riseai.monitoring.core.rule.RuleEvaluation;
import com.riseai.monitoring.core.rule.RuleFinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class RuleEngine {

    private final List<MonitoringRule> rules;
    private final MonitoringProperties properties;

    public RuleEngine(List<MonitoringRule> rules, MonitoringProperties properties) {
        Set<String> identifiers = new HashSet<>();
        for (MonitoringRule rule : rules) {
            if (rule.getId() == null || !identifiers.add(rule.getId())) {
                throw new IllegalStateException("Monitoring rule identifiers must be unique and non-null: " + rule.getId());
            }
        }
        this.rules = rules.stream()
                .sorted(Comparator.comparing(MonitoringRule::getCategory).thenComparing(MonitoringRule::getId))
                .toList();
        this.properties = properties;
    }

    public List<MonitoringRule> getRules() {
        return rules;
    }

    public boolean isActive(MonitoringRule rule) {
        if (rule.getCategory() == RuleCategory.VALIDATION) {
            return true;
        }
        MonitoringProperties.Rules settings = properties.getRules();
        return settings.isEnabled()
                && settings.getDisabledRules().stream().noneMatch(disabled -> disabled.equalsIgnoreCase(rule.getId()));
    }

    public RuleEvaluation evaluate(MonitoringEvent event) {
        List<RuleFinding> findings = new ArrayList<>();
        List<String> evaluatedRules = new ArrayList<>();
        for (MonitoringRule rule : rules) {
            if (!isActive(rule)) {
                continue;
            }
            evaluatedRules.add(rule.getId());
            try {
                findings.addAll(rule.evaluate(event));
            } catch (RuntimeException ex) {
                log.error("Monitoring rule {} failed for event {}", rule.getId(), event.getEventId(), ex);
                findings.add(RuleFinding.flag(rule.getId(), Severity.MEDIUM, "rule could not be evaluated"));
            }
        }
        return RuleEvaluation.builder()
                .decision(decide(event, findings))
                .findings(List.copyOf(findings))
                .evaluatedRules(List.copyOf(evaluatedRules))
                .build();
    }

    public RuleEvaluation withAdditionalFindings(MonitoringEvent event, RuleEvaluation base, List<RuleFinding> additional) {
        if (additional.isEmpty()) {
            return base;
        }
        List<RuleFinding> findings = new ArrayList<>(base.getFindings());
        findings.addAll(additional);
        return RuleEvaluation.builder()
                .decision(decide(event, findings))
                .findings(List.copyOf(findings))
                .evaluatedRules(base.getEvaluatedRules())
                .build();
    }

    private RuleDecision decide(MonitoringEvent event, List<RuleFinding> findings) {
        if (findings.stream().anyMatch(finding -> finding.getEffect() == RuleEffect.REJECT)) {
            return RuleDecision.REJECT;
        }
        MonitoringProperties.Ai ai = properties.getAi();
        boolean flagged = findings.stream().anyMatch(finding -> finding.getEffect() == RuleEffect.FLAG);
        boolean alwaysAnalyzed = ai.getTrigger() == MonitoringProperties.AiTrigger.ALWAYS
                || ai.getAlwaysAnalyzeEventTypes().stream().anyMatch(type -> type.equalsIgnoreCase(event.getEventType()));
        return flagged || alwaysAnalyzed ? RuleDecision.REQUIRES_AI_ANALYSIS : RuleDecision.RULES_SUFFICIENT;
    }
}
