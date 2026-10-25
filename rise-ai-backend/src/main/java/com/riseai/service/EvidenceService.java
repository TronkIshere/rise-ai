package com.riseai.service;

import com.riseai.dto.request.EvidenceRequest;
import com.riseai.dto.response.EvidenceResponse;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface EvidenceService {
    EvidenceResponse submit(AuthUser actor, UUID milestoneId, UUID disbursementRequestId, EvidenceRequest request);

    EvidenceResponse review(AuthUser actor, UUID id, boolean accept, String note);

    EvidenceResponse get(AuthUser actor, UUID id);

    Page<EvidenceResponse> listByMilestone(AuthUser actor, UUID milestoneId, Pageable pageable);

    Page<EvidenceResponse> listByRequest(AuthUser actor, UUID requestId, Pageable pageable);
}
