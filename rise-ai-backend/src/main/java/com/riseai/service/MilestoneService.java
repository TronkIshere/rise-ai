package com.riseai.service;

import com.riseai.dto.request.MilestoneRequest;
import com.riseai.dto.response.MilestoneResponse;
import com.riseai.dto.response.StatusHistoryResponse;
import com.riseai.entity.Milestone;
import com.riseai.enums.MilestoneStatus;
import com.riseai.security.AuthUser;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MilestoneService {
    Set<MilestoneStatus> ACTIVE_WORK = EnumSet.of(MilestoneStatus.READY, MilestoneStatus.IN_PROGRESS,
            MilestoneStatus.EVIDENCE_SUBMITTED, MilestoneStatus.UNDER_REVIEW, MilestoneStatus.PARTIALLY_DISBURSED,
            MilestoneStatus.DISBURSED, MilestoneStatus.DISPUTED);

    MilestoneResponse create(AuthUser actor, UUID campaignId, MilestoneRequest request);

    MilestoneResponse update(AuthUser actor, UUID id, MilestoneRequest request);

    void delete(AuthUser actor, UUID id);

    MilestoneResponse get(AuthUser actor, UUID id);

    List<MilestoneResponse> list(AuthUser actor, UUID campaignId);

    List<StatusHistoryResponse> history(AuthUser actor, UUID id);

    MilestoneResponse start(AuthUser actor, UUID id);

    MilestoneResponse complete(AuthUser actor, UUID id, String reason);

    MilestoneResponse cancel(AuthUser actor, UUID id, String reason);

    void refreshStatus(Milestone milestone, UUID actorId, String reason);

    void markDisputed(UUID milestoneId, UUID actorId, String reason);

    void restoreAfterDispute(UUID milestoneId, UUID actorId, String reason);
}
