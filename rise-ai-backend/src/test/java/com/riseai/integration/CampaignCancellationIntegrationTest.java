package com.riseai.integration;

import com.riseai.enums.CampaignStatus;
import com.riseai.enums.EscrowStatus;
import com.riseai.enums.MilestoneStatus;
import com.riseai.enums.Role;
import com.riseai.exception.ErrorCode;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignCancellationIntegrationTest extends AbstractMySqlIntegrationTest {
    AuthUser admin;
    AuthUser creator;
    AuthUser otherCreator;
    AuthUser donor;

    @BeforeEach
    void setUp() {
        admin = user(Role.ADMIN);
        creator = user(Role.CAMPAIGN_CREATOR);
        otherCreator = user(Role.CAMPAIGN_CREATOR);
        donor = user(Role.DONOR);
    }

    @Test
    void ownerCanCancelBeforePublicationButNotAfter() {
        UUID draft = draftCampaign(creator, verifiedOrganization(creator, admin), 1_000_000).id();
        assertError(() -> campaignService.cancel(otherCreator, draft, "not mine"), ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(campaignService.cancel(creator, draft, "Changed plans").campaignStatus()).isEqualTo(CampaignStatus.CANCELLED);
        assertError(() -> campaignService.cancel(creator, draft, "again"), ErrorCode.INVALID_STATE_TRANSITION);

        ActiveCampaign active = activeCampaign(creator, admin, 1_000_000);
        assertError(() -> campaignService.cancel(creator, active.id(), "after publish"), ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    void adminCancellationOfPublishedCampaignCancelsOpenMilestonesAndMovesEscrowToSettlement() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 1_000_000, 2_000_000);
        confirmedDonation(donor, admin, campaign.id(), 500_000);

        assertThat(campaignService.cancel(admin, campaign.id(), "Organizer withdrew").campaignStatus()).isEqualTo(CampaignStatus.CANCELLED);

        assertThat(campaign.milestones()).allSatisfy(id ->
                assertThat(milestoneRepository.findById(id).orElseThrow().getMilestoneStatus()).isEqualTo(MilestoneStatus.CANCELLED));
        assertThat(escrowAccountRepository.findByCampaignIdAndCurrency(campaign.id(), "VND").orElseThrow().getEscrowStatus())
                .isEqualTo(EscrowStatus.SETTLEMENT);
        assertThat(campaignService.history(admin, campaign.id())).last()
                .satisfies(h -> assertThat(h.toStatus()).isEqualTo(CampaignStatus.CANCELLED.name()));
        assertError(() -> confirmedDonation(donor, admin, campaign.id(), 500_000), ErrorCode.CAMPAIGN_NOT_ACCEPTING_DONATIONS);
    }

    @Test
    void cancellationIsBlockedWhileFundsAreReservedAndAfterCompletion() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 1_000_000);
        confirmedDonation(donor, admin, campaign.id(), 1_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        UUID request = requestFunds(creator, m1, 500_000);
        assertError(() -> campaignService.cancel(admin, campaign.id(), "stop"), ErrorCode.OPEN_REQUEST_EXISTS);

        disbursementService.cancel(creator, request, "Not needed");
        campaignService.complete(admin, campaign.id(), "Done");
        assertError(() -> campaignService.cancel(admin, campaign.id(), "late"), ErrorCode.INVALID_STATE_TRANSITION);
    }
}
