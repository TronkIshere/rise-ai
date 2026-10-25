package com.riseai.service;

import com.riseai.dto.response.FindingDetailResponse;
import com.riseai.dto.response.FindingResponse;
import com.riseai.dto.response.OutboxEventResponse;
import com.riseai.dto.response.OutboxStatsResponse;
import com.riseai.dto.response.StatusHistoryResponse;
import com.riseai.enums.FindingReviewStatus;
import com.riseai.enums.FindingSeverity;
import com.riseai.enums.OutboxStatus;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface FindingService {
    Page<FindingResponse> search(FindingReviewStatus status, FindingSeverity severity, String category, UUID campaignId, Pageable pageable);

    FindingDetailResponse get(UUID id);

    List<StatusHistoryResponse> history(UUID id);

    FindingResponse review(AuthUser actor, UUID id, FindingReviewStatus target, String note);

    OutboxStatsResponse outboxStats();

    Page<OutboxEventResponse> outboxEvents(OutboxStatus status, Pageable pageable);

    OutboxEventResponse requeue(AuthUser actor, UUID id, String reason);
}
