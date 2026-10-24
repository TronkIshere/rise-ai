package com.aicore.simulator.report;

import com.aicore.simulator.evaluation.ScenarioResult;
import com.aicore.simulator.evaluation.StepResult;
import com.aicore.simulator.scenario.Category;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class RunMetrics {

    public static final String ANOMALY_DEFINITION = "An event counts as anomalous when the Core returns HTTP 200, the result is "
            + "neither REJECTED nor a replayed duplicate, and reviewRequired is true or overallRiskLevel is MEDIUM, HIGH or CRITICAL";
    public static final String LATENCY_DEFINITION = "Per-request latency of requests that received an HTTP response, "
            + "including expected 4xx responses; timeouts and connection failures are excluded";
    public static final String THROUGHPUT_DEFINITION = "requestsPerSecond counts every HTTP response received (any status) "
            + "divided by wall-clock run duration; timeouts and connection failures are excluded";

    private static final Set<Category> API_CATEGORIES = EnumSet.of(Category.INVALID, Category.DUPLICATE, Category.OPTIONAL_FIELDS);
    private static final Set<Category> RULE_CATEGORIES = EnumSet.of(Category.RULE_VIOLATION, Category.BOUNDARY);
    private static final Set<String> AI_NOT_ATTEMPTED = Set.of("SKIPPED", "DISABLED");

    private final Map<Category, CategoryStats> categories = new EnumMap<>(Category.class);
    private final Map<String, Long> aiStatuses = new TreeMap<>();
    private long requestsAttempted;
    private long responsesReceived;
    private long http2xx;
    private long http4xx;
    private long http5xx;
    private long httpOther;
    private long timeouts;
    private long connectionFailures;
    private long otherTransportErrors;
    private long truePositives;
    private long trueNegatives;
    private long falsePositives;
    private long falseNegatives;
    private long labeledNotClassified;
    private long aiScored;
    private long aiAcceptable;
    private long[] latencies = new long[4096];
    private int latencyCount;

    public RunMetrics(Map<Category, Integer> plannedCounts) {
        for (Category category : Category.values()) {
            categories.put(category, new CategoryStats(plannedCounts.getOrDefault(category, 0)));
        }
    }

    public synchronized void record(ScenarioResult result) {
        categories.get(result.getCategory()).record(result);
        for (StepResult step : result.getSteps()) {
            recordStep(step);
        }
        if (result.isAiScored()) {
            aiScored++;
            if (result.isAiAssessmentAcceptable()) {
                aiAcceptable++;
            }
        }
    }

    private void recordStep(StepResult step) {
        requestsAttempted++;
        switch (step.getOutcome()) {
            case RESPONSE -> recordResponse(step);
            case TIMEOUT -> timeouts++;
            case CONNECTION_FAILED -> connectionFailures++;
            case IO_ERROR -> otherTransportErrors++;
        }
    }

    private void recordResponse(StepResult step) {
        responsesReceived++;
        if (latencyCount == latencies.length) {
            latencies = Arrays.copyOf(latencies, latencies.length * 2);
        }
        latencies[latencyCount++] = step.getLatencyNanos();
        int statusClass = step.getHttpStatus() / 100;
        switch (statusClass) {
            case 2 -> http2xx++;
            case 4 -> http4xx++;
            case 5 -> http5xx++;
            default -> httpOther++;
        }
        if (step.getHttpStatus() == 200 && step.getAiStatus() != null && !Boolean.TRUE.equals(step.getDuplicate())) {
            aiStatuses.merge(step.getAiStatus(), 1L, Long::sum);
        }
        if (step.getExpectedAnomalous() != null) {
            if (step.getActualAnomalous() == null) {
                labeledNotClassified++;
            } else if (step.getExpectedAnomalous()) {
                if (step.getActualAnomalous()) {
                    truePositives++;
                } else {
                    falseNegatives++;
                }
            } else if (step.getActualAnomalous()) {
                falsePositives++;
            } else {
                trueNegatives++;
            }
        }
    }

    public synchronized long getErrorCount() {
        return categories.values().stream().mapToLong(CategoryStats::getErrors).sum();
    }

    public synchronized List<Object[]> categoryRows() {
        List<Object[]> rows = new ArrayList<>();
        for (Map.Entry<Category, CategoryStats> entry : categories.entrySet()) {
            Category category = entry.getKey();
            CategoryStats stats = entry.getValue();
            rows.add(new Object[]{category.getCode(), category.name(), category.getLabel(), category.getDescription(),
                    stats.getGenerated(), stats.getCompleted(), stats.getScored(), stats.getPassed(), stats.getFailed(),
                    stats.getUnscored(), stats.getErrors(), Ratios.percentText(Ratios.percent(stats.getPassed(), stats.getScored())),
                    Ratios.percentText(Ratios.percent(stats.getScored(), stats.getGenerated())), reasonsText(stats.topFailureReasons(3))});
        }
        return rows;
    }

    public synchronized Map<String, Object> summary(long durationNanos) {
        long passed = sum(CategoryStats::getPassed);
        long failed = sum(CategoryStats::getFailed);
        long unscored = sum(CategoryStats::getUnscored);
        long errors = sum(CategoryStats::getErrors);
        long generated = categories.values().stream().mapToLong(CategoryStats::getGenerated).sum();
        long completed = sum(CategoryStats::getCompleted);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("generated", generated);
        counts.put("completed", completed);
        counts.put("scored", passed + failed);
        counts.put("passed", passed);
        counts.put("failed", failed);
        counts.put("unscored", unscored);
        counts.put("errors", errors);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("counts", counts);
        summary.put("overallAccuracy", Ratios.ratio(passed, passed + failed));
        summary.put("categories", categorySummaries());
        summary.put("binaryClassification", binarySummary());
        summary.put("systemMetrics", systemMetrics(passed + failed, generated));
        summary.put("requests", requestSummary());
        summary.put("ai", aiSummary());
        summary.put("latencyMs", latencySummary());
        summary.put("throughput", throughputSummary(durationNanos, completed));
        summary.put("topFailureReasons", topReasons(10));
        return summary;
    }

    public synchronized void printReport(PrintStream out, long durationNanos, Path reportDirectory) {
        long passed = sum(CategoryStats::getPassed);
        long failed = sum(CategoryStats::getFailed);
        Map<String, Object> latency = latencySummary();
        out.println();
        out.println("MONITORING CORE TEST REPORT");
        out.println();
        out.printf(Locale.US, "Generated scenarios: %,d%n", categories.values().stream().mapToLong(CategoryStats::getGenerated).sum());
        out.printf(Locale.US, "Scored scenarios: %,d%n", passed + failed);
        out.printf(Locale.US, "Unscored scenarios: %,d%n", sum(CategoryStats::getUnscored));
        out.printf(Locale.US, "Scenario errors: %,d%n", sum(CategoryStats::getErrors));
        out.println();
        out.println("Overall scored accuracy: " + Ratios.display(passed, passed + failed));
        out.println();
        out.println("Category results:");
        for (Map.Entry<Category, CategoryStats> entry : categories.entrySet()) {
            CategoryStats stats = entry.getValue();
            out.printf(Locale.US, "- %s: %s (generated %,d, passed %,d, failed %,d, unscored %,d, errors %,d)%n",
                    entry.getKey().getLabel(), Ratios.display(stats.getPassed(), stats.getScored()), stats.getGenerated(),
                    stats.getPassed(), stats.getFailed(), stats.getUnscored(), stats.getErrors());
        }
        out.println();
        out.println("Binary classification (per labeled event):");
        out.printf(Locale.US, "- TP: %,d%n- TN: %,d%n- FP: %,d%n- FN: %,d%n", truePositives, trueNegatives, falsePositives, falseNegatives);
        out.println("- Precision: " + Ratios.display(truePositives, truePositives + falsePositives));
        out.println("- Recall: " + Ratios.display(truePositives, truePositives + falseNegatives));
        out.println("- F1-score: " + Ratios.percentText(f1()));
        out.printf(Locale.US, "- Labeled events without a classifiable response: %,d%n", labeledNotClassified);
        out.println();
        out.println("System:");
        out.println("- API handling pass rate: " + Ratios.display(sumOf(API_CATEGORIES, CategoryStats::getPassed), sumOf(API_CATEGORIES, CategoryStats::getScored)));
        out.println("- Deterministic rule accuracy: " + Ratios.display(sumOf(RULE_CATEGORIES, CategoryStats::getPassed), sumOf(RULE_CATEGORIES, CategoryStats::getScored)));
        out.println("- AI classification accuracy: " + Ratios.display(aiAcceptable, aiScored));
        out.println("- HTTP responses received: " + Ratios.display(responsesReceived, requestsAttempted));
        out.println("- Request failure rate: " + Ratios.display(timeouts + connectionFailures + otherTransportErrors, requestsAttempted));
        out.println();
        out.println("Performance:");
        out.println("- Average latency: " + msText(latency.get("averageMs")));
        out.println("- p50: " + msText(latency.get("p50Ms")));
        out.println("- p95: " + msText(latency.get("p95Ms")));
        out.println("- p99: " + msText(latency.get("p99Ms")));
        out.println("- Max: " + msText(latency.get("maxMs")));
        out.printf(Locale.US, "- Throughput: %.2f responses/second over %.1f s%n", perSecond(responsesReceived, durationNanos), durationNanos / 1e9);
        out.println();
        long aiAttempted = aiAttempted();
        out.println("AI (responses where the Core attempted AI analysis):");
        out.println("- Completed: " + Ratios.display(aiStatuses.getOrDefault("COMPLETED", 0L), aiAttempted));
        out.println("- Timeout: " + Ratios.display(aiStatuses.getOrDefault("TIMEOUT", 0L), aiAttempted));
        out.println("- Unavailable: " + Ratios.display(aiStatuses.getOrDefault("UNAVAILABLE", 0L), aiAttempted));
        out.println("- Invalid response: " + Ratios.display(aiStatuses.getOrDefault("INVALID_RESPONSE", 0L), aiAttempted));
        out.println("- Failed: " + Ratios.display(aiStatuses.getOrDefault("FAILED", 0L), aiAttempted));
        out.printf(Locale.US, "- Not attempted (SKIPPED/DISABLED): %,d%n", aiStatuses.getOrDefault("SKIPPED", 0L) + aiStatuses.getOrDefault("DISABLED", 0L));
        out.println();
        out.println("Top failure reasons:");
        List<Map<String, Object>> reasons = topReasons(3);
        if (reasons.isEmpty()) {
            out.println("(none)");
        }
        for (int position = 0; position < reasons.size(); position++) {
            out.printf(Locale.US, "%d. %s (%,d)%n", position + 1, reasons.get(position).get("reason"), (Long) reasons.get(position).get("count"));
        }
        out.println();
        out.println("Report directory: " + reportDirectory);
    }

    private List<Map<String, Object>> categorySummaries() {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map.Entry<Category, CategoryStats> entry : categories.entrySet()) {
            Category category = entry.getKey();
            CategoryStats stats = entry.getValue();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("code", category.getCode());
            summary.put("category", category.name());
            summary.put("label", category.getLabel());
            summary.put("description", category.getDescription());
            summary.put("generated", stats.getGenerated());
            summary.put("completed", stats.getCompleted());
            summary.put("scored", stats.getScored());
            summary.put("passed", stats.getPassed());
            summary.put("failed", stats.getFailed());
            summary.put("unscored", stats.getUnscored());
            summary.put("errors", stats.getErrors());
            summary.put("accuracy", Ratios.ratio(stats.getPassed(), stats.getScored()));
            summary.put("evaluationCoverage", Ratios.ratio(stats.getScored(), stats.getGenerated()));
            summary.put("topFailureReasons", stats.topFailureReasons(5).stream().map(this::reason).toList());
            summaries.add(summary);
        }
        return summaries;
    }

    private Map<String, Object> binarySummary() {
        Map<String, Object> binary = new LinkedHashMap<>();
        binary.put("definition", ANOMALY_DEFINITION);
        binary.put("truePositives", truePositives);
        binary.put("trueNegatives", trueNegatives);
        binary.put("falsePositives", falsePositives);
        binary.put("falseNegatives", falseNegatives);
        binary.put("labeledEventsWithoutClassifiableResponse", labeledNotClassified);
        binary.put("precision", Ratios.ratio(truePositives, truePositives + falsePositives));
        binary.put("recall", Ratios.ratio(truePositives, truePositives + falseNegatives));
        binary.put("f1Percent", f1());
        binary.put("accuracy", Ratios.ratio(truePositives + trueNegatives, truePositives + trueNegatives + falsePositives + falseNegatives));
        return binary;
    }

    private Map<String, Object> systemMetrics(long scored, long generated) {
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("apiHandlingPassRate", Ratios.ratio(sumOf(API_CATEGORIES, CategoryStats::getPassed), sumOf(API_CATEGORIES, CategoryStats::getScored)));
        system.put("deterministicRuleAccuracy", Ratios.ratio(sumOf(RULE_CATEGORIES, CategoryStats::getPassed), sumOf(RULE_CATEGORIES, CategoryStats::getScored)));
        system.put("aiClassificationAccuracy", Ratios.ratio(aiAcceptable, aiScored));
        system.put("monitoringDecisionAccuracy", Ratios.ratio(truePositives + trueNegatives, truePositives + trueNegatives + falsePositives + falseNegatives));
        system.put("httpResponseRate", Ratios.ratio(responsesReceived, requestsAttempted));
        system.put("http2xxShareOfResponses", Ratios.ratio(http2xx, responsesReceived));
        system.put("requestFailureRate", Ratios.ratio(timeouts + connectionFailures + otherTransportErrors, requestsAttempted));
        system.put("evaluationCoverage", Ratios.ratio(scored, generated));
        long aiAttempted = aiAttempted();
        system.put("aiAvailabilityRate", Ratios.ratio(aiStatuses.getOrDefault("COMPLETED", 0L), aiAttempted));
        system.put("aiTimeoutRate", Ratios.ratio(aiStatuses.getOrDefault("TIMEOUT", 0L), aiAttempted));
        system.put("aiInvalidResponseRate", Ratios.ratio(aiStatuses.getOrDefault("INVALID_RESPONSE", 0L), aiAttempted));
        system.put("aiUnavailableRate", Ratios.ratio(aiStatuses.getOrDefault("UNAVAILABLE", 0L), aiAttempted));
        system.put("aiFailedRate", Ratios.ratio(aiStatuses.getOrDefault("FAILED", 0L), aiAttempted));
        return system;
    }

    private Map<String, Object> requestSummary() {
        Map<String, Object> requests = new LinkedHashMap<>();
        requests.put("attempted", requestsAttempted);
        requests.put("responsesReceived", responsesReceived);
        requests.put("http2xx", http2xx);
        requests.put("http4xx", http4xx);
        requests.put("http5xx", http5xx);
        requests.put("httpOther", httpOther);
        requests.put("timeouts", timeouts);
        requests.put("connectionFailures", connectionFailures);
        requests.put("otherTransportErrors", otherTransportErrors);
        requests.put("retries", 0);
        return requests;
    }

    private Map<String, Object> aiSummary() {
        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("statusCounts", new LinkedHashMap<>(aiStatuses));
        ai.put("attempted", aiAttempted());
        ai.put("rubricScoredScenarios", aiScored);
        ai.put("rubricAcceptableScenarios", aiAcceptable);
        return ai;
    }

    private Map<String, Object> latencySummary() {
        long[] sorted = Arrays.copyOf(latencies, latencyCount);
        Arrays.sort(sorted);
        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("definition", LATENCY_DEFINITION);
        latency.put("count", sorted.length);
        latency.put("averageMs", sorted.length == 0 ? null : millis(Arrays.stream(sorted).average().orElse(0)));
        latency.put("p50Ms", percentile(sorted, 50));
        latency.put("p95Ms", percentile(sorted, 95));
        latency.put("p99Ms", percentile(sorted, 99));
        latency.put("maxMs", sorted.length == 0 ? null : millis(sorted[sorted.length - 1]));
        return latency;
    }

    private Map<String, Object> throughputSummary(long durationNanos, long completedScenarios) {
        Map<String, Object> throughput = new LinkedHashMap<>();
        throughput.put("definition", THROUGHPUT_DEFINITION);
        throughput.put("durationSeconds", Math.round(durationNanos / 1e7) / 100.0);
        throughput.put("requestsPerSecond", Math.round(perSecond(responsesReceived, durationNanos) * 100) / 100.0);
        throughput.put("scenariosPerSecond", Math.round(perSecond(completedScenarios, durationNanos) * 100) / 100.0);
        return throughput;
    }

    static Double percentile(long[] sorted, double percentile) {
        if (sorted.length == 0) {
            return null;
        }
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.length);
        return millis(sorted[Math.max(0, rank - 1)]);
    }

    private Double f1() {
        long precisionDenominator = truePositives + falsePositives;
        long recallDenominator = truePositives + falseNegatives;
        if (precisionDenominator == 0 || recallDenominator == 0 || truePositives == 0) {
            return precisionDenominator == 0 || recallDenominator == 0 ? null : 0.0;
        }
        double precision = (double) truePositives / precisionDenominator;
        double recall = (double) truePositives / recallDenominator;
        return Math.round(2 * precision * recall / (precision + recall) * 10000) / 100.0;
    }

    private long aiAttempted() {
        return aiStatuses.entrySet().stream()
                .filter(entry -> !AI_NOT_ATTEMPTED.contains(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private List<Map<String, Object>> topReasons(int limit) {
        Map<String, Long> merged = new HashMap<>();
        categories.values().forEach(stats -> stats.getFailureReasons().forEach((reason, count) -> merged.merge(reason, count, Long::sum)));
        return merged.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(this::reason)
                .toList();
    }

    private Map<String, Object> reason(Map.Entry<String, Long> entry) {
        Map<String, Object> reason = new LinkedHashMap<>();
        reason.put("reason", entry.getKey());
        reason.put("count", entry.getValue());
        return reason;
    }

    private String reasonsText(List<Map.Entry<String, Long>> reasons) {
        return String.join(" | ", reasons.stream().map(entry -> entry.getKey() + " (" + entry.getValue() + ")").toList());
    }

    private long sum(java.util.function.ToLongFunction<CategoryStats> value) {
        return categories.values().stream().mapToLong(value).sum();
    }

    private long sumOf(Set<Category> selected, java.util.function.ToLongFunction<CategoryStats> value) {
        return selected.stream().map(categories::get).mapToLong(value).sum();
    }

    private static double perSecond(long count, long durationNanos) {
        return durationNanos <= 0 ? 0 : count / (durationNanos / 1e9);
    }

    private static Double millis(double nanos) {
        return Math.round(nanos / 1e4) / 100.0;
    }

    private static String msText(Object value) {
        return value == null ? "N/A" : value + " ms";
    }
}
