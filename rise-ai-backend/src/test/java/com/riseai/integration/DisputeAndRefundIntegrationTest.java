package com.riseai.integration;

import com.riseai.dto.request.CreateRefundRequest;
import com.riseai.dto.request.OpenDisputeRequest;
import com.riseai.dto.request.ResolveDisputeRequest;
import com.riseai.dto.response.EscrowSummary;
import com.riseai.enums.DisbursementStatus;
import com.riseai.enums.DisputeType;
import com.riseai.enums.DonationStatus;
import com.riseai.enums.MilestoneStatus;
import com.riseai.enums.ResolutionOutcome;
import com.riseai.enums.Role;
import com.riseai.exception.ErrorCode;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DisputeAndRefundIntegrationTest extends AbstractMySqlIntegrationTest {
    private AuthUser admin;
    private AuthUser reviewer;
    private AuthUser creator;
    private AuthUser donor;

    @BeforeEach
    void users() {
        admin = user(Role.ADMIN);
        reviewer = user(Role.ADMIN);
        creator = user(Role.CAMPAIGN_CREATOR);
        donor = user(Role.DONOR);
    }

    private OpenDisputeRequest dispute(UUID milestoneId) {
        return new OpenDisputeRequest(milestoneId, null, DisputeType.MISSING_EVIDENCE, "Concern", "Details", List.of("ref"));
    }

    private record Fixture(UUID campaignId, UUID milestoneId, UUID requestId) {
    }

    private Fixture approvedFixture() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        acceptedEvidence(creator, admin, m1);
        return new Fixture(campaign.id(), m1, approvedRequest(creator, admin, m1, 4_000_000));
    }

    @Test
    void disputeHoldsRequestAndDismissalRestoresIt() {
        Fixture f = approvedFixture();
        UUID disputeId = disputeService.open(donor, f.campaignId(), dispute(f.milestoneId())).id();
        assertThat(disbursementRepository.findById(f.requestId()).orElseThrow().getRequestStatus()).isEqualTo(DisbursementStatus.DISPUTED);
        assertThat(milestoneRepository.findById(f.milestoneId()).orElseThrow().getMilestoneStatus()).isEqualTo(MilestoneStatus.DISPUTED);
        assertError(() -> disbursementService.release(admin, f.requestId()), ErrorCode.INVALID_STATE_TRANSITION);

        disputeService.assign(admin, disputeId, reviewer.id());
        disputeService.resolve(reviewer, disputeId, new ResolveDisputeRequest(ResolutionOutcome.DISMISSED, "Evidence is adequate"));

        assertThat(disbursementRepository.findById(f.requestId()).orElseThrow().getRequestStatus()).isEqualTo(DisbursementStatus.APPROVED);
        assertThat(milestoneRepository.findById(f.milestoneId()).orElseThrow().getMilestoneStatus()).isEqualTo(MilestoneStatus.UNDER_REVIEW);
        disbursementService.release(admin, f.requestId());
        assertThat(escrowQueryService.reconcile(f.campaignId()).consistent()).isTrue();
    }

    @Test
    void upheldDisputeRejectsRequestAndReturnsReservation() {
        Fixture f = approvedFixture();
        UUID disputeId = disputeService.open(donor, f.campaignId(), dispute(f.milestoneId())).id();
        disputeService.assign(admin, disputeId, reviewer.id());
        disputeService.resolve(reviewer, disputeId, new ResolveDisputeRequest(ResolutionOutcome.UPHELD, "Evidence does not support the request"));

        assertThat(disbursementRepository.findById(f.requestId()).orElseThrow().getRequestStatus()).isEqualTo(DisbursementStatus.REJECTED);
        EscrowSummary summary = escrowQueryService.summary(admin, f.campaignId());
        assertThat(summary.totalReserved()).isEqualByComparingTo("0");
        assertThat(summary.availableBalance()).isEqualByComparingTo("10000000");
        assertThat(milestoneRepository.findById(f.milestoneId()).orElseThrow().getMilestoneStatus()).isEqualTo(MilestoneStatus.EVIDENCE_SUBMITTED);
        assertThat(escrowQueryService.reconcile(f.campaignId()).consistent()).isTrue();
    }

    @Test
    void ineligibleUsersCannotOpenDisputes() {
        Fixture f = approvedFixture();
        AuthUser stranger = user(Role.DONOR);
        assertError(() -> disputeService.open(creator, f.campaignId(), dispute(f.milestoneId())), ErrorCode.DISPUTE_NOT_ELIGIBLE);
        assertError(() -> disputeService.open(stranger, f.campaignId(), dispute(f.milestoneId())), ErrorCode.DISPUTE_NOT_ELIGIBLE);
    }

    @Test
    void disputeOpenerCannotReviewOrResolve() {
        Fixture f = approvedFixture();
        UUID disputeId = disputeService.open(admin, f.campaignId(), dispute(f.milestoneId())).id();
        assertError(() -> disputeService.assign(reviewer, disputeId, admin.id()), ErrorCode.CONFLICT_OF_INTEREST);
        disputeService.assign(admin, disputeId, reviewer.id());
        assertError(() -> disputeService.resolve(admin, disputeId, new ResolveDisputeRequest(ResolutionOutcome.DISMISSED, "self")),
                ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
    }

    @Test
    void duplicateOpenDisputeIsRejectedAndWithdrawalReleasesHold() {
        Fixture f = approvedFixture();
        UUID disputeId = disputeService.open(donor, f.campaignId(), dispute(f.milestoneId())).id();
        assertError(() -> disputeService.open(donor, f.campaignId(), dispute(f.milestoneId())), ErrorCode.OPEN_REQUEST_EXISTS);
        disputeService.withdraw(donor, disputeId, "Resolved informally");
        assertThat(disbursementRepository.findById(f.requestId()).orElseThrow().getRequestStatus()).isEqualTo(DisbursementStatus.APPROVED);
    }

    @Test
    void refundAboveRefundableIsRejected() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID donation = confirmedDonation(donor, admin, campaign.id(), 1_000_000);
        assertError(() -> refundService.request(donor, donation, new CreateRefundRequest(vnd(1_000_001), "too much")),
                ErrorCode.REFUND_EXCEEDS_REFUNDABLE);
    }

    @Test
    void recordedRefundUpdatesLedgerDonationAndRejectsDuplicates() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID donation = confirmedDonation(donor, admin, campaign.id(), 3_000_000);
        UUID refund = refundService.request(donor, donation, new CreateRefundRequest(vnd(1_000_000), "Changed mind")).id();
        assertThat(donationRepository.findById(donation).orElseThrow().getDonationStatus()).isEqualTo(DonationStatus.REFUND_PENDING);
        assertError(() -> refundService.request(donor, donation, new CreateRefundRequest(vnd(1), "again")), ErrorCode.INVALID_STATE_TRANSITION);

        refundService.approve(admin, refund, "ok");
        refundService.record(admin, refund);
        assertError(() -> refundService.record(admin, refund), ErrorCode.DUPLICATE_OPERATION);

        assertThat(donationRepository.findById(donation).orElseThrow().getDonationStatus()).isEqualTo(DonationStatus.PARTIALLY_REFUNDED);
        EscrowSummary summary = escrowQueryService.summary(admin, campaign.id());
        assertThat(summary.totalRefunded()).isEqualByComparingTo("1000000");
        assertThat(summary.availableBalance()).isEqualByComparingTo("2000000");
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void refundCannotUseReservedFunds() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID donation = confirmedDonation(donor, admin, campaign.id(), 3_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        requestFunds(creator, m1, 2_500_000);
        UUID refund = refundService.request(donor, donation, new CreateRefundRequest(vnd(1_000_000), "Need it back")).id();
        assertError(() -> refundService.approve(admin, refund, "ok"), ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);
    }

    @Test
    void donorCannotApproveOwnRefund() {
        AuthUser adminDonor = user(Role.ADMIN, Role.DONOR);
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID donation = confirmedDonation(adminDonor, admin, campaign.id(), 1_000_000);
        UUID refund = refundService.request(adminDonor, donation, new CreateRefundRequest(vnd(500_000), "x")).id();
        assertError(() -> refundService.approve(adminDonor, refund, "self"), ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
    }
}
