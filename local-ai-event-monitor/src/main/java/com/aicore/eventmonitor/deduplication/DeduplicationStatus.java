package com.aicore.eventmonitor.deduplication;

public enum DeduplicationStatus {
    NEW,
    DUPLICATE,
    CONFLICT,
    IN_PROGRESS,
    CAPACITY_EXHAUSTED
}
