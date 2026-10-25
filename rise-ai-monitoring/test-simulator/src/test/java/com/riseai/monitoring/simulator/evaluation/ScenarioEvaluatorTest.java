package com.riseai.monitoring.simulator.evaluation;

import com.riseai.monitoring.simulator.client.CoreResponse;
import com.riseai.monitoring.simulator.client.TransportOutcome;
import com.riseai.monitoring.simulator.scenario.Category;
import com.riseai.monitoring.simulator.scenario.ExpectedOutcome;
import com.riseai.monitoring.simulator.scenario.Outcomes;
import com.riseai.monitoring.simulator.scenario.Scenario;
import com.riseai.monitoring.simulator.scenario.ScenarioStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScenarioEvaluator evaluator = new ScenarioEvaluator("INCONCLUSIVE");

    @Test
    void passesWhenTheResponseMatchesTheExpectation() {
        ScenarioResult result = evaluator.evaluate(single(Outcomes.normal()), List.of(ok(normalData())));

        assertThat(result.getStatus()).isEqualTo(ScenarioStatus.PASS);
        assertThat(result.getRequestBodies()).isEmpty();
        assertThat(result.getSteps().get(0).getExpectedAnomalous()).isFalse();
        assertThat(result.getSteps().get(0).getActualAnomalous()).isFalse();
    }

    @Test
    void failsWithAPreciseReasonAndKeepsTheRequestBody() {
        ObjectNode data = normalData().put("overallRiskLevel", "HIGH");

        ScenarioResult result = evaluator.evaluate(single(Outcomes.normal()), List.of(ok(data)));

        assertThat(result.getStatus()).isEqualTo(ScenarioStatus.FAIL);
        assertThat(result.getFailureReason()).contains("overallRiskLevel expected LOW, actual HIGH");
        assertThat(result.getRequestBodies()).containsExactly("{\"eventId\":\"evt-1\"}");
        assertThat(result.getSteps().get(0).getActualAnomalous()).isTrue();
    }

    @Test
    void failsWhenAnUnexpectedRuleFires() {
        ObjectNode data = normalData();
        data.putArray("ruleFindings").addObject().put("ruleId", "attribute-threshold");

        assertThat(evaluator.evaluate(single(Outcomes.normal()), List.of(ok(data))).getFailureReason())
                .contains("expected no rule findings, actual [attribute-threshold]");
    }

    @Test
    void comparesHttpStatusAndErrorCodeForRejectedRequests() {
        ExpectedOutcome expected = Outcomes.error(400, 1000, "test");

        assertThat(evaluator.evaluate(single(expected), List.of(error(400, 1000))).getStatus()).isEqualTo(ScenarioStatus.PASS);
        ScenarioResult wrongCode = evaluator.evaluate(single(expected), List.of(error(400, 1001)));
        assertThat(wrongCode.getStatus()).isEqualTo(ScenarioStatus.FAIL);
        assertThat(wrongCode.getFailureReason()).contains("error code expected 1000, actual 1001");
        assertThat(evaluator.evaluate(single(expected), List.of(ok(normalData()))).getFailureReason())
                .contains("HTTP status expected 400, actual 200");
    }

    @Test
    void reportsTransportFailuresAsErrors() {
        CoreResponse timeout = CoreResponse.builder().outcome(TransportOutcome.TIMEOUT).error("HttpTimeoutException").build();

        ScenarioResult result = evaluator.evaluate(single(Outcomes.normal()), List.of(timeout));

        assertThat(result.getStatus()).isEqualTo(ScenarioStatus.ERROR);
        assertThat(result.getFailureReason()).startsWith("TIMEOUT");
        assertThat(result.getSteps().get(0).getActualAnomalous()).isNull();
    }

    @Test
    void reportsUnreadableBodiesAndMissingStepsAsErrors() {
        CoreResponse unreadable = CoreResponse.builder().outcome(TransportOutcome.RESPONSE).httpStatus(502).build();
        assertThat(evaluator.evaluate(single(Outcomes.normal()), List.of(unreadable)).getFailureReason())
                .contains("response body is not a JSON object");

        Scenario twoSteps = scenario(List.of(step(Outcomes.normal()), step(Outcomes.normal())), true, null, null);
        assertThat(evaluator.evaluate(twoSteps, List.of(ok(normalData()))).getFailureReason())
                .isEqualTo("only 1 of 2 steps were executed");
    }

    @Test
    void exploratoryScenariosAreUnscoredUnlessARuleCheckFails() {
        Scenario exploratory = scenario(List.of(step(Outcomes.normal())), false, "no defensible rubric", null);

        ScenarioResult result = evaluator.evaluate(exploratory, List.of(ok(normalData())));

        assertThat(result.getStatus()).isEqualTo(ScenarioStatus.UNSCORED);
        assertThat(result.getUnscoredReason()).isEqualTo("no defensible rubric");
        assertThat(evaluator.evaluate(exploratory, List.of(ok(normalData().put("overallRiskLevel", "HIGH")))).getStatus())
                .isEqualTo(ScenarioStatus.FAIL);
    }

    @Test
    void aiRubricIsAppliedOnlyWhenTheAnalysisCompleted() {
        ExpectedOutcome flagged = Outcomes.flagged("attribute-threshold", "HIGH", "test");
        Scenario rubric = scenario(List.of(step(flagged)), true, null, Set.of("NEEDS_REVIEW", "LIKELY_ANOMALOUS"));

        ScenarioResult missingAssessment = evaluator.evaluate(rubric, List.of(ok(flaggedData("COMPLETED", "COMPLETED", true))));
        assertThat(missingAssessment.getStatus()).isEqualTo(ScenarioStatus.FAIL);
        assertThat(missingAssessment.getFailureReason()).contains("AI assessment null is not one of");

        ObjectNode completed = flaggedData("COMPLETED", "COMPLETED", true);
        ((ObjectNode) completed.get("aiAnalysis")).put("assessment", "LIKELY_ANOMALOUS");
        ScenarioResult pass = evaluator.evaluate(rubric, List.of(ok(completed)));
        assertThat(pass.getStatus()).isEqualTo(ScenarioStatus.PASS);
        assertThat(pass.isAiScored()).isTrue();
        assertThat(pass.isAiAssessmentAcceptable()).isTrue();

        ObjectNode reassuring = flaggedData("COMPLETED", "COMPLETED", false);
        ((ObjectNode) reassuring.get("aiAnalysis")).put("assessment", "CONSISTENT_WITH_EXPECTED_BEHAVIOR");
        ScenarioResult fail = evaluator.evaluate(rubric, List.of(ok(reassuring)));
        assertThat(fail.getStatus()).isEqualTo(ScenarioStatus.FAIL);
        assertThat(fail.isAiScored()).isTrue();
        assertThat(fail.isAiAssessmentAcceptable()).isFalse();

        ScenarioResult unavailable = evaluator.evaluate(rubric, List.of(ok(flaggedData("UNAVAILABLE", "INCONCLUSIVE", true))));
        assertThat(unavailable.getStatus()).isEqualTo(ScenarioStatus.UNSCORED);
        assertThat(unavailable.getUnscoredReason()).contains("did not complete (UNAVAILABLE)");
        assertThat(unavailable.isAiScored()).isFalse();
    }

    @Test
    void aiFailureContractIsCheckedForEveryObservedAiStatus() {
        Scenario resilience = single(Outcomes.flagged("attribute-threshold", "HIGH", "test").toBuilder().aiResilienceInvariant(true).build());

        assertThat(evaluator.evaluate(resilience, List.of(ok(flaggedData("UNAVAILABLE", "INCONCLUSIVE", true)))).getStatus())
                .isEqualTo(ScenarioStatus.PASS);
        assertThat(evaluator.evaluate(resilience, List.of(ok(flaggedData("TIMEOUT", "INCONCLUSIVE", false)))).getFailureReason())
                .contains("reviewRequired with AI TIMEOUT expected true, actual false");
        assertThat(evaluator.evaluate(resilience, List.of(ok(flaggedData("UNAVAILABLE", "COMPLETED", true)))).getStatus())
                .isEqualTo(ScenarioStatus.FAIL);
        assertThat(evaluator.evaluate(resilience, List.of(ok(flaggedData("COMPLETED", "COMPLETED", true)))).getStatus())
                .isEqualTo(ScenarioStatus.PASS);
        assertThat(evaluator.evaluate(resilience, List.of(ok(flaggedData("DISABLED", "COMPLETED_WITHOUT_AI", true)))).getStatus())
                .isEqualTo(ScenarioStatus.PASS);
        assertThat(evaluator.evaluate(resilience, List.of(ok(flaggedData("SKIPPED", "COMPLETED", false)))).getFailureReason())
                .contains("is not valid for an event flagged by rules");
        assertThat(new ScenarioEvaluator("RULES_ONLY").evaluate(resilience,
                List.of(ok(flaggedData("TIMEOUT", "COMPLETED_WITHOUT_AI", true)))).getStatus()).isEqualTo(ScenarioStatus.PASS);
    }

    @Test
    void responsesWithMissingFieldsAreFailuresNotCrashes() {
        ObjectNode incomplete = normalData();
        incomplete.remove("overallRiskLevel");
        incomplete.remove("aiAnalysis");
        Scenario resilience = single(Outcomes.flagged("attribute-threshold", "HIGH", "test").toBuilder().aiResilienceInvariant(true).build());

        ScenarioResult result = evaluator.evaluate(resilience, List.of(ok(incomplete)));

        assertThat(result.getStatus()).isEqualTo(ScenarioStatus.FAIL);
        assertThat(result.getFailureReason()).contains("overallRiskLevel expected HIGH, actual null")
                .contains("aiAnalysis.analysisStatus null is not valid");
    }

    @Test
    void replayedDuplicatesMustKeepTheOriginalCreationTime() {
        ExpectedOutcome replay = Outcomes.normal().toBuilder().duplicate(true).sameCreatedAtAsPreviousStep(true).anomalous(null).build();
        Scenario duplicate = scenario(List.of(step(Outcomes.normal()), step(replay)), true, null, null);

        ScenarioResult same = evaluator.evaluate(duplicate, List.of(ok(normalData()), ok(normalData().put("duplicate", true))));
        assertThat(same.getStatus()).isEqualTo(ScenarioStatus.PASS);
        assertThat(same.getSteps().get(1).getActualAnomalous()).isNull();

        ScenarioResult different = evaluator.evaluate(duplicate, List.of(ok(normalData()),
                ok(normalData().put("duplicate", true).put("createdAt", "2026-10-24T00:00:09Z"))));
        assertThat(different.getFailureReason()).contains("step 2: replayed createdAt");
    }

    private ObjectNode normalData() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("eventId", "evt-1");
        data.put("monitoringStatus", "COMPLETED");
        data.put("duplicate", false);
        data.put("ruleDecision", "RULES_SUFFICIENT");
        data.putArray("ruleFindings");
        data.putObject("aiAnalysis").put("analysisStatus", "SKIPPED");
        data.put("overallRiskLevel", "LOW");
        data.put("reviewRequired", false);
        data.put("createdAt", "2026-10-24T00:00:00Z");
        return data;
    }

    private ObjectNode flaggedData(String aiStatus, String monitoringStatus, boolean reviewRequired) {
        ObjectNode data = normalData();
        data.put("monitoringStatus", monitoringStatus);
        data.put("ruleDecision", "REQUIRES_AI_ANALYSIS");
        data.putArray("ruleFindings").addObject().put("ruleId", "attribute-threshold");
        data.putObject("aiAnalysis").put("analysisStatus", aiStatus);
        data.put("overallRiskLevel", "HIGH");
        data.put("reviewRequired", reviewRequired);
        return data;
    }

    private CoreResponse ok(ObjectNode data) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("code", 200);
        root.set("data", data);
        return CoreResponse.builder().outcome(TransportOutcome.RESPONSE).httpStatus(200).body(root).latencyNanos(2_000_000).build();
    }

    private CoreResponse error(int httpStatus, int code) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("status", code);
        root.put("error", "test");
        return CoreResponse.builder().outcome(TransportOutcome.RESPONSE).httpStatus(httpStatus).body(root).latencyNanos(1_000_000).build();
    }

    private ScenarioStep step(ExpectedOutcome expected) {
        return ScenarioStep.builder().eventId("evt-1").body("{\"eventId\":\"evt-1\"}").expected(expected).build();
    }

    private Scenario single(ExpectedOutcome expected) {
        return scenario(List.of(step(expected)), true, null, null);
    }

    private Scenario scenario(List<ScenarioStep> steps, boolean scorable, String unscoredReason, Set<String> acceptable) {
        return Scenario.builder()
                .scenarioId("S000001")
                .index(0)
                .category(Category.NORMAL)
                .template("test")
                .description("test scenario")
                .steps(steps)
                .scorable(scorable)
                .unscoredReason(unscoredReason)
                .acceptableAiAssessments(acceptable)
                .build();
    }
}
