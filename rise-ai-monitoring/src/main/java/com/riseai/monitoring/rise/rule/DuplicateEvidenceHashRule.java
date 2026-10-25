package com.riseai.monitoring.rise.rule;

import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.core.finding.FindingCategory;
import com.riseai.monitoring.core.finding.RecommendedAction;
import com.riseai.monitoring.repository.RiseProjectionRepository;
import com.riseai.monitoring.repository.RiseProjectionRepository.EvidenceHashRow;
import com.riseai.monitoring.rise.RiseEnvelope;
import com.riseai.monitoring.rise.RiseEventTypes;
import com.riseai.monitoring.rise.RiseFindingRule;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DuplicateEvidenceHashRule implements RiseFindingRule {
    public static final String ID = "rise.evidence.duplicate-file-hash";

    RiseProjectionRepository projection;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getVersion() {
        return "1";
    }

    @Override
    public String getDescription() {
        return "EV-01: the same evidence file hash was already submitted for a different milestone";
    }

    @Override
    public List<Finding> evaluate(RiseEnvelope envelope) {
        if (!envelope.is(RiseEventTypes.MILESTONE_EVIDENCE_SUBMITTED)) {
            return List.of();
        }
        String hash = envelope.text("fileHash") == null ? null : envelope.text("fileHash").toLowerCase(Locale.ROOT);
        String evidenceId = envelope.text("evidenceId");
        String milestoneId = envelope.text("milestoneId");
        if (hash == null || evidenceId == null || milestoneId == null) {
            return List.of();
        }
        String campaignId = envelope.reference("campaignId");
        List<EvidenceHashRow> earlier = projection.evidenceWithHash(hash, envelope.eventId()).stream()
                .filter(row -> !row.milestoneId().equals(milestoneId) && !row.evidenceId().equals(evidenceId))
                .filter(row -> !row.occurredAt().isAfter(envelope.occurredAt()))
                .toList();
        if (earlier.isEmpty()) {
            return List.of();
        }
        long otherCampaigns = earlier.stream().map(EvidenceHashRow::campaignId).filter(Objects::nonNull)
                .filter(id -> !id.equals(campaignId)).distinct().count();
        Severity severity = otherCampaigns > 0 ? Severity.HIGH : Severity.MEDIUM;
        String explanation = "The same file (SHA-256 " + hash.substring(0, Math.min(12, hash.length())) + "...) was already submitted as evidence for "
                + earlier.stream().map(EvidenceHashRow::milestoneId).distinct().count() + " other milestone(s)"
                + (otherCampaigns > 0 ? ", including " + otherCampaigns + " other campaign(s)" : " of the same campaign")
                + ". Reusing one document can be legitimate, for example an invoice that covers several milestones, so a reviewer should confirm how it applies here. This finding does not establish misuse.";
        return List.of(RuleSupport.finding(ID, getVersion(), envelope, FindingCategory.EVIDENCE_INTEGRITY, severity, "EVIDENCE", evidenceId, hash,
                List.of(RuleSupport.fact("fileHash", hash, envelope),
                        RuleSupport.fact("earlierEvidenceIds", earlier.stream().map(EvidenceHashRow::evidenceId).distinct().toList(), envelope),
                        RuleSupport.fact("earlierMilestoneIds", earlier.stream().map(EvidenceHashRow::milestoneId).distinct().toList(), envelope),
                        RuleSupport.fact("otherCampaignCount", otherCampaigns, envelope)),
                explanation, RecommendedAction.REVIEW_EVIDENCE));
    }
}
