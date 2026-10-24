package com.riseai.evidence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class EvidenceDtos {
    private EvidenceDtos() {
    }

    public record EvidenceRequest(
            @NotNull EvidenceType evidenceType,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @Size(max = 500) @Pattern(regexp = "^((https|sim)://\\S+)?$", message = "must be an https:// or sim:// reference") String fileUrl,
            @Pattern(regexp = "^([a-fA-F0-9]{64})?$", message = "must be a SHA-256 hex digest") String fileHash,
            @Size(max = 500) String externalReference) {
    }

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
}
