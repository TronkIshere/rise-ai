package com.riseai.dto.response;

import com.riseai.entity.FindingOccurrence;
import com.riseai.enums.FindingSeverity;

import java.time.Instant;
import java.util.UUID;

public record OccurrenceResponse(UUID sourceEventId, UUID actorId, FindingSeverity severity, Instant observedAt) {
    public static OccurrenceResponse from(FindingOccurrence o) {
        return new OccurrenceResponse(o.getSourceEventId(), o.getActorId(), o.getSeverity(), o.getObservedAt());
    }
}
