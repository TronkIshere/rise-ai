package com.riseai.monitoring.deduplication;

public enum DeduplicationStatus {
    NEW,
    DUPLICATE,
    CONFLICT,
    IN_PROGRESS,
    CAPACITY_EXHAUSTED
}
