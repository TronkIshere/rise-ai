package com.riseai.service;

import com.riseai.dto.request.CreateAdjustmentRequest;
import com.riseai.dto.response.AdjustmentResponse;
import com.riseai.enums.AdjustmentStatus;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdjustmentService {
    AdjustmentResponse request(AuthUser actor, UUID campaignId, CreateAdjustmentRequest request);

    AdjustmentResponse approve(AuthUser actor, UUID id, String reason);

    AdjustmentResponse reject(AuthUser actor, UUID id, String reason);

    AdjustmentResponse cancel(AuthUser actor, UUID id, String reason);

    AdjustmentResponse get(UUID id);

    Page<AdjustmentResponse> byCampaign(UUID campaignId, Pageable pageable);

    Page<AdjustmentResponse> list(AdjustmentStatus status, Pageable pageable);
}
