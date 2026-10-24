package com.riseai.adjustment;

import com.riseai.adjustment.AdjustmentDtos.AdjustmentResponse;
import com.riseai.adjustment.AdjustmentDtos.CreateAdjustmentRequest;
import com.riseai.common.exception.ErrorCode;
import com.riseai.escrow.EscrowDtos.EscrowSummary;
import com.riseai.escrow.LedgerDirection;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdjustmentIntegrationTest extends AbstractMySqlIntegrationTest {
    AuthUser admin;
    AuthUser secondAdmin;
    AuthUser creator;
    AuthUser donor;

    @BeforeEach
    void setUp() {
        admin = user(Role.ADMIN);
        secondAdmin = user(Role.ADMIN);
        creator = user(Role.CAMPAIGN_CREATOR);
        donor = user(Role.DONOR);
    }

    @Test
    void adjustmentIsAppliedOnlyAfterASecondAdminApproves() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 2_000_000);

        AdjustmentResponse credit = adjustmentService.request(admin, campaign.id(),
                new CreateAdjustmentRequest(LedgerDirection.CREDIT, vnd(150_000), "Bank interest credited to escrow"));
        assertThat(escrowQueryService.summary(admin, campaign.id()).availableBalance()).isEqualByComparingTo("2000000");
        assertError(() -> adjustmentService.approve(admin, credit.id(), "self"), ErrorCode.SELF_APPROVAL_NOT_ALLOWED);

        assertThat(adjustmentService.approve(secondAdmin, credit.id(), "Verified statement").adjustmentStatus()).isEqualTo(AdjustmentStatus.APPLIED);
        assertError(() -> adjustmentService.approve(secondAdmin, credit.id(), "twice"), ErrorCode.INVALID_STATE_TRANSITION);

        UUID debit = adjustmentService.request(secondAdmin, campaign.id(),
                new CreateAdjustmentRequest(LedgerDirection.DEBIT, vnd(50_000), "Bank fee")).id();
        adjustmentService.approve(admin, debit, "ok");

        EscrowSummary summary = escrowQueryService.summary(admin, campaign.id());
        assertThat(summary.totalAdjustmentCredits()).isEqualByComparingTo("150000");
        assertThat(summary.totalAdjustmentDebits()).isEqualByComparingTo("50000");
        assertThat(summary.availableBalance()).isEqualByComparingTo("2100000");
        assertThat(summary.heldBalance()).isEqualByComparingTo("2100000");
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void debitCannotExceedAvailableBalanceAndRejectedRequestsChangeNothing() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 1_000_000);

        UUID tooLarge = adjustmentService.request(admin, campaign.id(),
                new CreateAdjustmentRequest(LedgerDirection.DEBIT, vnd(1_000_001), "Wrong figure")).id();
        assertError(() -> adjustmentService.approve(secondAdmin, tooLarge, "ok"), ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);
        assertThat(adjustmentService.reject(secondAdmin, tooLarge, "Wrong figure").adjustmentStatus()).isEqualTo(AdjustmentStatus.REJECTED);

        UUID cancelled = adjustmentService.request(admin, campaign.id(),
                new CreateAdjustmentRequest(LedgerDirection.CREDIT, vnd(10), "Typo")).id();
        assertError(() -> adjustmentService.cancel(secondAdmin, cancelled, "not mine"), ErrorCode.ACCESS_DENIED);
        assertThat(adjustmentService.cancel(admin, cancelled, "Typo").adjustmentStatus()).isEqualTo(AdjustmentStatus.CANCELLED);

        assertThat(escrowQueryService.summary(admin, campaign.id()).availableBalance()).isEqualByComparingTo("1000000");
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void invalidRequestsAreRejected() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        assertError(() -> adjustmentService.request(admin, campaign.id(), new CreateAdjustmentRequest(LedgerDirection.NEUTRAL, vnd(1), "x")),
                ErrorCode.INVALID_REQUEST);
        assertError(() -> adjustmentService.request(creator, campaign.id(), new CreateAdjustmentRequest(LedgerDirection.CREDIT, vnd(1), "x")),
                ErrorCode.ACCESS_DENIED);
        AuthUser adminCreator = user(Role.ADMIN, Role.CAMPAIGN_CREATOR);
        ActiveCampaign own = activeCampaign(adminCreator, admin, 10_000_000);
        assertError(() -> adjustmentService.request(adminCreator, own.id(), new CreateAdjustmentRequest(LedgerDirection.CREDIT, vnd(1), "x")),
                ErrorCode.CONFLICT_OF_INTEREST);
        UUID pending = adjustmentService.request(admin, own.id(), new CreateAdjustmentRequest(LedgerDirection.CREDIT, vnd(1), "x")).id();
        assertError(() -> adjustmentService.approve(adminCreator, pending, "own campaign"), ErrorCode.CONFLICT_OF_INTEREST);
        UUID draft = draftCampaign(creator, verifiedOrganization(creator, admin), 1_000_000).id();
        assertError(() -> adjustmentService.request(admin, draft, new CreateAdjustmentRequest(LedgerDirection.CREDIT, vnd(1), "x")),
                ErrorCode.ESCROW_NOT_ACTIVE);
    }
}
