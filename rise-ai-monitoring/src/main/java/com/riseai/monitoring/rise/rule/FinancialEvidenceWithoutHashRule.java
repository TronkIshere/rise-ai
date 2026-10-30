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
import java.util.Set;

@Component
public class FinancialEvidenceWithoutHashRule implements RiseFindingRule {
    public static final String ID = "rise.evidence.financial-without-hash";
    private static final Set<String> FINANCIAL_TYPES = Set.of("RECEIPT", "INVOICE");

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
        return "EV-02: a receipt or invoice was submitted without a file hash";
    }

    @Override
    public List<Finding> evaluate(RiseEnvelope envelope) {
        String evidenceType = envelope.text("evidenceType");
        String evidenceId = envelope.text("evidenceId");
        if (!envelope.is(RiseEventTypes.MILESTONE_EVIDENCE_SUBMITTED) || !RuleSupport.validId(evidenceId)
                || evidenceType == null || !FINANCIAL_TYPES.contains(evidenceType) || envelope.fileHash() != null) {
            return List.of();
        }
        return List.of(RuleSupport.finding(ID, getVersion(), envelope, FindingCategory.DATA_QUALITY, Severity.LOW, "EVIDENCE", evidenceId, null,
                List.of(RuleSupport.fact("evidenceType", evidenceType, envelope)),
                "A " + evidenceType.toLowerCase() + " was submitted without a usable file hash, so duplicate-document checks cannot cover it. Ask for the file hash if the document is available.",
                RecommendedAction.REQUEST_CLARIFICATION));
    }
}
