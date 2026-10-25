package com.riseai.service;

import com.riseai.dto.response.ReconciliationRunResponse;
import com.riseai.enums.ReconciliationRunStatus;
import com.riseai.enums.ReconciliationTrigger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReconciliationService {
    ReconciliationRunResponse run(ReconciliationTrigger trigger, UUID actorId);

    Page<ReconciliationRunResponse> history(ReconciliationRunStatus status, Pageable pageable);

    ReconciliationRunResponse get(UUID id);
}
