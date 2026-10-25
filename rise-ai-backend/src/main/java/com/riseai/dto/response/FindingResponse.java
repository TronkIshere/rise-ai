package com.riseai.dto.response;

import com.riseai.entity.MonitoringFinding;
import com.riseai.enums.FindingReviewStatus;
import com.riseai.enums.FindingSeverity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
