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
import java.util.ArrayList;
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
        if (envelope.is(RiseEventTypes.DISBURSEMENT_REJECTED)) {
            return requestsAlreadyDeliveredBeforeThisRejection(envelope);
        }
        if (!envelope.is(RiseEventTypes.DISBURSEMENT_REQUESTED)) {
            return List.of();
        }
        String requestId = envelope.text("requestId");
        String milestoneId = envelope.text("milestoneId");
        if (!RuleSupport.validId(requestId) || !RuleSupport.validId(milestoneId)) {
            return List.of();
        }
        List<DisbursementEventRow> rejections = rejectionsWithin(milestoneId, envelope.eventId(), envelope.occurredAt());
        if (rejections.isEmpty()) {
            return List.of();
        }
        DisbursementEventRow latest = rejections.get(rejections.size() - 1);
        return List.of(finding(envelope, requestId, latest, envelope.occurredAt(), rejections.size()));
    }

    private List<Finding> requestsAlreadyDeliveredBeforeThisRejection(RiseEnvelope rejection) {
        String milestoneId = rejection.text("milestoneId");
        if (!RuleSupport.validId(rejection.text("requestId")) || !RuleSupport.validId(milestoneId)) {
            return List.of();
        }
        Instant until = rejection.occurredAt().plus(properties.getRise().getResubmissionWindow());
        DisbursementEventRow thisRejection = new DisbursementEventRow(rejection.eventId(), rejection.text("requestId"), null, rejection.occurredAt());
        List<Finding> findings = new ArrayList<>();
        for (DisbursementEventRow request : projection.disbursementEvents(milestoneId, RiseEventTypes.DISBURSEMENT_REQUESTED, rejection.eventId())) {
            boolean inWindow = !request.occurredAt().isBefore(rejection.occurredAt()) && !request.occurredAt().isAfter(until);
            if (inWindow && RuleSupport.validId(request.requestId())
                    && rejectionsWithin(milestoneId, rejection.eventId(), request.occurredAt()).isEmpty()) {
                findings.add(finding(rejection, request.requestId(), thisRejection, request.occurredAt(), 1));
            }
        }
        return findings;
    }

    private List<DisbursementEventRow> rejectionsWithin(String milestoneId, String excludeEventId, Instant requestedAt) {
        Instant since = requestedAt.minus(properties.getRise().getResubmissionWindow());
        return projection.disbursementEvents(milestoneId, RiseEventTypes.DISBURSEMENT_REJECTED, excludeEventId).stream()
                .filter(row -> !row.occurredAt().isAfter(requestedAt) && !row.occurredAt().isBefore(since))
                .toList();
    }

    private Finding finding(RiseEnvelope source, String requestId, DisbursementEventRow rejection, Instant requestedAt, int rejectionsInWindow) {
        long minutes = Duration.between(rejection.occurredAt(), requestedAt).toMinutes();
        return RuleSupport.finding(ID, getVersion(), source, FindingCategory.DISBURSEMENT_PATTERN, Severity.MEDIUM,
                "DISBURSEMENT_REQUEST", requestId, null,
                List.of(RuleSupport.fact("rejectedRequestId", RuleSupport.code(rejection.requestId()), source),
                        RuleSupport.fact("minutesSinceRejection", minutes, source),
                        RuleSupport.fact("rejectionsInWindow", rejectionsInWindow, source)),
                "A new disbursement request was made " + minutes + " minute(s) after a rejected request on the same milestone. Corrected resubmissions are common; a reviewer should check what changed since the rejection.",
                RecommendedAction.REVIEW_EVIDENCE);
    }
}
