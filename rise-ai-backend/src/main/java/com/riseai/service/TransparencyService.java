package com.riseai.service;

import com.riseai.dto.response.CampaignChainResponse;
import com.riseai.dto.response.ChainConfigResponse;
import com.riseai.dto.response.ReservesResponse;
import com.riseai.dto.response.TransparencyReport;
import com.riseai.security.AuthUser;

import java.util.UUID;

public interface TransparencyService {
    TransparencyReport anchors();

    ChainConfigResponse chainConfig();

    CampaignChainResponse campaignChain(AuthUser actor, UUID campaignId);

    ReservesResponse reserves();
}
