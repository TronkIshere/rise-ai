package com.riseai.monitoring.simulator.report;

import com.riseai.monitoring.simulator.client.TransportOutcome;
import com.riseai.monitoring.simulator.evaluation.ScenarioResult;
import com.riseai.monitoring.simulator.evaluation.ScenarioStatus;
import com.riseai.monitoring.simulator.evaluation.StepResult;
import com.riseai.monitoring.simulator.scenario.Category;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void escapesCsvValues() {
        assertThat(Csv.escape("plain")).isEqualTo("plain");
        assertThat(Csv.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(Csv.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(Csv.escape("line\nbreak")).isEqualTo("\"line\nbreak\"");
        assertThat(Csv.row("a", null, 3)).isEqualTo("a,,3\n");
    }

    @Test
    void writesPerScenarioFilesIncrementally() throws Exception {
        Path directory = tempDir.resolve("run-1");
        try (ReportWriter writer = new ReportWriter(directory, objectMapper, 42, "tag")) {
            writer.write(result("S000001", ScenarioStatus.PASS, "ordinary", null, null));
            writer.write(result("S000002", ScenarioStatus.FAIL, "has \"quotes\", commas", "risk expected LOW, actual HIGH", null));
            writer.write(result("S000003", ScenarioStatus.UNSCORED, "exploratory", null, "no rubric"));
            writer.write(result("S000004", ScenarioStatus.ERROR, "timed out", "TIMEOUT", null));
        }

        List<String> results = Files.readAllLines(directory.resolve("results.csv"), StandardCharsets.UTF_8);
        assertThat(results).hasSize(5);
        assertThat(results.get(0)).startsWith("scenario_id,index,category");
        String failures = Files.readString(directory.resolve("failures.csv"), StandardCharsets.UTF_8);
        assertThat(failures).contains("S000002").contains("S000004").doesNotContain("S000001")
                .contains("\"has \"\"quotes\"\", commas\"").contains("seed=42");
        List<String> unscored = Files.readAllLines(directory.resolve("unscored.csv"), StandardCharsets.UTF_8);
        assertThat(unscored).hasSize(2);
        assertThat(unscored.get(1)).contains("S000003").contains("no rubric");
    }

    @Test
    void writesCategorySummaryAndJson() throws Exception {
        Path directory = tempDir.resolve("run-2");
        RunMetrics metrics = new RunMetrics(Map.of(Category.NORMAL, 1));
        metrics.record(result("S000001", ScenarioStatus.PASS, "ordinary", null, null));
        try (ReportWriter writer = new ReportWriter(directory, objectMapper, 42, "tag")) {
            writer.writeCategorySummary(metrics);
            writer.writeJson("summary.json", metrics.summary(1_000_000_000L));
        }

        List<String> categories = Files.readAllLines(directory.resolve("category-summary.csv"), StandardCharsets.UTF_8);
        assertThat(categories).hasSize(Category.values().length + 1);
        assertThat(categories.get(1)).startsWith("A,NORMAL,Normal events").contains("100.00%");
        JsonNode summary = objectMapper.readTree(directory.resolve("summary.json").toFile());
        assertThat(summary.at("/overallAccuracy/display").asText()).isEqualTo("1 / 1 = 100.00%");
    }

    private ScenarioResult result(String id, ScenarioStatus status, String description, String failure, String unscoredReason) {
        StepResult step = StepResult.builder().outcome(TransportOutcome.RESPONSE).httpStatus(200).bodyReadable(true)
                .monitoringStatus("COMPLETED").riskLevel("LOW").aiStatus("SKIPPED").latencyNanos(1_500_000).build();
        return ScenarioResult.builder()
                .scenarioId(id)
                .index(1)
                .category(Category.NORMAL)
                .template("normal")
                .description(description)
                .status(status)
                .failureReason(failure)
                .unscoredReason(unscoredReason)
                .expectedSummary("HTTP 200 COMPLETED")
                .actualSummary("HTTP 200 COMPLETED")
                .expectedOutcomeSource("test")
                .steps(List.of(step))
                .requestBodies(failure == null ? List.of() : List.of("{\"eventId\":\"x\"}"))
                .build();
    }
}
