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
import com.riseai.monitoring.testsupport.MutableClock;
import com.riseai.monitoring.testsupport.TestEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineTest {

    private MonitoringProperties properties;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        properties = TestEvents.properties();
        clock = new MutableClock(TestEvents.NOW);
    }

    @Test
    void validEventIsHandledByRulesAlone() {
        RuleEvaluation evaluation = engine().evaluate(TestEvents.event().build());

        assertThat(evaluation.getDecision()).isEqualTo(RuleDecision.RULES_SUFFICIENT);
        assertThat(evaluation.getFindings()).isEmpty();
        assertThat(evaluation.getEvaluatedRules()).hasSize(6);
    }

    @Test
    void thresholdViolationFlagsTheEventForAiAnalysis() {
        RuleEvaluation evaluation = engine().evaluate(event(Map.of("latencyMs", 5000)));

        assertThat(evaluation.getDecision()).isEqualTo(RuleDecision.REQUIRES_AI_ANALYSIS);
        RuleFinding finding = evaluation.getFindings().get(0);
        assertThat(finding.getRuleId()).isEqualTo(AttributeThresholdRule.ID);
        assertThat(finding.getEffect()).isEqualTo(RuleEffect.FLAG);
        assertThat(finding.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(finding.getMessage()).isEqualTo("attribute 'latencyMs' value 5000 is above the configured maximum 1000");
    }

    @Test
    void minimumThresholdsAndNumericStringsAreSupported() {
        properties.getRules().getThresholds().get(0).setMin(new BigDecimal("10"));

        RuleEvaluation evaluation = engine().evaluate(event(Map.of("latencyMs", "2.5")));

        assertThat(evaluation.getFindings()).extracting(RuleFinding::getMessage)
                .containsExactly("attribute 'latencyMs' value 2.5 is below the configured minimum 10");
    }

    @Test
    void nonNumericThresholdAttributesAreFlagged() {
        RuleEvaluation evaluation = engine().evaluate(event(Map.of("latencyMs", "slow")));

        assertThat(evaluation.getFindings().get(0).getSeverity()).isEqualTo(Severity.MEDIUM);
        assertThat(evaluation.getDecision()).isEqualTo(RuleDecision.REQUIRES_AI_ANALYSIS);
    }

    @Test
    void thresholdsCanBeScopedToEventTypes() {
        properties.getRules().getThresholds().get(0).setEventTypes(List.of("job.finished"));

        assertThat(engine().evaluate(event(Map.of("latencyMs", 5000))).getFindings()).isEmpty();
    }

    @Test
    void highReportedSeverityIsFlaggedAndLowerSeverityIsNot() {
        assertThat(engine().evaluate(TestEvents.event().severity(Severity.CRITICAL).build()).getFindings())
                .extracting(RuleFinding::getRuleId, RuleFinding::getSeverity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ReportedSeverityRule.ID, Severity.CRITICAL));
        assertThat(engine().evaluate(TestEvents.event().severity(Severity.MEDIUM).build()).getFindings()).isEmpty();
    }

    @Test
    void futureOccurrenceIsRejected() {
        RuleEvaluation evaluation = engine().evaluate(TestEvents.event().occurredAt(TestEvents.NOW.plus(Duration.ofHours(1))).build());

        assertThat(evaluation.getDecision()).isEqualTo(RuleDecision.REJECT);
        assertThat(evaluation.getFindings().get(0).getRuleId()).isEqualTo(TimestampRule.ID);
    }

    @Test
    void futureReceiveTimeIsRejected() {
        RuleEvaluation evaluation = engine().evaluate(TestEvents.event().receivedAt(TestEvents.NOW.plus(Duration.ofHours(1))).build());

        assertThat(evaluation.getFindings()).extracting(RuleFinding::getMessage).contains("receivedAt is in the future");
    }

    @Test
    void staleEventIsRejected() {
        RuleEvaluation evaluation = engine().evaluate(TestEvents.event().occurredAt(TestEvents.NOW.minus(Duration.ofDays(8))).build());

        assertThat(evaluation.getDecision()).isEqualTo(RuleDecision.REJECT);
        assertThat(evaluation.getFindings().get(0).getMessage()).isEqualTo("occurredAt is older than the accepted event age");
    }

    @Test
    void unsupportedEventTypeIsRejected() {
        properties.getRules().setAllowedEventTypes(List.of("job.finished"));

        RuleEvaluation evaluation = engine().evaluate(TestEvents.event().build());

        assertThat(evaluation.getDecision()).isEqualTo(RuleDecision.REJECT);
        assertThat(evaluation.getFindings().get(0).getRuleId()).isEqualTo(EventTypeRule.ID);
    }

    @Test
    void oversizedPayloadAndTooManyAttributesAreRejected() {
        properties.getLimits().setMaxPayloadBytes(10);
        properties.getLimits().setMaxAttributes(1);

        RuleEvaluation evaluation = engine().evaluate(TestEvents.event().payloadBytes(11).build());

        assertThat(evaluation.getDecision()).isEqualTo(RuleDecision.REJECT);
        assertThat(evaluation.getFindings()).extracting(RuleFinding::getMessage).containsExactly(
                "payload is 11 bytes, the limit is 10 bytes",
                "event has 2 attributes, the limit is 1");
    }

    @Test
    void missingRequiredFieldsAndAttributesAreRejected() {
        properties.getRules().setRequiredAttributes(List.of("tenant"));

        RuleEvaluation evaluation = engine().evaluate(TestEvents.event().eventType(null).build());

        assertThat(evaluation.getDecision()).isEqualTo(RuleDecision.REJECT);
        assertThat(evaluation.getFindings()).extracting(RuleFinding::getMessage)
                .contains("eventType is required", "required attribute 'tenant' is missing");
    }

    @Test
    void rejectionTakesPrecedenceOverFlags() {
        RuleEvaluation evaluation = engine().evaluate(event(Map.of("latencyMs", 5000)).toBuilder()
                .occurredAt(TestEvents.NOW.plus(Duration.ofHours(1))).build());

        assertThat(evaluation.getDecision()).isEqualTo(RuleDecision.REJECT);
        assertThat(evaluation.hasFlags()).isTrue();
    }

    @Test
    void alwaysTriggerRequiresAiForEveryAcceptedEvent() {
        properties.getAi().setTrigger(MonitoringProperties.AiTrigger.ALWAYS);

        assertThat(engine().evaluate(TestEvents.event().build()).getDecision()).isEqualTo(RuleDecision.REQUIRES_AI_ANALYSIS);
    }

    @Test
    void configuredEventTypesAreAlwaysAnalyzed() {
        properties.getAi().setAlwaysAnalyzeEventTypes(List.of("REQUEST.COMPLETED"));

        assertThat(engine().evaluate(TestEvents.event().build()).getDecision()).isEqualTo(RuleDecision.REQUIRES_AI_ANALYSIS);
    }

    @Test
    void disablingTheRuleEngineKeepsValidationRulesActive() {
        properties.getRules().setEnabled(false);
        RuleEngine engine = engine();

        assertThat(engine.evaluate(event(Map.of("latencyMs", 5000))).getFindings()).isEmpty();
        assertThat(engine.evaluate(TestEvents.event().occurredAt(TestEvents.NOW.plus(Duration.ofHours(1))).build())
                .getDecision()).isEqualTo(RuleDecision.REJECT);
        assertThat(engine.evaluate(TestEvents.event().build()).getEvaluatedRules()).hasSize(4);
    }

    @Test
    void individualMonitoringRulesCanBeDisabled() {
        properties.getRules().setDisabledRules(List.of(AttributeThresholdRule.ID));

        assertThat(engine().evaluate(event(Map.of("latencyMs", 5000))).getFindings()).isEmpty();
    }

    @Test
    void failingRuleProducesAFlagInsteadOfAFailure() {
        List<MonitoringRule> rules = new ArrayList<>(rules());
        rules.add(new BrokenRule());

        RuleEvaluation evaluation = new RuleEngine(rules, properties).evaluate(TestEvents.event().build());

        assertThat(evaluation.getDecision()).isEqualTo(RuleDecision.REQUIRES_AI_ANALYSIS);
        assertThat(evaluation.getFindings()).extracting(RuleFinding::getMessage).containsExactly("rule could not be evaluated");
    }

    @Test
    void duplicateRuleIdentifiersAreRejectedAtStartup() {
        List<MonitoringRule> rules = List.of(new EventTypeRule(properties), new EventTypeRule(properties));

        assertThatThrownBy(() -> new RuleEngine(rules, properties)).isInstanceOf(IllegalStateException.class);
    }

    private MonitoringEvent event(Map<String, Object> attributes) {
        return TestEvents.event().attributes(attributes).build();
    }

    private List<MonitoringRule> rules() {
        return List.of(new RequiredFieldsRule(properties), new TimestampRule(properties, clock), new EventTypeRule(properties),
                new PayloadLimitsRule(properties), new AttributeThresholdRule(properties), new ReportedSeverityRule(properties));
    }

    private RuleEngine engine() {
        return new RuleEngine(rules(), properties);
    }

    private static final class BrokenRule implements MonitoringRule {

        @Override
        public String getId() {
            return "broken";
        }

        @Override
        public String getDescription() {
            return "Always fails";
        }

        @Override
        public RuleCategory getCategory() {
            return RuleCategory.MONITORING;
        }

        @Override
        public RuleEffect getEffect() {
            return RuleEffect.FLAG;
        }

        @Override
        public List<RuleFinding> evaluate(MonitoringEvent event) {
            throw new IllegalStateException("boom");
        }
    }
}
