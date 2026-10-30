package com.riseai.dto.response;

import java.time.Instant;

public record AlertStatsResponse(boolean webhookConfigured, long pending, long retrying, long sent, long failed,
                                 Instant oldestUndeliveredCreatedAt, Long oldestUndeliveredAgeSeconds) {
}
