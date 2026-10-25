package com.riseai.dto.response;

import java.time.Instant;

public record OutboxStatsResponse(boolean relayEnabled, long pending, long inFlight, long published, long failed,
                                  Instant oldestUndeliveredOccurredAt, Long oldestUndeliveredAgeSeconds) {
}
