package com.riseai.monitoring.deduplication;

import com.riseai.monitoring.dto.response.monitoring.MonitoringResultResponse;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeduplicationOutcome {
    DeduplicationStatus status;
    MonitoringResultResponse previousResponse;

    public static DeduplicationOutcome of(DeduplicationStatus status) {
        return DeduplicationOutcome.builder().status(status).build();
    }
}
