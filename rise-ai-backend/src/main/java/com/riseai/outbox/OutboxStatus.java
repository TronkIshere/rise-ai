package com.riseai.outbox;

public enum OutboxStatus {
    PENDING,
    IN_FLIGHT,
    PUBLISHED,
    FAILED
}
