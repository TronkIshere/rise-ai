package com.riseai.service;

import com.riseai.dto.request.OrganizationRequest;
import com.riseai.dto.response.OrganizationResponse;
import com.riseai.enums.VerificationStatus;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrganizationService {
    OrganizationResponse create(AuthUser actor, OrganizationRequest request);

    OrganizationResponse update(AuthUser actor, UUID id, OrganizationRequest request);

    void delete(AuthUser actor, UUID id);

    OrganizationResponse get(UUID id);

    Page<OrganizationResponse> search(String q, VerificationStatus status, Pageable pageable);

    Page<OrganizationResponse> mine(AuthUser actor, Pageable pageable);

    OrganizationResponse submitForVerification(AuthUser actor, UUID id);

    OrganizationResponse verify(AuthUser actor, UUID id, String notes);

    OrganizationResponse rejectVerification(AuthUser actor, UUID id, String notes);
}
