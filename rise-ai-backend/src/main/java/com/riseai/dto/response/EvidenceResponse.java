package com.riseai.dto.response;

import com.riseai.entity.EvidenceRecord;
import com.riseai.enums.EvidenceStatus;
import com.riseai.enums.EvidenceType;

import java.time.Instant;
import java.util.UUID;

public record EvidenceResponse(UUID id, UUID campaignId, UUID milestoneId, UUID disbursementRequestId, UUID submittedBy,
                               EvidenceType evidenceType, String title, String description, String fileUrl, String fileHash,
                               String externalReference, EvidenceStatus evidenceStatus, Instant submittedAt, UUID reviewedBy,
                               Instant reviewedAt, String reviewNote) {
    public static EvidenceResponse from(EvidenceRecord e) {
        return new EvidenceResponse(e.getId(), e.getCampaignId(), e.getMilestoneId(), e.getDisbursementRequestId(), e.getSubmittedBy(),
                e.getEvidenceType(), e.getTitle(), e.getDescription(), e.getFileUrl(), e.getFileHash(), e.getExternalReference(),
                e.getEvidenceStatus(), e.getSubmittedAt(), e.getReviewedBy(), e.getReviewedAt(), e.getReviewNote());
    }
}
