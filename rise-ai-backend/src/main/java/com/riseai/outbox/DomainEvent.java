package com.riseai.outbox;

import java.util.Map;
import java.util.UUID;

public record DomainEvent(String eventType, String aggregateType, UUID aggregateId, UUID actorId, String actorRole,
                          Map<String, Object> references, Map<String, Object> payload) {
    public static final int SCHEMA_VERSION = 1;
    public static final String PRODUCER = "rise-ai-backend";
    public static final String SYSTEM_ROLE = "SYSTEM";
}
