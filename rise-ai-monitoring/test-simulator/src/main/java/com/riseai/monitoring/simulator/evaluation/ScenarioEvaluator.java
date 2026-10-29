package com.riseai.monitoring.simulator.evaluation;

import com.riseai.monitoring.simulator.client.CoreResponse;
import com.riseai.monitoring.simulator.scenario.ExpectedOutcome;
import com.riseai.monitoring.simulator.scenario.Scenario;
import com.riseai.monitoring.simulator.scenario.ScenarioStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ScenarioEvaluator {

    private static final Set<String> AI_NOT_COMPLETED = Set.of("UNAVAILABLE", "TIMEOUT", "INVALID_RESPONSE", "FAILED");
    private static final Set<String> ELEVATED_RISK = Set.of("MEDIUM", "HIGH", "CRITICAL");
    private static final int MAX_STORED_BODY_LENGTH = 1000;

    private final boolean rulesOnlyOnAiFailure;

    public ScenarioEvaluator(String aiFailureMode) {
        this.rulesOnlyOnAiFailure = "RULES_ONLY".equals(aiFailureMode);
    }

    public ScenarioResult evaluate(Scenario scenario, List<CoreResponse> responses) {
        List<StepResult> steps = new ArrayList<>();
        List<String> mismatches = new ArrayList<>();
        String error = null;
        String previousCreatedAt = null;
        boolean multiStep = scenario.getSteps().size() > 1;

        for (int position = 0; position < responses.size(); position++) {
            ScenarioStep step = scenario.getSteps().get(position);
            StepResult actual = StepResult.from(step.getEventId(), responses.get(position));
            String prefix = multiStep ? "step " + (position + 1) + ": " : "";
            steps.add(label(step, actual));
            if (!actual.isResponseReceived()) {
                error = prefix + actual.summarize();
                break;
            }
            if (!actual.isBodyReadable()) {
                error = prefix + "response body is not a JSON object (HTTP " + actual.getHttpStatus() + ")";
                break;
            }
            compare(step, actual, previousCreatedAt, prefix, mismatches);
            previousCreatedAt = actual.getCreatedAt();
        }
        if (error == null && steps.size() < scenario.getSteps().size()) {
            error = "only " + steps.size() + " of " + scenario.getSteps().size() + " steps were executed";
        }

        boolean aiScored = false;
        boolean aiAcceptable = false;
        String aiUnscoredReason = null;
        if (error == null && scenario.getAcceptableAiAssessments() != null) {
            StepResult last = steps.get(steps.size() - 1);
            if ("COMPLETED".equals(last.getAiStatus())) {
                aiScored = true;
                aiAcceptable = contains(scenario.getAcceptableAiAssessments(), last.getAssessment());
                if (!aiAcceptable) {
                    mismatches.add("AI assessment " + last.getAssessment() + " is not one of "
                            + new TreeSet<>(scenario.getAcceptableAiAssessments()));
                }
            } else {
                aiUnscoredReason = "AI analysis did not complete (" + last.getAiStatus() + "), so the AI rubric cannot be applied";
            }
        }

        ScenarioStatus status;
        String failureReason = null;
        String unscoredReason = null;
        if (error != null) {
            status = ScenarioStatus.ERROR;
            failureReason = error;
        } else if (!mismatches.isEmpty()) {
            status = ScenarioStatus.FAIL;
            failureReason = String.join("; ", mismatches);
        } else if (!scenario.isScorable()) {
            status = ScenarioStatus.UNSCORED;
            unscoredReason = scenario.getUnscoredReason();
        } else if (aiUnscoredReason != null) {
            status = ScenarioStatus.UNSCORED;
            unscoredReason = aiUnscoredReason;
        } else {
            status = ScenarioStatus.PASS;
        }

        boolean keepBodies = status == ScenarioStatus.FAIL || status == ScenarioStatus.ERROR;
        return ScenarioResult.builder()
                .scenarioId(scenario.getScenarioId())
                .index(scenario.getIndex())
                .category(scenario.getCategory())
                .template(scenario.getTemplate())
                .description(scenario.getDescription())
                .status(status)
                .failureReason(failureReason)
                .unscoredReason(unscoredReason)
                .expectedSummary(scenario.describeExpected())
                .actualSummary(steps.stream().map(StepResult::summarize).collect(Collectors.joining(" -> ")))
                .expectedOutcomeSource(scenario.getExpectedOutcomeSource())
                .steps(List.copyOf(steps))
                .requestBodies(keepBodies ? scenario.getSteps().stream().map(step -> truncate(step.getBody())).toList() : List.of())
                .aiScored(aiScored)
                .aiAssessmentAcceptable(aiAcceptable)
                .build();
    }

    private void compare(ScenarioStep step, StepResult actual, String previousCreatedAt, String prefix, List<String> mismatches) {
        ExpectedOutcome expected = step.getExpected();
        expect(mismatches, prefix, "HTTP status", expected.getHttpStatus(), actual.getHttpStatus());
        if (expected.getErrorCode() != null) {
            expect(mismatches, prefix, "error code", expected.getErrorCode(), actual.getErrorCode());
        }
        if (actual.getHttpStatus() != 200 || actual.getMonitoringStatus() == null) {
            return;
        }
        if (step.getEventId() != null) {
            expect(mismatches, prefix, "eventId echo", step.getEventId(), actual.getResponseEventId());
        }
        if (expected.getMonitoringStatus() != null) {
            expect(mismatches, prefix, "monitoringStatus", expected.getMonitoringStatus(), actual.getMonitoringStatus());
        }
        if (expected.getRuleDecision() != null) {
            expect(mismatches, prefix, "ruleDecision", expected.getRuleDecision(), actual.getRuleDecision());
        }
        Set<String> missing = new TreeSet<>(expected.getRequiredRuleIds());
        actual.getRuleIds().forEach(missing::remove);
        if (!missing.isEmpty()) {
            mismatches.add(prefix + "rule findings missing " + missing + ", actual " + actual.getRuleIds());
        }
        expected.getRequiredFindings().forEach((ruleId, severity) -> {
            String actualSeverity = actual.getFindingSeverities().get(ruleId);
            if (actualSeverity == null) {
                mismatches.add(prefix + "finding " + ruleId + " missing, actual " + actual.getFindingSeverities());
            } else if (!actualSeverity.equals(severity)) {
                mismatches.add(prefix + "finding " + ruleId + " severity expected " + severity + ", actual " + actualSeverity);
            }
        });
        expected.getForbiddenFindings().stream().filter(actual.getFindingSeverities()::containsKey)
                .forEach(ruleId -> mismatches.add(prefix + "finding " + ruleId + " was not expected"));
        if (expected.isNoFindings() && !actual.getRuleIds().isEmpty()) {
            mismatches.add(prefix + "expected no rule findings, actual " + actual.getRuleIds());
        }
        if (expected.getRiskLevel() != null) {
            expect(mismatches, prefix, "overallRiskLevel", expected.getRiskLevel(), actual.getRiskLevel());
        }
        if (expected.getDuplicate() != null) {
            expect(mismatches, prefix, "duplicate", expected.getDuplicate(), actual.getDuplicate());
        }
        if (expected.getReviewRequired() != null) {
            expect(mismatches, prefix, "reviewRequired", expected.getReviewRequired(), actual.getReviewRequired());
        }
        if (expected.getAiStatus() != null) {
            expect(mismatches, prefix, "aiAnalysis.analysisStatus", expected.getAiStatus(), actual.getAiStatus());
        }
        if (expected.getCorrelationId() != null) {
            expect(mismatches, prefix, "correlationId", expected.getCorrelationId(), actual.getCorrelationId());
        }
        if (expected.isSameCreatedAtAsPreviousStep() && !Objects.equals(previousCreatedAt, actual.getCreatedAt())) {
            mismatches.add(prefix + "replayed createdAt " + actual.getCreatedAt() + " differs from original " + previousCreatedAt);
        }
        if (expected.isAiResilienceInvariant()) {
            checkAiFailureContract(actual, prefix, mismatches);
        }
    }

    private void checkAiFailureContract(StepResult actual, String prefix, List<String> mismatches) {
        String aiStatus = actual.getAiStatus();
        if ("COMPLETED".equals(aiStatus)) {
            expect(mismatches, prefix, "monitoringStatus with AI COMPLETED", "COMPLETED", actual.getMonitoringStatus());
        } else if ("DISABLED".equals(aiStatus)) {
            expect(mismatches, prefix, "monitoringStatus with AI DISABLED", "COMPLETED_WITHOUT_AI", actual.getMonitoringStatus());
        } else if (contains(AI_NOT_COMPLETED, aiStatus)) {
            String expectedStatus = rulesOnlyOnAiFailure ? "COMPLETED_WITHOUT_AI" : "INCONCLUSIVE";
            expect(mismatches, prefix, "monitoringStatus with AI " + aiStatus, expectedStatus, actual.getMonitoringStatus());
            expect(mismatches, prefix, "reviewRequired with AI " + aiStatus, true, actual.getReviewRequired());
        } else {
            mismatches.add(prefix + "aiAnalysis.analysisStatus " + aiStatus + " is not valid for an event flagged by rules");
        }
    }

    private StepResult label(ScenarioStep step, StepResult actual) {
        return actual.toBuilder()
                .expectedAnomalous(step.getExpected().getAnomalous())
                .actualAnomalous(classify(actual))
                .build();
    }

    private Boolean classify(StepResult actual) {
        if (!actual.isResponseReceived() || actual.getHttpStatus() != 200 || actual.getMonitoringStatus() == null
                || "REJECTED".equals(actual.getMonitoringStatus()) || Boolean.TRUE.equals(actual.getDuplicate())) {
            return null;
        }
        return Boolean.TRUE.equals(actual.getReviewRequired()) || contains(ELEVATED_RISK, actual.getRiskLevel());
    }

    private static boolean contains(Set<String> values, String value) {
        return value != null && values.contains(value);
    }

    private void expect(List<String> mismatches, String prefix, String field, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            mismatches.add(prefix + field + " expected " + expected + ", actual " + actual);
        }
    }

    private String truncate(String body) {
        return body.length() <= MAX_STORED_BODY_LENGTH ? body : body.substring(0, MAX_STORED_BODY_LENGTH) + "...(truncated)";
    }
}
