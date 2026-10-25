package com.riseai.monitoring.rise.rule;

import com.riseai.monitoring.configuration.MonitoringProperties;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RapidResubmissionRule implements RiseFindingRule {
    public static final String ID = "rise.disbursement.rapid-resubmission";

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
        return "DB-02: a disbursement was requested again shortly after a rejection on the same milestone";
    }

    @Override
    public List<Finding> evaluate(RiseEnvelope envelope) {
        if (!envelope.is(RiseEventTypes.DISBURSEMENT_REQUESTED)) {
            return List.of();
        }
        String requestId = envelope.text("requestId");
        String milestoneId = envelope.text("milestoneId");
        if (requestId == null || milestoneId == null) {
            return List.of();
        }
        Duration window = properties.getRise().getResubmissionWindow();
        Instant since = envelope.occurredAt().minus(window);
        List<DisbursementEventRow> rejections = projection.disbursementEvents(milestoneId, RiseEventTypes.DISBURSEMENT_REJECTED, envelope.eventId())
                .stream()
                .filter(row -> !row.occurredAt().isAfter(envelope.occurredAt()) && !row.occurredAt().isBefore(since))
                .toList();
        if (rejections.isEmpty()) {
            return List.of();
        }
        DisbursementEventRow latest = rejections.get(rejections.size() - 1);
        long minutes = Duration.between(latest.occurredAt(), envelope.occurredAt()).toMinutes();
        return List.of(RuleSupport.finding(ID, getVersion(), envelope, FindingCategory.DISBURSEMENT_PATTERN, Severity.MEDIUM,
                "DISBURSEMENT_REQUEST", requestId, null,
                List.of(RuleSupport.fact("rejectedRequestId", latest.requestId(), envelope),
                        RuleSupport.fact("minutesSinceRejection", minutes, envelope),
                        RuleSupport.fact("rejectionsInWindow", rejections.size(), envelope)),
                "A new disbursement request was made " + minutes + " minute(s) after a rejected request on the same milestone. Corrected resubmissions are common; a reviewer should check what changed since the rejection.",
                RecommendedAction.REVIEW_EVIDENCE));
    }
}
