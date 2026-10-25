package com.riseai.service;

import com.riseai.dto.request.OpenDisputeRequest;
import com.riseai.dto.request.ResolveDisputeRequest;
import com.riseai.dto.response.DisputeMessageResponse;
import com.riseai.dto.response.DisputeResponse;
import com.riseai.dto.response.StatusHistoryResponse;
import com.riseai.enums.DisputeStatus;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DisputeService {
    DisputeResponse open(AuthUser actor, UUID campaignId, OpenDisputeRequest request);

    DisputeResponse assign(AuthUser actor, UUID id, UUID reviewerId);

    DisputeResponse resolve(AuthUser actor, UUID id, ResolveDisputeRequest request);

    DisputeResponse withdraw(AuthUser actor, UUID id, String reason);

    DisputeMessageResponse addMessage(AuthUser actor, UUID id, String text);

    Page<DisputeMessageResponse> messages(AuthUser actor, UUID id, Pageable pageable);

    DisputeResponse get(AuthUser actor, UUID id);

    List<StatusHistoryResponse> history(AuthUser actor, UUID id);

    Page<DisputeResponse> mine(AuthUser actor, Pageable pageable);

    Page<DisputeResponse> byCampaign(AuthUser actor, UUID campaignId, Pageable pageable);

    Page<DisputeResponse> adminQueue(DisputeStatus status, Pageable pageable);
}
