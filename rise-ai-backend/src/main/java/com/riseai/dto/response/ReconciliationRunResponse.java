package com.riseai.dto.response;

import com.riseai.entity.ReconciliationRun;
import com.riseai.enums.ReconciliationRunStatus;
import com.riseai.enums.ReconciliationTrigger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationRunResponse(UUID id, String runCode, ReconciliationTrigger triggerType, UUID triggeredBy,
                                        ReconciliationRunStatus runStatus, Instant startedAt, Instant finishedAt, int accountsChecked,
                                        int inconsistentAccounts, List<ReconciliationReport> findings, String errorMessage) {
    public static ReconciliationRunResponse from(ReconciliationRun r) {
        return new ReconciliationRunResponse(r.getId(), r.getRunCode(), r.getTriggerType(), r.getTriggeredBy(), r.getRunStatus(),
                r.getStartedAt(), r.getFinishedAt(), r.getAccountsChecked(), r.getInconsistentAccounts(),
                r.getFindings() == null ? List.of() : r.getFindings(), r.getErrorMessage());
    }
}
