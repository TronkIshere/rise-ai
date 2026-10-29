package com.riseai.monitoring.simulator.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.simulator.client.CoreClient;
import com.riseai.monitoring.simulator.client.CoreResponse;
import com.riseai.monitoring.simulator.configuration.SimulatorProperties;
import com.riseai.monitoring.simulator.evaluation.ScenarioEvaluator;
import com.riseai.monitoring.simulator.evaluation.ScenarioResult;
import com.riseai.monitoring.simulator.evaluation.ScenarioStatus;
import com.riseai.monitoring.simulator.scenario.RiseScenarioSuite;
import com.riseai.monitoring.simulator.scenario.Scenario;
import com.riseai.monitoring.simulator.scenario.ScenarioStep;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class RiseSuiteRun {

    private final SimulatorProperties properties;
    private final ObjectMapper objectMapper;
    private final PrintStream out;

    public RiseSuiteRun(SimulatorProperties properties, ObjectMapper objectMapper, PrintStream out) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.out = out;
    }

    public int execute() throws IOException {
        CoreClient client = new CoreClient(properties.getCoreUrl(), properties.getRequestTimeout(), objectMapper, properties.getServiceKey());
        out.println("Rise rule suite against " + properties.getCoreUrl());
        String problem = preflight(client);
        if (problem != null) {
            out.println("ERROR: " + problem);
            return SimulationRun.EXIT_MISCONFIGURED;
        }
        List<Scenario> scenarios = new RiseScenarioSuite(objectMapper, Instant.now(), properties.getRiseApprovalAmount(),
                properties.getRiseAdjustmentAmount(), properties.getRiseMaxRequestsPerMilestone()).build();
        ScenarioEvaluator evaluator = new ScenarioEvaluator("RULES_ONLY");
        List<ScenarioResult> results = new ArrayList<>();
        Set<String> exercised = new TreeSet<>();
        for (Scenario scenario : scenarios) {
            List<CoreResponse> responses = new ArrayList<>();
            for (ScenarioStep step : scenario.getSteps()) {
                CoreResponse response = client.postEvent(step.getBody(), step.getContentType());
                responses.add(response);
                if (!response.isReceived()) {
                    break;
                }
            }
            scenario.getSteps().forEach(step -> exercised.addAll(step.getExpected().getRequiredFindings().keySet()));
            ScenarioResult result = evaluator.evaluate(scenario, responses);
            results.add(result);
            out.printf("%-8s %-4s %s%s%n", scenario.getScenarioId(), result.getStatus(), scenario.getTemplate(),
                    result.getFailureReason() == null ? "" : "  <- " + result.getFailureReason());
        }
        long passed = results.stream().filter(result -> result.getStatus() == ScenarioStatus.PASS).count();
        Set<String> uncovered = new TreeSet<>(RiseScenarioSuite.ALL_RULES);
        uncovered.removeAll(exercised);
        out.printf("Rise suite: %d/%d passed; rules covered: %d/%d%s%n", passed, results.size(), exercised.size(),
                RiseScenarioSuite.ALL_RULES.size(), uncovered.isEmpty() ? "" : "; NOT covered " + uncovered);
        writeReport(results);
        return passed == results.size() && uncovered.isEmpty() ? SimulationRun.EXIT_OK : 1;
    }

    private String preflight(CoreClient client) {
        CoreResponse rules = client.get("/api/v1/monitoring/rules");
        if (!rules.isReceived()) {
            return "Rise AI Monitoring is not reachable: " + rules.getError();
        }
        if (rules.getHttpStatus() == 401) {
            return "The Core requires a service key; pass --simulator.service-key=...";
        }
        JsonNode body = rules.getBody();
        if (rules.getHttpStatus() != 200 || body == null) {
            return "Unexpected answer from /api/v1/monitoring/rules: HTTP " + rules.getHttpStatus();
        }
        int charity = 0;
        for (JsonNode rule : body.path("data")) {
            if ("CHARITY".equals(rule.path("category").asText()) && rule.path("active").asBoolean()) {
                charity++;
            }
        }
        return charity == RiseScenarioSuite.ALL_RULES.size() ? null
                : "Expected " + RiseScenarioSuite.ALL_RULES.size() + " active CHARITY rules, found " + charity + "; is monitoring.rise.enabled true?";
    }

    private void writeReport(List<ScenarioResult> results) throws IOException {
        Path directory = Path.of(properties.getReportDirectory(), "rise-" + Instant.now().getEpochSecond());
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("results.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        out.println("Report: " + directory.toAbsolutePath());
    }
}
