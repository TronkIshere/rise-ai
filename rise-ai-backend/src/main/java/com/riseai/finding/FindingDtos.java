package com.riseai.finding;

import com.riseai.outbox.OutboxEvent;
import com.riseai.outbox.OutboxStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FindingDtos {
    private FindingDtos() {
    }

    public record ReviewFindingRequest(@NotNull FindingReviewStatus status, @NotBlank @Size(max = 2000) String note) {
    }

    public record FindingResponse(UUID id, String findingKey, String detectorType, String ruleId, String ruleVersion, String category,
                                  FindingSeverity severity, String subjectType, String subjectId, UUID campaignId, UUID milestoneId,
                                  UUID disbursementRequestId, String explanation, String recommendedAction, List<Map<String, Object>> facts,
                                  FindingReviewStatus reviewStatus, int occurrenceCount, int newOccurrencesSinceReview,
                                  Instant firstSeenAt, Instant lastSeenAt, UUID reviewedBy, Instant reviewedAt, String reviewNote,
                                  boolean advisoryOnly) {
        public static FindingResponse from(MonitoringFinding f) {
            return new FindingResponse(f.getId(), f.getFindingKey(), f.getDetectorType(), f.getRuleId(), f.getRuleVersion(), f.getCategory(),
                    f.getSeverity(), f.getSubjectType(), f.getSubjectId(), f.getCampaignId(), f.getMilestoneId(), f.getDisbursementRequestId(),
                    f.getExplanation(), f.getRecommendedAction(), f.getFacts(), f.getReviewStatus(), f.getOccurrenceCount(),
                    f.getNewOccurrencesSinceReview(), f.getFirstSeenAt(), f.getLastSeenAt(), f.getReviewedBy(), f.getReviewedAt(),
                    f.getReviewNote(), true);
        }
    }

    public record OccurrenceResponse(UUID sourceEventId, UUID actorId, FindingSeverity severity, Instant observedAt) {
        public static OccurrenceResponse from(FindingOccurrence o) {
            return new OccurrenceResponse(o.getSourceEventId(), o.getActorId(), o.getSeverity(), o.getObservedAt());
        }
    }

    public record FindingDetailResponse(FindingResponse finding, List<OccurrenceResponse> occurrences) {
    }

    public record OutboxEventResponse(UUID id, UUID eventId, String eventType, String aggregateType, UUID aggregateId, OutboxStatus status,
                                      int attempts, Instant occurredAt, Instant nextAttemptAt, Instant publishedAt, String lastError) {
        public static OutboxEventResponse from(OutboxEvent e) {
            return new OutboxEventResponse(e.getId(), e.getEventId(), e.getEventType(), e.getAggregateType(), e.getAggregateId(), e.getStatus(),
                    e.getAttempts(), e.getOccurredAt(), e.getNextAttemptAt(), e.getPublishedAt(), e.getLastError());
        }
    }

    public record OutboxStatsResponse(boolean relayEnabled, long pending, long inFlight, long published, long failed,
                                      Instant oldestUndeliveredOccurredAt, Long oldestUndeliveredAgeSeconds) {
    }
}
