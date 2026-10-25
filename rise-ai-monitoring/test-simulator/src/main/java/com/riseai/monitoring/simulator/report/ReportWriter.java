package com.riseai.monitoring.simulator.report;

import com.riseai.monitoring.simulator.evaluation.ScenarioResult;
import com.riseai.monitoring.simulator.evaluation.ScenarioStatus;
import com.riseai.monitoring.simulator.evaluation.StepResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

public class ReportWriter implements Closeable {

    private static final String RESULTS_HEADER = Csv.row("scenario_id", "index", "category", "template", "description",
            "status", "http_statuses", "monitoring_status", "ai_status", "risk_level", "rule_findings", "latency_ms",
            "expected", "actual", "failure_reason");
    private static final String FAILURES_HEADER = Csv.row("scenario_id", "index", "category", "template", "description",
            "status", "expected", "actual", "failure_reason", "expected_outcome_source", "seed", "run_tag", "reproduce",
            "request_bodies");
    private static final String UNSCORED_HEADER = Csv.row("scenario_id", "index", "category", "template", "description",
            "unscored_reason", "actual");
    private static final String CATEGORY_HEADER = Csv.row("code", "category", "label", "description", "generated",
            "completed", "scored", "passed", "failed", "unscored", "errors", "accuracy", "evaluation_coverage",
            "top_failure_reasons");

    private final Path directory;
    private final ObjectMapper objectMapper;
    private final long seed;
    private final String runTag;
    private final BufferedWriter results;
    private final BufferedWriter failures;
    private final BufferedWriter unscored;

    public ReportWriter(Path directory, ObjectMapper objectMapper, long seed, String runTag) throws IOException {
        Files.createDirectories(directory);
        this.directory = directory;
        this.objectMapper = objectMapper;
        this.seed = seed;
        this.runTag = runTag;
        this.results = open("results.csv", RESULTS_HEADER);
        this.failures = open("failures.csv", FAILURES_HEADER);
        this.unscored = open("unscored.csv", UNSCORED_HEADER);
    }

    public Path getDirectory() {
        return directory;
    }

    public synchronized void write(ScenarioResult result) {
        StepResult last = result.getFinalStep();
        String category = result.getCategory().getCode() + " " + result.getCategory().name();
        try {
            results.write(Csv.row(result.getScenarioId(), result.getIndex(), category, result.getTemplate(),
                    result.getDescription(), result.getStatus(), httpStatuses(result),
                    last == null ? null : last.getMonitoringStatus(), last == null ? null : last.getAiStatus(),
                    last == null ? null : last.getRiskLevel(), last == null ? null : String.join(" ", last.getRuleIds()),
                    String.format(Locale.US, "%.2f", result.getTotalLatencyNanos() / 1e6), result.getExpectedSummary(),
                    result.getActualSummary(), result.getFailureReason()));
            if (result.getStatus() == ScenarioStatus.FAIL || result.getStatus() == ScenarioStatus.ERROR) {
                failures.write(Csv.row(result.getScenarioId(), result.getIndex(), category, result.getTemplate(),
                        result.getDescription(), result.getStatus(), result.getExpectedSummary(), result.getActualSummary(),
                        result.getFailureReason(), result.getExpectedOutcomeSource(), seed, runTag,
                        "regenerate with seed=" + seed + " and the same scenario count and distribution, index=" + result.getIndex(),
                        String.join("\n---\n", result.getRequestBodies())));
            }
            if (result.getStatus() == ScenarioStatus.UNSCORED) {
                unscored.write(Csv.row(result.getScenarioId(), result.getIndex(), category, result.getTemplate(),
                        result.getDescription(), result.getUnscoredReason(), result.getActualSummary()));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void writeCategorySummary(RunMetrics metrics) throws IOException {
        try (BufferedWriter writer = open("category-summary.csv", CATEGORY_HEADER)) {
            for (Object[] row : metrics.categoryRows()) {
                writer.write(Csv.row(row));
            }
        }
    }

    public void writeJson(String fileName, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(fileName).toFile(), value);
    }

    @Override
    public synchronized void close() throws IOException {
        results.close();
        failures.close();
        unscored.close();
    }

    private BufferedWriter open(String fileName, String header) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(directory.resolve(fileName), StandardCharsets.UTF_8);
        writer.write(header);
        return writer;
    }

    private String httpStatuses(ScenarioResult result) {
        return result.getSteps().stream()
                .map(step -> step.isResponseReceived() ? String.valueOf(step.getHttpStatus()) : String.valueOf(step.getOutcome()))
                .collect(Collectors.joining("|"));
    }
}
