package com.riseai.disbursement;

import com.riseai.common.exception.ErrorCode;
import com.riseai.disbursement.DisbursementDtos.CreateDisbursementRequest;
import com.riseai.refund.RefundDtos.CreateRefundRequest;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdempotencyIntegrationTest extends AbstractMySqlIntegrationTest {
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
    void disbursementReplayReturnsTheOriginalRequestAndReservesOnce() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 5_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        CreateDisbursementRequest body = new CreateDisbursementRequest(vnd(1_000_000), "Supplier");

        UUID first = disbursementService.create(creator, m1, body, "dsb-key-1").id();
        UUID replay = disbursementService.create(creator, m1, body, "dsb-key-1").id();

        assertThat(replay).isEqualTo(first);
        assertThat(escrowQueryService.summary(admin, campaign.id()).totalReserved()).isEqualByComparingTo("1000000");
        assertError(() -> disbursementService.create(creator, m1, new CreateDisbursementRequest(vnd(2_000_000), "Other"), "dsb-key-1"),
                ErrorCode.DUPLICATE_OPERATION);
        assertError(() -> disbursementService.create(creator, m1, body, " "), ErrorCode.INVALID_REQUEST);
    }

    @Test
    void refundReplayReturnsTheOriginalRequest() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID donation = confirmedDonation(donor, admin, campaign.id(), 3_000_000);
        CreateRefundRequest body = new CreateRefundRequest(vnd(1_000_000), "Mistake");

        UUID first = refundService.request(donor, donation, body, "rfd-key-1").id();
        UUID replay = refundService.request(donor, donation, body, "rfd-key-1").id();

        assertThat(replay).isEqualTo(first);
        assertError(() -> refundService.request(donor, donation, new CreateRefundRequest(vnd(500_000), "Mistake"), "rfd-key-1"),
                ErrorCode.DUPLICATE_OPERATION);
    }

    @Test
    void sameKeyFromDifferentUsersIsIndependent() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        AuthUser otherDonor = user(Role.DONOR);
        UUID d1 = confirmedDonation(donor, admin, campaign.id(), 1_000_000);
        UUID d2 = confirmedDonation(otherDonor, admin, campaign.id(), 1_000_000);

        UUID r1 = refundService.request(donor, d1, new CreateRefundRequest(vnd(100_000), "x"), "shared-key").id();
        UUID r2 = refundService.request(otherDonor, d2, new CreateRefundRequest(vnd(100_000), "x"), "shared-key").id();

        assertThat(r1).isNotEqualTo(r2);
    }

    @Test
    void reusingAKeyOnSomeoneElsesResourceDoesNotRevealIt() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        AuthUser otherDonor = user(Role.DONOR);
        UUID own = confirmedDonation(otherDonor, admin, campaign.id(), 1_000_000);
        UUID foreign = confirmedDonation(donor, admin, campaign.id(), 1_000_000);
        CreateRefundRequest body = new CreateRefundRequest(vnd(100_000), "x");
        refundService.request(otherDonor, own, body, "probe-key");

        assertError(() -> refundService.request(otherDonor, foreign, body, "probe-key"), ErrorCode.RESOURCE_NOT_FOUND);

        AuthUser otherCreator = user(Role.CAMPAIGN_CREATOR);
        UUID m1 = campaign.milestones().get(0);
        assertError(() -> disbursementService.create(otherCreator, m1, new CreateDisbursementRequest(vnd(1), "x"), "probe-key"),
                ErrorCode.ACCESS_DENIED);
    }
}
