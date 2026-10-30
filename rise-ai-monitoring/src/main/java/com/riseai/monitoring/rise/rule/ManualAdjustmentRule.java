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
public class ManualAdjustmentRule implements RiseFindingRule {
    public static final String ID = "rise.override.manual-adjustment";

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
        return "AO-01: a dual-controlled manual escrow adjustment was applied";
    }

    @Override
    public List<Finding> evaluate(RiseEnvelope envelope) {
        if (!envelope.is(RiseEventTypes.ADJUSTMENT_APPLIED)) {
            return List.of();
        }
        String adjustmentId = envelope.text("adjustmentId");
        BigDecimal amount = envelope.decimal("amount");
        if (!RuleSupport.validId(adjustmentId) || amount == null) {
            return List.of();
        }
        boolean large = amount.compareTo(properties.getRise().getAdjustmentReviewAmount()) >= 0;
        return List.of(RuleSupport.finding(ID, getVersion(), envelope, FindingCategory.ADMINISTRATIVE_OVERRIDE, large ? Severity.MEDIUM : Severity.INFO,
                "ESCROW_ADJUSTMENT", adjustmentId, null,
                List.of(RuleSupport.fact("direction", RuleSupport.direction(envelope.text("direction")), envelope),
                        RuleSupport.fact("amount", amount.toPlainString(), envelope), RuleSupport.fact("currency", RuleSupport.currency(envelope.text("currency")), envelope)),
                "A manual escrow " + RuleSupport.orUnspecified(RuleSupport.direction(envelope.text("direction"))).toLowerCase() + " adjustment of " + amount.toPlainString() + " "
                        + RuleSupport.orUnspecified(RuleSupport.currency(envelope.text("currency"))) + " was applied under dual control. Recorded for oversight of administrative changes to ledger balances.",
                large ? RecommendedAction.REVIEW_ADMIN_ACTION : RecommendedAction.NONE_INFORMATIONAL));
    }
}
