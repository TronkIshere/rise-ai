package com.riseai.monitoring.rise.rule;

import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.core.finding.FindingCategory;
import com.riseai.monitoring.core.finding.RecommendedAction;
import com.riseai.monitoring.rise.RiseEnvelope;
import com.riseai.monitoring.rise.RiseEventTypes;
import com.riseai.monitoring.rise.RiseFindingRule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReconciliationInconsistencyRule implements RiseFindingRule {
    public static final String ID = "rise.ledger.reconciliation-inconsistent";

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
        return "LG-01: an escrow reconciliation run found inconsistencies or failed";
    }

    @Override
    public List<Finding> evaluate(RiseEnvelope envelope) {
        if (!envelope.is(RiseEventTypes.RECONCILIATION_RUN_COMPLETED)) {
            return List.of();
        }
        String status = envelope.text("runStatus");
        String runId = envelope.text("runId");
        if (!RuleSupport.validId(runId) || !("INCONSISTENT".equals(status) || "FAILED".equals(status))) {
            return List.of();
        }
        boolean inconsistent = "INCONSISTENT".equals(status);
        String explanation = inconsistent
                ? "Escrow reconciliation found " + envelope.number("inconsistentAccounts", 0) + " of " + envelope.number("accountsChecked", 0)
                + " account(s) whose stored balances differ from the ledger. Money records must be checked before further releases."
                : "An escrow reconciliation run failed, so ledger consistency was not verified for this run.";
        return List.of(RuleSupport.finding(ID, getVersion(), envelope, FindingCategory.LEDGER_INTEGRITY,
                inconsistent ? Severity.CRITICAL : Severity.HIGH, "RECONCILIATION_RUN", runId, null,
                List.of(RuleSupport.fact("runStatus", status, envelope), RuleSupport.fact("runCode", RuleSupport.code(envelope.text("runCode")), envelope),
                        RuleSupport.fact("accountsChecked", envelope.number("accountsChecked", 0), envelope),
                        RuleSupport.fact("inconsistentAccounts", envelope.number("inconsistentAccounts", 0), envelope)),
                explanation, RecommendedAction.CHECK_RECONCILIATION));
    }
}
