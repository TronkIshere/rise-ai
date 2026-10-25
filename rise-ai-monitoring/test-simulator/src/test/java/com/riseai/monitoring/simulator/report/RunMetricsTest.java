package com.riseai.monitoring.simulator.report;

import com.riseai.monitoring.simulator.client.TransportOutcome;
import com.riseai.monitoring.simulator.evaluation.ScenarioResult;
import com.riseai.monitoring.simulator.evaluation.ScenarioStatus;
import com.riseai.monitoring.simulator.evaluation.StepResult;
import com.riseai.monitoring.simulator.scenario.Category;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunMetricsTest {

    @Test
    void ratiosShowExactNumeratorAndDenominator() {
        assertThat(Ratios.display(8450, 10000)).isEqualTo("8,450 / 10,000 = 84.50%");
        assertThat(Ratios.display(0, 0)).isEqualTo("0 / 0 = N/A");
        assertThat(Ratios.percent(1, 3)).isEqualTo(33.33);
        assertThat(Ratios.percent(5, 0)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void computesAccuracyCoverageAndConfusionMatrix() {
        RunMetrics metrics = new RunMetrics(Map.of(Category.NORMAL, 4, Category.RULE_VIOLATION, 3, Category.AI_CONTEXT, 1));
        metrics.record(result(Category.NORMAL, ScenarioStatus.PASS, step(false, false, 10)));
        metrics.record(result(Category.NORMAL, ScenarioStatus.PASS, step(false, false, 20)));
        metrics.record(result(Category.NORMAL, ScenarioStatus.FAIL, step(false, true, 30)));
        metrics.record(result(Category.NORMAL, ScenarioStatus.UNSCORED, step(null, false, 40)));
        metrics.record(result(Category.RULE_VIOLATION, ScenarioStatus.PASS, step(true, true, 50)));
        metrics.record(result(Category.RULE_VIOLATION, ScenarioStatus.FAIL, step(true, false, 60)));
        metrics.record(result(Category.RULE_VIOLATION, ScenarioStatus.ERROR, timeoutStep()));

        Map<String, Object> summary = metrics.summary(2_000_000_000L);

        Map<String, Object> overall = (Map<String, Object>) summary.get("overallAccuracy");
        assertThat(overall.get("display")).isEqualTo("3 / 5 = 60.00%");
        Map<String, Object> binary = (Map<String, Object>) summary.get("binaryClassification");
        assertThat(binary.get("truePositives")).isEqualTo(1L);
        assertThat(binary.get("trueNegatives")).isEqualTo(2L);
        assertThat(binary.get("falsePositives")).isEqualTo(1L);
        assertThat(binary.get("falseNegatives")).isEqualTo(1L);
        assertThat(((Map<String, Object>) binary.get("precision")).get("percent")).isEqualTo(50.0);
        assertThat(((Map<String, Object>) binary.get("recall")).get("percent")).isEqualTo(50.0);
        assertThat(binary.get("f1Percent")).isEqualTo(50.0);

        List<Map<String, Object>> categories = (List<Map<String, Object>>) summary.get("categories");
        Map<String, Object> normal = categories.get(0);
        assertThat(((Map<String, Object>) normal.get("accuracy")).get("display")).isEqualTo("2 / 3 = 66.67%");
        assertThat(((Map<String, Object>) normal.get("evaluationCoverage")).get("display")).isEqualTo("3 / 4 = 75.00%");
        Map<String, Object> ai = categories.get(Category.AI_CONTEXT.ordinal());
        assertThat(((Map<String, Object>) ai.get("accuracy")).get("percent")).isNull();

        Map<String, Object> requests = (Map<String, Object>) summary.get("requests");
        assertThat(requests.get("attempted")).isEqualTo(7L);
        assertThat(requests.get("responsesReceived")).isEqualTo(6L);
        assertThat(requests.get("timeouts")).isEqualTo(1L);
        Map<String, Object> system = (Map<String, Object>) summary.get("systemMetrics");
        assertThat(((Map<String, Object>) system.get("requestFailureRate")).get("display")).isEqualTo("1 / 7 = 14.29%");
        assertThat(((Map<String, Object>) system.get("aiClassificationAccuracy")).get("display")).isEqualTo("0 / 0 = N/A");
        assertThat(metrics.getErrorCount()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void latencyPercentilesUseNearestRank() {
        RunMetrics metrics = new RunMetrics(Map.of(Category.NORMAL, 100));
        for (int millis = 1; millis <= 100; millis++) {
            metrics.record(result(Category.NORMAL, ScenarioStatus.PASS, step(false, false, millis)));
        }

        Map<String, Object> latency = (Map<String, Object>) metrics.summary(1_000_000_000L).get("latencyMs");

        assertThat(latency.get("p50Ms")).isEqualTo(50.0);
        assertThat(latency.get("p95Ms")).isEqualTo(95.0);
        assertThat(latency.get("p99Ms")).isEqualTo(99.0);
        assertThat(latency.get("maxMs")).isEqualTo(100.0);
        assertThat(latency.get("averageMs")).isEqualTo(50.5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void zeroDenominatorsAreReportedAsNotAvailable() {
        RunMetrics metrics = new RunMetrics(Map.of(Category.NORMAL, 0));
        Map<String, Object> summary = metrics.summary(0);

        Map<String, Object> binary = (Map<String, Object>) summary.get("binaryClassification");
        assertThat(((Map<String, Object>) binary.get("precision")).get("percent")).isNull();
        assertThat(binary.get("f1Percent")).isNull();
        assertThat(((Map<String, Object>) summary.get("latencyMs")).get("p95Ms")).isNull();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        metrics.printReport(new PrintStream(output, true, StandardCharsets.UTF_8), 0, Path.of("reports"));
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("Overall scored accuracy: 0 / 0 = N/A").contains("- p95: N/A");
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsAiStatusesOnlyForNonDuplicateResponses() {
        RunMetrics metrics = new RunMetrics(Map.of(Category.AI_RESILIENCE, 3));
        metrics.record(result(Category.AI_RESILIENCE, ScenarioStatus.PASS, aiStep("COMPLETED", false)));
        metrics.record(result(Category.AI_RESILIENCE, ScenarioStatus.PASS, aiStep("TIMEOUT", false)));
        metrics.record(result(Category.AI_RESILIENCE, ScenarioStatus.PASS, aiStep("TIMEOUT", true)));

        Map<String, Object> system = (Map<String, Object>) metrics.summary(1).get("systemMetrics");

        assertThat(((Map<String, Object>) system.get("aiAvailabilityRate")).get("display")).isEqualTo("1 / 2 = 50.00%");
        assertThat(((Map<String, Object>) system.get("aiTimeoutRate")).get("display")).isEqualTo("1 / 2 = 50.00%");
    }

    private ScenarioResult result(Category category, ScenarioStatus status, StepResult step) {
        return ScenarioResult.builder()
                .scenarioId("S")
                .category(category)
                .status(status)
                .failureReason(status == ScenarioStatus.FAIL ? "overallRiskLevel expected LOW, actual HIGH" : status == ScenarioStatus.ERROR ? "TIMEOUT" : null)
                .steps(List.of(step))
                .build();
    }

    private StepResult step(Boolean expectedAnomalous, Boolean actualAnomalous, int latencyMillis) {
        return StepResult.builder()
                .outcome(TransportOutcome.RESPONSE)
                .httpStatus(200)
                .bodyReadable(true)
                .aiStatus("SKIPPED")
                .duplicate(false)
                .latencyNanos(latencyMillis * 1_000_000L)
                .expectedAnomalous(expectedAnomalous)
                .actualAnomalous(actualAnomalous)
                .build();
    }

    private StepResult aiStep(String aiStatus, boolean duplicate) {
        return StepResult.builder().outcome(TransportOutcome.RESPONSE).httpStatus(200).bodyReadable(true)
                .aiStatus(aiStatus).duplicate(duplicate).latencyNanos(1_000_000L).build();
    }

    private StepResult timeoutStep() {
        return StepResult.builder().outcome(TransportOutcome.TIMEOUT).build();
    }
}
