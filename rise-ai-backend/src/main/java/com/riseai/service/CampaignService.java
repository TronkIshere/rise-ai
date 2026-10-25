package com.riseai.service;

import com.riseai.dto.request.CampaignRequest;
import com.riseai.dto.response.CampaignResponse;
import com.riseai.dto.response.CampaignStatistics;
import com.riseai.dto.response.StatusHistoryResponse;
import com.riseai.entity.Campaign;
import com.riseai.enums.CampaignCategory;
import com.riseai.enums.CampaignStatus;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CampaignService {
    Set<CampaignStatus> PUBLIC_STATUSES = EnumSet.of(CampaignStatus.ACTIVE, CampaignStatus.PAUSED, CampaignStatus.COMPLETED);

    CampaignResponse create(AuthUser actor, CampaignRequest request);

    CampaignResponse update(AuthUser actor, UUID id, CampaignRequest request);

    CampaignResponse submit(AuthUser actor, UUID id);

    CampaignResponse approve(AuthUser actor, UUID id, String reason);

    CampaignResponse reject(AuthUser actor, UUID id, String reason);

    CampaignResponse publish(AuthUser actor, UUID id);

    CampaignResponse pause(AuthUser actor, UUID id, String reason);

    CampaignResponse resume(AuthUser actor, UUID id, String reason);

    CampaignResponse complete(AuthUser actor, UUID id, String reason);

    CampaignResponse cancel(AuthUser actor, UUID id, String reason);

    CampaignResponse get(AuthUser actor, UUID id);

    CampaignResponse getBySlug(AuthUser actor, String slug);

    Page<CampaignResponse> search(AuthUser actor, CampaignStatus status, CampaignCategory category, UUID organizationId, String q, Pageable pageable);

    Page<CampaignResponse> mine(AuthUser actor, Pageable pageable);

    CampaignStatistics statistics(AuthUser actor, UUID id);

    List<StatusHistoryResponse> history(AuthUser actor, UUID id);

    Campaign load(UUID id);

    Campaign lock(UUID id);

    Campaign loadVisible(UUID id, AuthUser actor);

    Campaign loadOwned(AuthUser actor, UUID id);

    Campaign loadOwnerOrAdmin(AuthUser actor, UUID id);

    boolean isVisible(Campaign campaign, AuthUser actor);

    boolean isOwnerOrAdmin(Campaign campaign, AuthUser actor);
}
