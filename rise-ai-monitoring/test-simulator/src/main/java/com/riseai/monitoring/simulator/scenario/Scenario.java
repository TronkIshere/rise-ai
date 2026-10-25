package com.riseai.monitoring.simulator.scenario;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Scenario {
    String scenarioId;
    int index;
    Category category;
    String template;
    String description;
    List<ScenarioStep> steps;

    @Builder.Default
    boolean scorable = true;

    String unscoredReason;
    Set<String> acceptableAiAssessments;

    public String getExpectedOutcomeSource() {
        return steps.stream()
                .map(step -> step.getExpected().getSource())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" | "));
    }

    public String describeExpected() {
        String expected = steps.stream()
                .map(step -> step.getExpected().describe())
                .collect(Collectors.joining(" -> "));
        return acceptableAiAssessments == null ? expected : expected + " and AI assessment in " + acceptableAiAssessments;
    }
}
