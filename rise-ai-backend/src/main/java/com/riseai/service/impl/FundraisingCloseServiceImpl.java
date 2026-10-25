package com.riseai.service.impl;

import com.riseai.entity.Campaign;
import com.riseai.enums.CampaignStatus;
import com.riseai.repository.CampaignRepository;
import com.riseai.service.AuditService;
import com.riseai.service.CampaignService;
import com.riseai.service.DonationService;
import com.riseai.service.FundraisingCloseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FundraisingCloseServiceImpl implements FundraisingCloseService {
    private static final Set<CampaignStatus> FUNDRAISING = EnumSet.of(CampaignStatus.ACTIVE, CampaignStatus.PAUSED);

    private final CampaignRepository campaignRepository;
    private final CampaignService campaignService;
    private final DonationService donationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @Override
    public List<UUID> findDue(Instant now) {
        return campaignRepository.findFundraisingDue(FUNDRAISING, now);
    }

    @Transactional
    @Override
    public boolean close(UUID campaignId, Instant now) {
        Campaign campaign = campaignService.lock(campaignId);
        if (!FUNDRAISING.contains(campaign.getCampaignStatus()) || campaign.getFundraisingClosedAt() != null || campaign.getEndAt().isAfter(now)) {
            return false;
        }
        campaign.setFundraisingClosedAt(now);
        int failed = donationService.failPendingAfterFundraisingClose(campaignId);
        auditService.record(null, "CAMPAIGN_FUNDRAISING_CLOSED", "CAMPAIGN", campaignId, null, null,
                AuditService.meta("endAt", campaign.getEndAt(), "failedPendingDonations", failed));
        return true;
    }
}
