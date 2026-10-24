package com.aicore.simulator.runner;

import com.aicore.simulator.client.CoreClient;
import com.aicore.simulator.configuration.SimulatorProperties;
import com.aicore.simulator.evaluation.ScenarioEvaluator;
import com.aicore.simulator.report.ReportWriter;
import com.aicore.simulator.report.RunMetrics;
import com.aicore.simulator.scenario.Category;
import com.aicore.simulator.scenario.CoreContract;
import com.aicore.simulator.scenario.EventFactory;
import com.aicore.simulator.scenario.ScenarioGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class SimulationRun {

    public static final String SIMULATOR_VERSION = "0.1.0-SNAPSHOT";
    public static final int EXIT_OK = 0;
    public static final int EXIT_UNREACHABLE = 2;
    public static final int EXIT_MISCONFIGURED = 3;
    public static final String CORE_START_COMMAND = "./mvnw spring-boot:run -Dspring-boot.run.arguments="
            + "--spring.config.additional-location=file:./test-simulator/core-config/core-simulator.yml";

    private static final int PROGRESS_INTERVAL = 1000;
    private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final SimulatorProperties properties;
    private final ObjectMapper objectMapper;
    private final PrintStream out;

    public SimulationRun(SimulatorProperties properties, ObjectMapper objectMapper, PrintStream out) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.out = out;
    }

    public int execute() throws IOException, InterruptedException {
        Instant startedAt = Instant.now();
        String runId = RUN_ID_FORMAT.format(startedAt) + "-seed" + properties.getSeed();
        String runTag = Long.toString(startedAt.getEpochSecond(), 36);
        Instant referenceTime = startedAt.truncatedTo(ChronoUnit.SECONDS).minusSeconds(120);
        EventFactory events = new EventFactory(objectMapper, referenceTime);
        CoreClient client = new CoreClient(properties.getCoreUrl(), properties.getRequestTimeout(), objectMapper);

        out.println("Monitoring Core test simulator " + SIMULATOR_VERSION);
        out.println("Core URL: " + properties.getCoreUrl());
        out.printf(Locale.US, "Scenarios: %,d%n", properties.getScenarioCount());
        out.println("Seed: " + properties.getSeed());
        out.println("Concurrency: " + properties.getConcurrency() + ", request timeout: " + properties.getRequestTimeout());
        out.println("AI scenarios: " + (properties.isIncludeAiScenarios() ? "included" : "excluded"));
        out.println("Checking the Core before submitting the workload...");

        CoreFacts facts = new CorePreflight(client, events, runTag).check();
        if (!facts.isReachable()) {
            out.println("ERROR: the Monitoring Core is not reachable at " + properties.getCoreUrl() + ": " + facts.getUnreachableReason());
            out.println("Start the Core from the repository root with the simulator profile:");
            out.println("  " + CORE_START_COMMAND);
            out.println("or point the simulator at the right address with --simulator.core-url=http://host:port");
            return EXIT_UNREACHABLE;
        }
        out.println("AI in Core: enabled=" + facts.getAiAnalysisEnabled() + ", runtime=" + facts.getAiRuntimeState()
                + ", model=" + facts.getModelName() + ", failure mode=" + facts.getAiFailureMode());
        facts.getCalibration().forEach(line -> out.println("Calibration " + line));
        if (!facts.getProblems().isEmpty()) {
            out.println("The Core does not match the configuration the expected outcomes assume:");
            facts.getProblems().forEach(problem -> out.println("  - " + problem));
            out.println("Start the Core with: " + CORE_START_COMMAND);
            if (properties.isFailFast()) {
                out.println("Aborting (simulator.fail-fast=true).");
                return EXIT_MISCONFIGURED;
            }
            out.println("WARNING: continuing because simulator.fail-fast=false; affected scenarios will be reported as FAIL.");
        }

        ScenarioGenerator generator = new ScenarioGenerator(properties.getSeed(), properties.getScenarioCount(),
                properties.getDistribution(), properties.isIncludeAiScenarios(), runTag, events);
        out.println("Planned distribution:");
        generator.getPlannedCounts().forEach((category, count) -> out.printf(Locale.US, "  %s %-34s %,9d%s%n",
                category.getCode(), category.getLabel(), count,
                count == 0 && category.isAiDependent() && !properties.isIncludeAiScenarios() ? "  (skipped: AI scenarios excluded)" : ""));

        Path directory = Path.of(properties.getReportDirectory(), runId);
        RunMetrics metrics = new RunMetrics(generator.getPlannedCounts());
        ScenarioEvaluator evaluator = new ScenarioEvaluator(facts.getAiFailureMode());
        int total = generator.getScenarioCount();
        long runStartNanos = System.nanoTime();
        try (ReportWriter writer = new ReportWriter(directory, objectMapper, properties.getSeed(), runTag)) {
            new ScenarioExecutor(client, evaluator, properties.getConcurrency()).run(generator,
                    result -> {
                        writer.write(result);
                        metrics.record(result);
                    },
                    completed -> {
                        if (completed % PROGRESS_INTERVAL == 0 || completed == total) {
                            out.printf(Locale.US, "Progress: %,d / %,d scenarios (%.1f%%), elapsed %.0f s, scenario errors %,d%n",
                                    completed, total, completed * 100.0 / total, (System.nanoTime() - runStartNanos) / 1e9,
                                    metrics.getErrorCount());
                        }
                    });
            long durationNanos = System.nanoTime() - runStartNanos;

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("runId", runId);
            summary.put("startedAt", startedAt.toString());
            summary.put("finishedAt", Instant.now().toString());
            summary.put("coreUrl", properties.getCoreUrl().toString());
            summary.put("seed", properties.getSeed());
            summary.put("scenarioCount", total);
            summary.put("concurrency", properties.getConcurrency());
            summary.put("requestTimeout", properties.getRequestTimeout().toString());
            summary.put("includeAiScenarios", properties.isIncludeAiScenarios());
            summary.put("distribution", plannedDistribution(generator));
            summary.putAll(metrics.summary(durationNanos));
            writer.writeJson("summary.json", summary);
            writer.writeCategorySummary(metrics);
            writer.writeJson("run-config.json", runConfig(runId, runTag, referenceTime, facts));
            metrics.printReport(out, durationNanos, directory.toAbsolutePath());
        }
        return EXIT_OK;
    }

    private Map<String, Object> plannedDistribution(ScenarioGenerator generator) {
        Map<String, Object> distribution = new LinkedHashMap<>();
        generator.getPlannedCounts().forEach((category, count) -> distribution.put(category.name(), count));
        return distribution;
    }

    private Map<String, Object> runConfig(String runId, String runTag, Instant referenceTime, CoreFacts facts) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("coreUrl", properties.getCoreUrl().toString());
        configuration.put("scenarioCount", properties.getScenarioCount());
        configuration.put("seed", properties.getSeed());
        configuration.put("concurrency", properties.getConcurrency());
        configuration.put("requestTimeout", properties.getRequestTimeout().toString());
        configuration.put("reportDirectory", properties.getReportDirectory());
        configuration.put("includeAiScenarios", properties.isIncludeAiScenarios());
        configuration.put("failFast", properties.isFailFast());
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            weights.put(category.name(), properties.getDistribution().getOrDefault(category, 0));
        }
        configuration.put("distributionWeights", weights);
        configuration.put("retries", "not supported; every request is sent exactly once");

        Map<String, Object> core = new LinkedHashMap<>();
        core.put("version", "not exposed by the Core API");
        core.put("aiAnalysisEnabled", facts.getAiAnalysisEnabled());
        core.put("aiTrigger", facts.getAiTrigger());
        core.put("aiFailureMode", facts.getAiFailureMode());
        core.put("ruleEngineEnabled", facts.getRuleEngineEnabled());
        core.put("deduplicationEnabled", facts.getDeduplicationEnabled());
        core.put("aiRuntimeState", facts.getAiRuntimeState());
        core.put("modelName", facts.getModelName());
        core.put("calibration", facts.getCalibration());
        core.put("problems", facts.getProblems());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("file", "test-simulator/core-config/core-simulator.yml");
        profile.put("allowedEventTypes", CoreContract.ALLOWED_EVENT_TYPES);
        profile.put("latencyMsMax", CoreContract.LATENCY_MAX);
        profile.put("freeDiskPercentMin", CoreContract.FREE_DISK_MIN);
        profile.put("maxClockSkew", CoreContract.MAX_CLOCK_SKEW.toString());
        profile.put("maxEventAge", CoreContract.MAX_EVENT_AGE.toString());
        profile.put("maxPayloadBytes", CoreContract.MAX_PAYLOAD_BYTES);
        profile.put("maxAttributes", CoreContract.MAX_ATTRIBUTES);
        profile.put("maxAttributeValueLength", CoreContract.MAX_ATTRIBUTE_VALUE_LENGTH);
        profile.put("maxPayloadDepth", CoreContract.MAX_PAYLOAD_DEPTH);

        Map<String, Object> runConfig = new LinkedHashMap<>();
        runConfig.put("runId", runId);
        runConfig.put("runTag", runTag);
        runConfig.put("referenceTime", referenceTime.toString());
        runConfig.put("simulatorVersion", SIMULATOR_VERSION);
        runConfig.put("configuration", configuration);
        runConfig.put("core", core);
        runConfig.put("assumedCoreProfile", profile);
        runConfig.put("reproduction", "Scenario categories, templates and values are determined by seed, scenario count and "
                + "distribution. Event IDs include runTag and timestamps are relative to referenceTime.");
        return runConfig;
    }
}
