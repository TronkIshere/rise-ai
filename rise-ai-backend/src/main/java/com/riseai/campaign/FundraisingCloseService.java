package com.riseai.campaign;

import com.riseai.audit.AuditService;
import com.riseai.donation.DonationService;
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
public class FundraisingCloseService {
    private static final Set<CampaignStatus> FUNDRAISING = EnumSet.of(CampaignStatus.ACTIVE, CampaignStatus.PAUSED);

    private final CampaignRepository campaignRepository;
    private final CampaignService campaignService;
    private final DonationService donationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<UUID> findDue(Instant now) {
        return campaignRepository.findFundraisingDue(FUNDRAISING, now);
    }

    @Transactional
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
