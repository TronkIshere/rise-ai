package com.aicore.simulator.scenario;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ScenarioStep {

    public static final String JSON = "application/json";

    String eventId;
    String body;

    @Builder.Default
    String contentType = JSON;

    ExpectedOutcome expected;
}
