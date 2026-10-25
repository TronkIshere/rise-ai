package com.riseai.service;

import com.riseai.dto.response.SettlementResponse;
import com.riseai.enums.SettlementStatus;
import com.riseai.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SettlementService {
    SettlementResponse propose(AuthUser actor, UUID campaignId, String reason);

    SettlementResponse approve(AuthUser actor, UUID id, String reason);

    SettlementResponse reject(AuthUser actor, UUID id, String reason);

    SettlementResponse cancel(AuthUser actor, UUID id, String reason);

    SettlementResponse get(UUID id);

    Page<SettlementResponse> byCampaign(UUID campaignId, Pageable pageable);

    Page<SettlementResponse> list(SettlementStatus status, Pageable pageable);
}
