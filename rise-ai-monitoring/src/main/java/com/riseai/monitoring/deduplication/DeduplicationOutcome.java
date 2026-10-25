package com.riseai.monitoring.deduplication;

import com.riseai.monitoring.core.result.MonitoringResult;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeduplicationOutcome {
    DeduplicationStatus status;
    MonitoringResult previousResult;

    public static DeduplicationOutcome of(DeduplicationStatus status) {
        return DeduplicationOutcome.builder().status(status).build();
    }
}
