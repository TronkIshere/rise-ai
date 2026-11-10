package com.riseai.service;

import com.riseai.dto.request.CreateDisbursementRequest;
import com.riseai.dto.request.EvidenceRequest;
import com.riseai.dto.response.DisbursementResponse;
import com.riseai.dto.response.EvidenceResponse;
import com.riseai.dto.response.StatusHistoryResponse;
import com.riseai.enums.DisbursementStatus;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DisbursementService {
    DisbursementResponse create(AuthUser actor, UUID milestoneId, CreateDisbursementRequest request);

    DisbursementResponse create(AuthUser actor, UUID milestoneId, CreateDisbursementRequest request, String idempotencyKey);

    DisbursementResponse startReview(AuthUser actor, UUID id);

    DisbursementResponse requestMoreEvidence(AuthUser actor, UUID id, String reason);

    DisbursementResponse resubmit(AuthUser actor, UUID id, String note);

    DisbursementResponse approve(AuthUser actor, UUID id, String reason);

    DisbursementResponse reject(AuthUser actor, UUID id, String reason);

    DisbursementResponse cancel(AuthUser actor, UUID id, String reason);

    DisbursementResponse release(AuthUser actor, UUID id);

    /** Records a release the chain has already executed. Called only by the chain command relay. */
    void completeChainRelease(UUID id, UUID actorId);

    EvidenceResponse addEvidence(AuthUser actor, UUID id, EvidenceRequest request);

    DisbursementResponse get(AuthUser actor, UUID id);

    Page<DisbursementResponse> listByCampaign(AuthUser actor, UUID campaignId, Pageable pageable);

    Page<DisbursementResponse> adminQueue(DisbursementStatus status, Pageable pageable);

    List<StatusHistoryResponse> history(AuthUser actor, UUID id);

    void holdForDispute(UUID milestoneId, UUID actorId, String reason);

    void rejectDisputed(UUID milestoneId, UUID onlyRequestId, UUID actorId, String reason);

    void restoreDisputed(UUID milestoneId, UUID actorId, String reason);
}
