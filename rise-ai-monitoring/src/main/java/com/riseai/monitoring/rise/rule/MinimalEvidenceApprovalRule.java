package com.riseai.monitoring.rise.rule;

import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.core.finding.FindingCategory;
import com.riseai.monitoring.core.finding.RecommendedAction;
import com.riseai.monitoring.rise.RiseEnvelope;
import com.riseai.monitoring.rise.RiseEventTypes;
import com.riseai.monitoring.rise.RiseFindingRule;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MinimalEvidenceApprovalRule implements RiseFindingRule {
    public static final String ID = "rise.disbursement.minimal-evidence-approval";

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
        return "DB-01: a large disbursement was approved with the minimum number of accepted evidence records";
    }

    @Override
    public List<Finding> evaluate(RiseEnvelope envelope) {
        if (!envelope.is(RiseEventTypes.DISBURSEMENT_APPROVED)) {
            return List.of();
        }
        String requestId = envelope.text("requestId");
        BigDecimal amount = envelope.decimal("amount");
        long accepted = envelope.number("acceptedEvidenceCount", -1);
        MonitoringProperties.Rise rise = properties.getRise();
        if (!RuleSupport.validId(requestId) || amount == null || accepted < 0 || accepted > rise.getMinimalEvidenceCount()
                || amount.compareTo(rise.getMinimalEvidenceApprovalAmount()) < 0) {
            return List.of();
        }
        return List.of(RuleSupport.finding(ID, getVersion(), envelope, FindingCategory.DISBURSEMENT_PATTERN, Severity.MEDIUM,
                "DISBURSEMENT_REQUEST", requestId, null,
                List.of(RuleSupport.fact("amount", amount.toPlainString(), envelope), RuleSupport.fact("currency", RuleSupport.currency(envelope.text("currency")), envelope),
                        RuleSupport.fact("acceptedEvidenceCount", accepted, envelope),
                        RuleSupport.fact("reviewThreshold", rise.getMinimalEvidenceApprovalAmount().toPlainString(), envelope)),
                "A disbursement of " + amount.toPlainString() + " " + RuleSupport.orUnspecified(RuleSupport.currency(envelope.text("currency"))) + " was approved with " + accepted
                        + " accepted evidence record(s). This is allowed by platform rules, but larger releases on little documentation deserve a second look.",
                RecommendedAction.REVIEW_EVIDENCE));
    }
}
