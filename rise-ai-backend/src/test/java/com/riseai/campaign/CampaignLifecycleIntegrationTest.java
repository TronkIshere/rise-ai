package com.riseai.campaign;

import com.riseai.audit.AuditDtos.StatusHistoryResponse;
import com.riseai.common.exception.ErrorCode;
import com.riseai.escrow.EscrowStatus;
import com.riseai.evidence.EvidenceDtos.EvidenceRequest;
import com.riseai.evidence.EvidenceType;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignLifecycleIntegrationTest extends AbstractMySqlIntegrationTest {
    private AuthUser admin;
    private AuthUser creator;
    private AuthUser donor;

    @BeforeEach
    void users() {
        admin = user(Role.ADMIN);
        creator = user(Role.CAMPAIGN_CREATOR);
        donor = user(Role.DONOR);
    }

    @Test
    void newCampaignIsDraftAndNeedsMilestonesBeforeSubmission() {
        UUID org = verifiedOrganization(creator, admin);
        UUID id = campaignService.create(creator, campaignRequest(org, 10_000_000)).id();
        assertThat(campaignService.get(creator, id).campaignStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertError(() -> campaignService.submit(creator, id), ErrorCode.BUDGET_MISMATCH);
    }

    @Test
    void milestoneBudgetsMustMatchTarget() {
        UUID org = verifiedOrganization(creator, admin);
        UUID id = campaignService.create(creator, campaignRequest(org, 10_000_000)).id();
        milestoneService.create(creator, id, milestoneRequest(6_000_000));
        assertError(() -> milestoneService.create(creator, id, milestoneRequest(5_000_000)), ErrorCode.BUDGET_MISMATCH);
        assertError(() -> campaignService.submit(creator, id), ErrorCode.BUDGET_MISMATCH);
        milestoneService.create(creator, id, milestoneRequest(4_000_000));
        assertThat(campaignService.submit(creator, id).campaignStatus()).isEqualTo(CampaignStatus.PENDING_REVIEW);
    }

    @Test
    void approvalRequiresVerifiedOrganizationAndAdmin() {
        ActiveCampaign draft = draftCampaign(creator, unverifiedOrganization(creator), 1_000_000);
        campaignService.submit(creator, draft.id());
        assertError(() -> campaignService.approve(creator, draft.id(), "self"), ErrorCode.ACCESS_DENIED);
        assertError(() -> campaignService.approve(admin, draft.id(), "ok"), ErrorCode.ORGANIZATION_NOT_VERIFIED);
    }

    @Test
    void invalidTransitionsAreRejected() {
        ActiveCampaign draft = draftCampaign(creator, verifiedOrganization(creator, admin), 1_000_000);
        assertError(() -> campaignService.publish(creator, draft.id()), ErrorCode.INVALID_STATE_TRANSITION);
        assertError(() -> campaignService.approve(admin, draft.id(), "ok"), ErrorCode.INVALID_STATE_TRANSITION);
        assertError(() -> campaignService.pause(admin, draft.id(), "x"), ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    void editingIsBlockedAfterSubmission() {
        UUID org = verifiedOrganization(creator, admin);
        ActiveCampaign draft = draftCampaign(creator, org, 1_000_000);
        campaignService.submit(creator, draft.id());
        assertError(() -> campaignService.update(creator, draft.id(), campaignRequest(org, 1_000_000)), ErrorCode.INVALID_STATE_TRANSITION);
        assertError(() -> milestoneService.create(creator, draft.id(), milestoneRequest(1)), ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    void donationsOnlyAcceptedWhileActive() {
        ActiveCampaign draft = draftCampaign(creator, verifiedOrganization(creator, admin), 1_000_000);
        campaignService.submit(creator, draft.id());
        campaignService.approve(admin, draft.id(), "ok");
        assertError(() -> pendingDonation(donor, draft.id(), 100_000), ErrorCode.RESOURCE_NOT_FOUND);
        campaignService.publish(creator, draft.id());
        pendingDonation(donor, draft.id(), 100_000);
        campaignService.pause(admin, draft.id(), "Inspection");
        assertError(() -> pendingDonation(donor, draft.id(), 100_000), ErrorCode.CAMPAIGN_NOT_ACCEPTING_DONATIONS);
        assertThat(escrowQueryService.summary(admin, draft.id()).escrowStatus()).isEqualTo(EscrowStatus.FROZEN);
    }

    @Test
    void creatorCannotDonateToOwnCampaign() {
        AuthUser creatorDonor = user(Role.CAMPAIGN_CREATOR, Role.DONOR);
        ActiveCampaign campaign = activeCampaign(creatorDonor, admin, 1_000_000);
        assertError(() -> pendingDonation(creatorDonor, campaign.id(), 100_000), ErrorCode.CONFLICT_OF_INTEREST);
    }

    @Test
    void evidenceCanOnlyBeSubmittedByCampaignOwner() {
        AuthUser other = user(Role.CAMPAIGN_CREATOR);
        ActiveCampaign campaign = activeCampaign(creator, admin, 1_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        EvidenceRequest request = new EvidenceRequest(EvidenceType.RECEIPT, "Receipt", null, "sim://x.pdf", null, null);
        assertError(() -> evidenceService.submit(other, m1, null, request), ErrorCode.ACCESS_DENIED);
    }

    @Test
    void milestonesAreStartedSequentially() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 1_000_000, 1_000_000);
        assertError(() -> milestoneService.start(creator, campaign.milestones().get(1)), ErrorCode.INVALID_STATE_TRANSITION);
        milestoneService.start(creator, campaign.milestones().get(0));
    }

    @Test
    void statusHistoryRecordsTheWorkflow() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 1_000_000);
        assertThat(campaignService.history(admin, campaign.id())).extracting(StatusHistoryResponse::toStatus)
                .containsExactly("DRAFT", "PENDING_REVIEW", "APPROVED", "ACTIVE");
    }

    @Test
    void campaignWithReservedFundsCannotBeCompleted() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 1_000_000);
        confirmedDonation(donor, admin, campaign.id(), 1_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        requestFunds(creator, m1, 500_000);
        assertError(() -> campaignService.complete(admin, campaign.id(), "done"), ErrorCode.OPEN_REQUEST_EXISTS);
    }
}
