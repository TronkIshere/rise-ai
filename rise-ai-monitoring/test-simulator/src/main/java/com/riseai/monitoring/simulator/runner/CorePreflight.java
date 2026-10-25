package com.riseai.monitoring.simulator.runner;

import com.riseai.monitoring.simulator.client.CoreClient;
import com.riseai.monitoring.simulator.client.CoreResponse;
import com.riseai.monitoring.simulator.evaluation.StepResult;
import com.riseai.monitoring.simulator.scenario.CoreContract;
import com.riseai.monitoring.simulator.scenario.EventFactory;
import com.riseai.monitoring.simulator.scenario.ScenarioStep;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

public class CorePreflight {

    private final CoreClient client;
    private final EventFactory events;
    private final String runTag;

    public CorePreflight(CoreClient client, EventFactory events, String runTag) {
        this.client = client;
        this.events = events;
        this.runTag = runTag;
    }

    public CoreFacts check() {
        CoreResponse health = client.get(CoreContract.HEALTH_PATH);
        if (!health.isReceived()) {
            return CoreFacts.builder()
                    .reachable(false)
                    .unreachableReason(health.getOutcome() + (health.getError() == null ? "" : " (" + health.getError() + ")"))
                    .build();
        }

        List<String> problems = new ArrayList<>();
        if (health.getHttpStatus() != 200 || !"UP".equals(text(health.getBody(), "/status"))) {
            problems.add("Actuator health is not UP (HTTP " + health.getHttpStatus() + ")");
        }

        CoreFacts.CoreFactsBuilder facts = CoreFacts.builder().reachable(true);
        CoreResponse status = client.get(CoreContract.STATUS_PATH);
        JsonNode body = status.isReceived() && status.getHttpStatus() == 200 ? status.getBody() : null;
        if (body == null || !body.path("data").isObject()) {
            problems.add("GET " + CoreContract.STATUS_PATH + " did not return monitoring status");
        } else {
            JsonNode data = body.path("data");
            facts.aiAnalysisEnabled(bool(data, "aiAnalysisEnabled"))
                    .aiTrigger(text(data, "/aiTrigger"))
                    .aiFailureMode(text(data, "/aiFailureMode"))
                    .ruleEngineEnabled(bool(data, "ruleEngineEnabled"))
                    .deduplicationEnabled(bool(data, "deduplicationEnabled"))
                    .aiRuntimeState(text(data, "/aiRuntime/state"))
                    .modelName(text(data, "/aiRuntime/modelName"));
            if (!Boolean.TRUE.equals(bool(data, "ruleEngineEnabled"))) {
                problems.add("monitoring.rules.enabled must be true");
            }
            if (!"ON_RULE_FLAG".equals(text(data, "/aiTrigger"))) {
                problems.add("monitoring.ai.trigger must be ON_RULE_FLAG, actual " + text(data, "/aiTrigger"));
            }
            if (!Boolean.TRUE.equals(bool(data, "deduplicationEnabled"))) {
                problems.add("monitoring.deduplication.enabled must be true");
            }
        }

        CoreResponse rules = client.get(CoreContract.RULES_PATH);
        Set<String> activeRules = new HashSet<>();
        if (rules.isReceived() && rules.getBody() != null) {
            for (JsonNode rule : rules.getBody().path("data")) {
                if (rule.path("active").asBoolean()) {
                    activeRules.add(rule.path("id").asText());
                }
            }
        }
        for (String ruleId : CoreContract.RULE_IDS) {
            if (!activeRules.contains(ruleId)) {
                problems.add("Rule " + ruleId + " is not listed as active by GET " + CoreContract.RULES_PATH);
            }
        }

        List<String> calibration = new ArrayList<>();
        probe(1, "latencyMs=1001 is flagged by attribute-threshold", "latencyMs", 1001,
                step -> step.getRuleIds().contains(CoreContract.RULE_THRESHOLD), calibration, problems);
        probe(2, "latencyMs=1000 produces no finding", "latencyMs", 1000,
                step -> step.getRuleIds().isEmpty(), calibration, problems);
        probe(3, "freeDiskPercent=9 is flagged by attribute-threshold", "freeDiskPercent", 9,
                step -> step.getRuleIds().contains(CoreContract.RULE_THRESHOLD), calibration, problems);
        probe(4, "eventType " + CoreContract.UNLISTED_EVENT_TYPE + " is rejected by supported-event-type", "eventType", null,
                step -> "REJECTED".equals(step.getMonitoringStatus()) && step.getRuleIds().contains(CoreContract.RULE_EVENT_TYPE),
                calibration, problems);

        return facts.problems(List.copyOf(problems)).calibration(List.copyOf(calibration)).build();
    }

    private void probe(int number, String description, String field, Object value, Predicate<StepResult> check,
                       List<String> calibration, List<String> problems) {
        String eventId = "sim-" + runTag + "-probe-" + number;
        Map<String, Object> event = events.event(eventId, "request.completed", new Random(number));
        if (field.equals("eventType")) {
            event.put("eventType", CoreContract.UNLISTED_EVENT_TYPE);
        } else {
            EventFactory.attributes(event).put(field, value);
        }
        StepResult step = StepResult.from(eventId, client.postEvent(events.toJson(event), ScenarioStep.JSON));
        boolean passed = step.isResponseReceived() && step.getHttpStatus() == 200 && check.test(step);
        calibration.add((passed ? "OK: " : "FAILED: ") + description + " -> " + step.summarize());
        if (!passed) {
            problems.add("Calibration failed: " + description + " (actual " + step.summarize() + ")");
        }
    }

    private static String text(JsonNode node, String pointer) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.at(pointer);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isBoolean() ? null : value.booleanValue();
    }
}
