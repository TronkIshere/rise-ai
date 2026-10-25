package com.riseai.monitoring.simulator.evaluation;

import com.riseai.monitoring.simulator.scenario.Category;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ScenarioResult {
    String scenarioId;
    int index;
    Category category;
    String template;
    String description;
    ScenarioStatus status;
    String failureReason;
    String unscoredReason;
    String expectedSummary;
    String actualSummary;
    String expectedOutcomeSource;

    @Builder.Default
    List<StepResult> steps = List.of();

    @Builder.Default
    List<String> requestBodies = List.of();

    boolean aiScored;
    boolean aiAssessmentAcceptable;

    public static ScenarioResult simulatorError(int index, Category category, Exception ex) {
        return ScenarioResult.builder()
                .scenarioId(String.format("S%06d", index + 1))
                .index(index)
                .category(category)
                .template("unknown")
                .description("Scenario could not be generated or executed by the simulator")
                .status(ScenarioStatus.ERROR)
                .failureReason("simulator error: " + ex)
                .build();
    }

    public StepResult getFinalStep() {
        return steps.isEmpty() ? null : steps.get(steps.size() - 1);
    }

    public long getTotalLatencyNanos() {
        return steps.stream().mapToLong(StepResult::getLatencyNanos).sum();
    }
}
