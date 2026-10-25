package com.riseai.monitoring.simulator.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioGeneratorTest {

    private static final Instant REFERENCE = Instant.parse("2026-10-24T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventFactory events = new EventFactory(objectMapper, REFERENCE);

    @Test
    void plannedCountsAlwaysSumToTheConfiguredScenarioCount() {
        for (int total : new int[]{1, 7, 101, 99999, 100000}) {
            Map<Category, Integer> counts = ScenarioGenerator.plannedCounts(total, Category.defaultDistribution(), true);
            assertThat(counts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(total);
        }
    }

    @Test
    void defaultDistributionForOneHundredThousandScenarios() {
        Map<Category, Integer> counts = ScenarioGenerator.plannedCounts(100000, Category.defaultDistribution(), true);

        assertThat(counts.get(Category.NORMAL)).isEqualTo(25000);
        assertThat(counts.get(Category.RULE_VIOLATION)).isEqualTo(15000);
        assertThat(counts.get(Category.BOUNDARY)).isEqualTo(15000);
        assertThat(counts.get(Category.AI_CONTEXT)).isEqualTo(5000);
        assertThat(counts.get(Category.INVALID)).isEqualTo(10000);
        assertThat(counts.get(Category.DUPLICATE)).isEqualTo(8000);
        assertThat(counts.get(Category.OPTIONAL_FIELDS)).isEqualTo(7000);
        assertThat(counts.get(Category.EVENT_SEQUENCE)).isEqualTo(5000);
        assertThat(counts.get(Category.LARGE_PAYLOAD)).isEqualTo(5000);
        assertThat(counts.get(Category.AI_RESILIENCE)).isEqualTo(5000);
    }

    @Test
    void excludingAiScenariosRedistributesWithoutChangingTheTotal() {
        Map<Category, Integer> counts = ScenarioGenerator.plannedCounts(1000, Category.defaultDistribution(), false);

        assertThat(counts.get(Category.AI_CONTEXT)).isZero();
        assertThat(counts.get(Category.AI_RESILIENCE)).isZero();
        assertThat(counts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(1000);
    }

    @Test
    void rejectsADistributionWithoutPositiveWeights() {
        Map<Category, Integer> weights = new LinkedHashMap<>();
        weights.put(Category.NORMAL, 0);

        assertThatThrownBy(() -> ScenarioGenerator.plannedCounts(10, weights, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatedCategoriesMatchThePlan() {
        ScenarioGenerator generator = generator(42, 10007);
        Map<Category, Integer> actual = new EnumMap<>(Category.class);
        for (int index = 0; index < generator.getScenarioCount(); index++) {
            actual.merge(generator.generate(index).getCategory(), 1, Integer::sum);
        }
        for (Category category : Category.values()) {
            assertThat(actual.getOrDefault(category, 0)).isEqualTo(generator.getPlannedCounts().get(category));
        }
    }

    @Test
    void sameSeedProducesIdenticalScenarios() {
        ScenarioGenerator first = generator(42, 3000);
        ScenarioGenerator second = generator(42, 3000);
        for (int index = 0; index < 3000; index++) {
            Scenario a = first.generate(index);
            Scenario b = second.generate(index);
            assertThat(b.getCategory()).isEqualTo(a.getCategory());
            assertThat(b.getTemplate()).isEqualTo(a.getTemplate());
            assertThat(b.describeExpected()).isEqualTo(a.describeExpected());
            for (int step = 0; step < a.getSteps().size(); step++) {
                assertThat(b.getSteps().get(step).getBody()).isEqualTo(a.getSteps().get(step).getBody());
            }
        }
    }

    @Test
    void differentSeedsProduceDifferentPlans() {
        ScenarioGenerator first = generator(42, 2000);
        ScenarioGenerator second = generator(43, 2000);
        int differences = 0;
        for (int index = 0; index < 2000; index++) {
            if (first.categoryOf(index) != second.categoryOf(index)) {
                differences++;
            }
        }
        assertThat(differences).isGreaterThan(100);
    }

    @Test
    void everyScenarioHasAnIndependentlyDefinedExpectation() {
        ScenarioGenerator generator = generator(7, 20000);
        Set<String> eventIdsSeen = new HashSet<>();
        for (int index = 0; index < generator.getScenarioCount(); index++) {
            Scenario scenario = generator.generate(index);
            assertThat(scenario.getScenarioId()).isEqualTo(String.format("S%06d", index + 1));
            assertThat(scenario.getSteps()).isNotEmpty();
            assertThat(scenario.getExpectedOutcomeSource()).isNotBlank();
            if (!scenario.isScorable()) {
                assertThat(scenario.getUnscoredReason()).isNotBlank();
            }
            Set<String> scenarioIds = new HashSet<>();
            for (ScenarioStep step : scenario.getSteps()) {
                assertThat(step.getExpected()).isNotNull();
                assertThat(step.getBody().getBytes(StandardCharsets.UTF_8).length).isLessThan(262144);
                if (step.getEventId() != null && !step.getEventId().isBlank()) {
                    scenarioIds.add(step.getEventId());
                }
            }
            for (String eventId : scenarioIds) {
                assertThat(eventIdsSeen.add(eventId)).as("event id reused across scenarios: " + eventId).isTrue();
            }
        }
    }

    @Test
    void boundaryExpectationsFollowTheDocumentedOperators() {
        ScenarioGenerator generator = generator(42, 30000);
        assertThat(find(generator, "latency-1000").getSteps().get(0).getExpected().isNoFindings()).isTrue();
        assertThat(find(generator, "latency-1001").getSteps().get(0).getExpected().getRequiredRuleIds()).containsExactly("attribute-threshold");
        assertThat(find(generator, "free-disk-10").getSteps().get(0).getExpected().isNoFindings()).isTrue();
        assertThat(find(generator, "severity-HIGH").getSteps().get(0).getExpected().getRequiredRuleIds()).containsExactly("reported-severity");
        assertThat(find(generator, "payload-65537-bytes").getSteps().get(0).getExpected().getMonitoringStatus()).isEqualTo("REJECTED");
        assertThat(find(generator, "attribute-value-1001").getSteps().get(0).getExpected().getErrorCode()).isEqualTo(2000);
    }

    @Test
    void duplicateScenariosReuseTheEventIdWithinTheScenario() {
        Scenario scenario = find(generator(42, 30000), "exact-resend");

        assertThat(scenario.getSteps()).hasSize(2);
        assertThat(scenario.getSteps().get(1).getEventId()).isEqualTo(scenario.getSteps().get(0).getEventId());
        assertThat(scenario.getSteps().get(1).getBody()).isEqualTo(scenario.getSteps().get(0).getBody());
        assertThat(scenario.getSteps().get(1).getExpected().getDuplicate()).isTrue();
    }

    @Test
    void payloadHelpersProduceExactSizesAndDepths() throws Exception {
        assertThat(events.payloadBytes(events.payloadOfBytes(65536))).isEqualTo(65536);
        assertThat(events.payloadBytes(events.payloadOfBytes(65537))).isEqualTo(65537);
        JsonNode node = objectMapper.readTree(events.toJson(events.nestedPayload(20)));
        int depth = 1;
        while (node.has("n")) {
            node = node.get("n");
            depth++;
        }
        assertThat(depth).isEqualTo(20);
        assertThat(events.payloadBytes(events.itemsPayload(58000))).isBetween(57000, 58100);
    }

    private Scenario find(ScenarioGenerator generator, String template) {
        for (int index = 0; index < generator.getScenarioCount(); index++) {
            Scenario scenario = generator.generate(index);
            if (scenario.getTemplate().equals(template)) {
                return scenario;
            }
        }
        throw new AssertionError("template not generated: " + template);
    }

    private ScenarioGenerator generator(long seed, int count) {
        return new ScenarioGenerator(seed, count, Category.defaultDistribution(), true, "test", events);
    }
}
