package com.riseai.monitoring.rise.rule;

import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.core.finding.FindingCategory;
import com.riseai.monitoring.core.finding.RecommendedAction;
import com.riseai.monitoring.repository.RiseProjectionRepository;
import com.riseai.monitoring.repository.RiseProjectionRepository.DisbursementEventRow;
import com.riseai.monitoring.rise.RiseEnvelope;
import com.riseai.monitoring.rise.RiseEventTypes;
import com.riseai.monitoring.rise.RiseFindingRule;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MisuseDisputeAfterReleaseRule implements RiseFindingRule {
    public static final String ID = "rise.dispute.misuse-after-release";

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
        return "DP-01: a misuse-of-funds dispute was opened on a milestone that already received funds";
    }

    @Override
    public List<Finding> evaluate(RiseEnvelope envelope) {
        if (!envelope.is(RiseEventTypes.DISPUTE_OPENED) || !"MISUSE_OF_FUNDS".equals(envelope.text("disputeType"))) {
            return List.of();
        }
        String disputeId = envelope.text("disputeId");
        String milestoneId = envelope.text("milestoneId");
        if (!RuleSupport.validId(disputeId) || !RuleSupport.validId(milestoneId)) {
            return List.of();
        }
        List<DisbursementEventRow> releases = projection.disbursementEvents(milestoneId, RiseEventTypes.DISBURSEMENT_RELEASED, envelope.eventId())
                .stream()
                .filter(row -> !row.occurredAt().isAfter(envelope.occurredAt()))
                .toList();
        if (releases.isEmpty()) {
            return List.of();
        }
        BigDecimal released = releases.stream().map(DisbursementEventRow::amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return List.of(RuleSupport.finding(ID, getVersion(), envelope, FindingCategory.DISPUTE_PATTERN, Severity.HIGH, "DISPUTE", disputeId, null,
                List.of(RuleSupport.fact("releaseCount", releases.size(), envelope), RuleSupport.fact("releasedAmount", released.toPlainString(), envelope)),
                "A misuse-of-funds dispute was opened on a milestone that has already had " + releases.size() + " release(s) totalling "
                        + released.toPlainString() + ". Released funds cannot be recovered by the platform, so the dispute deserves early attention. A dispute is an allegation, not a finding of fact.",
                RecommendedAction.REVIEW_EVIDENCE));
    }
}
