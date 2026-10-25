package com.riseai.monitoring.simulator.scenario;

import java.util.Set;

public final class Outcomes {

    private Outcomes() {
    }

    public static ExpectedOutcome normal() {
        return ExpectedOutcome.builder()
                .httpStatus(200)
                .monitoringStatus("COMPLETED")
                .ruleDecision("RULES_SUFFICIENT")
                .noFindings(true)
                .riskLevel("LOW")
                .duplicate(false)
                .reviewRequired(false)
                .aiStatus("SKIPPED")
                .anomalous(false)
                .source(CoreContract.SRC_NORMAL)
                .build();
    }

    public static ExpectedOutcome flagged(String ruleId, String riskLevel, String source) {
        return ExpectedOutcome.builder()
                .httpStatus(200)
                .ruleDecision("REQUIRES_AI_ANALYSIS")
                .requiredRuleIds(Set.of(ruleId))
                .riskLevel(riskLevel)
                .duplicate(false)
                .anomalous(true)
                .source(source)
                .build();
    }

    public static ExpectedOutcome rejected(String ruleId, String source) {
        return ExpectedOutcome.builder()
                .httpStatus(200)
                .monitoringStatus("REJECTED")
                .ruleDecision("REJECT")
                .requiredRuleIds(Set.of(ruleId))
                .riskLevel("UNKNOWN")
                .duplicate(false)
                .reviewRequired(false)
                .aiStatus("SKIPPED")
                .source(source)
                .build();
    }

    public static ExpectedOutcome error(int httpStatus, int errorCode, String source) {
        return ExpectedOutcome.builder()
                .httpStatus(httpStatus)
                .errorCode(errorCode)
                .source(source)
                .build();
    }
}
