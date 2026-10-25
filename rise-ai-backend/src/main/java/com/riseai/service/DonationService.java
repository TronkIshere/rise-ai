package com.riseai.service;

import com.riseai.dto.request.CreateDonationRequest;
import com.riseai.dto.response.DonationResponse;
import com.riseai.enums.DonationStatus;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DonationService {
    DonationResponse create(AuthUser actor, UUID campaignId, CreateDonationRequest request, String idempotencyKey);

    DonationResponse confirm(AuthUser actor, UUID id, String simulatedReference);

    DonationResponse fail(AuthUser actor, UUID id, String reason);

    DonationResponse cancel(AuthUser actor, UUID id);

    int failPendingAfterFundraisingClose(UUID campaignId);

    DonationResponse get(AuthUser actor, UUID id);

    Page<DonationResponse> mine(AuthUser actor, Pageable pageable);

    Page<DonationResponse> publicList(AuthUser actor, UUID campaignId, Pageable pageable);

    Page<DonationResponse> manageList(AuthUser actor, UUID campaignId, DonationStatus status, Pageable pageable);

    Page<DonationResponse> adminList(DonationStatus status, Pageable pageable);
}
