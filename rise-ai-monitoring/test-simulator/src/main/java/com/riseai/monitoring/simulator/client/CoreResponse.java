package com.riseai.monitoring.simulator.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CoreResponse {
    TransportOutcome outcome;
    int httpStatus;
    JsonNode body;
    long latencyNanos;
    String error;

    public boolean isReceived() {
        return outcome == TransportOutcome.RESPONSE;
    }
}
