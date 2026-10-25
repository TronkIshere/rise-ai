package com.riseai.monitoring.rise.rule;

import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.core.finding.FindingCategory;
import com.riseai.monitoring.core.finding.RecommendedAction;
import com.riseai.monitoring.repository.RiseProjectionRepository;
import com.riseai.monitoring.rise.RiseEnvelope;
import com.riseai.monitoring.rise.RiseEventTypes;
import com.riseai.monitoring.rise.RiseFindingRule;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RepeatedRequestsRule implements RiseFindingRule {
    public static final String ID = "rise.disbursement.repeated-requests";

    RiseProjectionRepository projection;
    MonitoringProperties properties;

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
        return "DB-03: a milestone has received many disbursement requests";
    }

    @Override
    public List<Finding> evaluate(RiseEnvelope envelope) {
        if (!envelope.is(RiseEventTypes.DISBURSEMENT_REQUESTED)) {
            return List.of();
        }
        String milestoneId = envelope.text("milestoneId");
        if (milestoneId == null) {
            return List.of();
        }
        long count = projection.disbursementEvents(milestoneId, RiseEventTypes.DISBURSEMENT_REQUESTED, envelope.eventId()).stream()
                .filter(row -> !row.occurredAt().isAfter(envelope.occurredAt()))
                .count() + 1;
        int limit = properties.getRise().getMaxRequestsPerMilestone();
        if (count < limit) {
            return List.of();
        }
        return List.of(RuleSupport.finding(ID, getVersion(), envelope, FindingCategory.DISBURSEMENT_PATTERN, Severity.LOW,
                "MILESTONE", milestoneId, null,
                List.of(RuleSupport.fact("requestCount", count, envelope), RuleSupport.fact("reviewThreshold", limit, envelope)),
                "This milestone has received " + count + " disbursement requests. Partial disbursements are normal; many separate requests can still be worth a look at the overall milestone spending.",
                RecommendedAction.REVIEW_EVIDENCE));
    }
}
