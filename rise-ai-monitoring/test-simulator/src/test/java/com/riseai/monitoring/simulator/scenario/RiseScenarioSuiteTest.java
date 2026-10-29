package com.riseai.monitoring.simulator.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RiseScenarioSuiteTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Scenario> scenarios = new RiseScenarioSuite(mapper, Instant.now(), new BigDecimal("50000000"),
            new BigDecimal("10000000"), 3).build();

    @Test
    void everyRiseRuleHasAScenarioThatRequiresItsFinding() {
        Set<String> required = new HashSet<>();
        scenarios.forEach(scenario -> scenario.getSteps().forEach(step -> required.addAll(step.getExpected().getRequiredFindings().keySet())));
        assertThat(required).containsExactlyInAnyOrderElementsOf(RiseScenarioSuite.ALL_RULES);
    }

    @Test
    void everyRuleExceptTheAlwaysEmittingAdjustmentRuleHasANegativeScenario() {
        Set<String> forbidden = new HashSet<>();
        scenarios.forEach(scenario -> scenario.getSteps().forEach(step -> forbidden.addAll(step.getExpected().getForbiddenFindings())));
        assertThat(forbidden).containsAll(RiseScenarioSuite.ALL_RULES.stream().filter(rule -> !rule.equals(RiseScenarioSuite.AO_01)).toList());
    }

    @Test
    void eventsAreWellFormedEnvelopesWithUniqueIdsAndNoFutureTimestamps() throws Exception {
        Set<String> ids = new HashSet<>();
        for (Scenario scenario : scenarios) {
            assertThat(scenario.getCategory()).isEqualTo(Category.RISE_RULES);
            for (ScenarioStep step : scenario.getSteps()) {
                JsonNode body = mapper.readTree(step.getBody());
                JsonNode envelope = body.path("payload");
                assertThat(ids.add(body.path("eventId").asText())).isTrue();
                assertThat(body.path("source").asText()).isEqualTo("rise-ai-backend");
                assertThat(envelope.path("eventId").asText()).isEqualTo(body.path("eventId").asText());
                assertThat(envelope.path("schemaVersion").asInt()).isEqualTo(1);
                assertThat(Instant.parse(body.path("occurredAt").asText())).isBefore(Instant.now());
                assertThat(envelope.path("occurredAt").asText()).isEqualTo(body.path("occurredAt").asText());
            }
        }
    }

    @Test
    void thresholdBoundariesFollowTheConfiguredAmounts() {
        String all = scenarios.stream().flatMap(s -> s.getSteps().stream()).map(ScenarioStep::getBody).reduce("", String::concat);
        assertThat(all).contains("\"amount\":\"50000000\"", "\"amount\":\"49999999.99\"", "\"amount\":\"10000000\"", "\"amount\":\"9999999.99\"");
    }
}
