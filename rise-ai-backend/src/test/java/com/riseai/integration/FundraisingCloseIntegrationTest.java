package com.riseai.integration;

import com.riseai.enums.CampaignStatus;
import com.riseai.enums.DonationStatus;
import com.riseai.enums.Role;
import com.riseai.exception.ErrorCode;
import com.riseai.security.AuthUser;
import com.riseai.service.FundraisingCloseService;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FundraisingCloseIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    FundraisingCloseService fundraisingCloseService;

    AuthUser admin;
    AuthUser creator;
    AuthUser donor;

    @BeforeEach
    void setUp() {
        admin = user(Role.ADMIN);
        creator = user(Role.CAMPAIGN_CREATOR);
        donor = user(Role.DONOR);
    }

    @Test
    void endedCampaignClosesFundraisingAndFailsPendingDonations() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID confirmed = confirmedDonation(donor, admin, campaign.id(), 1_000_000);
        UUID pending = pendingDonation(donor, campaign.id(), 500_000);
        assertThat(campaignService.get(admin, campaign.id()).acceptingDonations()).isTrue();

        moveEndDateToPast(campaign.id());

        assertThat(campaignService.get(admin, campaign.id()).acceptingDonations()).isFalse();
        assertError(() -> pendingDonation(donor, campaign.id(), 500_000), ErrorCode.CAMPAIGN_NOT_ACCEPTING_DONATIONS);
        assertError(() -> donationService.confirm(admin, pending, null), ErrorCode.CAMPAIGN_NOT_ACCEPTING_DONATIONS);

        Instant now = Instant.now();
        assertThat(fundraisingCloseService.findDue(now)).contains(campaign.id());
        assertThat(fundraisingCloseService.close(campaign.id(), now)).isTrue();

        assertThat(fundraisingCloseService.findDue(Instant.now())).doesNotContain(campaign.id());
        assertThat(fundraisingCloseService.close(campaign.id(), Instant.now())).isFalse();
        assertThat(campaignService.get(admin, campaign.id()).fundraisingClosedAt()).isNotNull();
        assertThat(campaignService.get(admin, campaign.id()).campaignStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(donationRepository.findById(pending).orElseThrow().getDonationStatus()).isEqualTo(DonationStatus.FAILED);
        assertThat(donationRepository.findById(confirmed).orElseThrow().getDonationStatus()).isEqualTo(DonationStatus.CONFIRMED);
        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("CAMPAIGN_FUNDRAISING_CLOSED");
            assertThat(log.getEntityId()).isEqualTo(campaign.id());
        });
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void campaignWithFutureEndDateIsNotClosed() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        assertThat(fundraisingCloseService.findDue(Instant.now())).doesNotContain(campaign.id());
        assertThat(fundraisingCloseService.close(campaign.id(), Instant.now())).isFalse();
        assertThat(campaignService.get(admin, campaign.id()).fundraisingClosedAt()).isNull();
    }

    @Test
    void campaignPastItsEndDateCannotBeApproved() {
        ActiveCampaign draft = draftCampaign(creator, verifiedOrganization(creator, admin), 1_000_000);
        campaignService.submit(creator, draft.id());
        moveEndDateToPast(draft.id());
        assertError(() -> campaignService.approve(admin, draft.id(), "late"), ErrorCode.INVALID_STATE_TRANSITION);
    }
}
