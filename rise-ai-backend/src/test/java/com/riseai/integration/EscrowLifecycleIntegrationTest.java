package com.riseai.integration;

import com.riseai.dto.request.CreateDonationRequest;
import com.riseai.dto.response.EscrowSummary;
import com.riseai.dto.response.ReconciliationReport;
import com.riseai.entity.AuditLog;
import com.riseai.enums.DisbursementStatus;
import com.riseai.enums.MilestoneStatus;
import com.riseai.enums.Role;
import com.riseai.exception.ErrorCode;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EscrowLifecycleIntegrationTest extends AbstractMySqlIntegrationTest {
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
    void fullLifecycleKeepsLedgerAndAggregatesConsistent() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 40_000_000, 60_000_000);
        confirmedDonation(donor, admin, campaign.id(), 50_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        acceptedEvidence(creator, admin, m1);

        UUID request = requestFunds(creator, m1, 30_000_000);
        EscrowSummary reserved = escrowQueryService.summary(admin, campaign.id());
        assertThat(reserved.totalReserved()).isEqualByComparingTo("30000000");
        assertThat(reserved.availableBalance()).isEqualByComparingTo("20000000");
        assertThat(milestoneRepository.findById(m1).orElseThrow().getMilestoneStatus()).isEqualTo(MilestoneStatus.UNDER_REVIEW);

        disbursementService.startReview(admin, request);
        disbursementService.approve(admin, request, "ok");
        disbursementService.release(admin, request);

        EscrowSummary released = escrowQueryService.summary(admin, campaign.id());
        assertThat(released.totalDisbursed()).isEqualByComparingTo("30000000");
        assertThat(released.totalReserved()).isEqualByComparingTo("0");
        assertThat(released.availableBalance()).isEqualByComparingTo("20000000");
        assertThat(released.heldBalance()).isEqualByComparingTo("20000000");
        assertThat(milestoneRepository.findById(m1).orElseThrow().getMilestoneStatus()).isEqualTo(MilestoneStatus.PARTIALLY_DISBURSED);
        assertThat(disbursementRepository.findById(request).orElseThrow().getRequestStatus()).isEqualTo(DisbursementStatus.RELEASED);

        ReconciliationReport report = escrowQueryService.reconcile(campaign.id());
        assertThat(report.consistent()).isTrue();
        assertThat(report.ledgerEntryCount()).isEqualTo(3);
        assertThat(auditLogRepository.findByEntityTypeAndEntityId("DISBURSEMENT_REQUEST", request))
                .extracting(AuditLog::getAction).contains("DISBURSEMENT_REQUESTED", "DISBURSEMENT_APPROVED", "DISBURSEMENT_RELEASED");
    }

    @Test
    void pendingDonationDoesNotFundEscrow() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        pendingDonation(donor, campaign.id(), 5_000_000);
        assertThat(escrowQueryService.summary(admin, campaign.id()).totalConfirmedContributions()).isEqualByComparingTo("0");
    }

    @Test
    void duplicateConfirmationIsRejected() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID donation = confirmedDonation(donor, admin, campaign.id(), 1_000_000);
        assertError(() -> donationService.confirm(admin, donation, null), ErrorCode.DUPLICATE_OPERATION);
        assertThat(escrowQueryService.summary(admin, campaign.id()).totalConfirmedContributions()).isEqualByComparingTo("1000000");
    }

    @Test
    void donationBelowMinimumIsRejected() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        assertError(() -> pendingDonation(donor, campaign.id(), 5_000), ErrorCode.DONATION_BELOW_MINIMUM);
    }

    @Test
    void idempotencyKeyReturnsTheSameDonation() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        CreateDonationRequest request = new CreateDonationRequest(vnd(100_000), "VND", null, false, null);
        UUID first = donationService.create(donor, campaign.id(), request, "key-1").id();
        UUID second = donationService.create(donor, campaign.id(), request, "key-1").id();
        assertThat(second).isEqualTo(first);
        assertError(() -> donationService.create(donor, campaign.id(), new CreateDonationRequest(vnd(200_000), "VND", null, false, null), "key-1"),
                ErrorCode.DUPLICATE_OPERATION);
    }

    @Test
    void requestWithoutEvidenceIsRejected() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        milestoneService.start(creator, campaign.milestones().get(0));
        assertError(() -> requestFunds(creator, campaign.milestones().get(0), 1_000_000), ErrorCode.EVIDENCE_REQUIRED);
    }

    @Test
    void requestAboveMilestoneBudgetIsRejected() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 20_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        assertError(() -> requestFunds(creator, m1, 10_000_001), ErrorCode.MILESTONE_BUDGET_EXCEEDED);
    }

    @Test
    void requestAboveAvailableBalanceIsRejected() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 2_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        assertError(() -> requestFunds(creator, m1, 3_000_000), ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);
        assertThat(escrowQueryService.summary(admin, campaign.id()).totalReserved()).isEqualByComparingTo("0");
    }

    @Test
    void secondOpenRequestOnSameMilestoneIsRejected() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        requestFunds(creator, m1, 1_000_000);
        assertError(() -> requestFunds(creator, m1, 1_000_000), ErrorCode.OPEN_REQUEST_EXISTS);
    }

    @Test
    void approvalRequiresAcceptedEvidence() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        UUID request = requestFunds(creator, m1, 1_000_000);
        disbursementService.startReview(admin, request);
        assertError(() -> disbursementService.approve(admin, request, "ok"), ErrorCode.EVIDENCE_REQUIRED);
    }

    @Test
    void userWhoIsBothAdminAndCreatorCannotReviewOwnRequest() {
        AuthUser dual = user(Role.ADMIN, Role.CAMPAIGN_CREATOR);
        ActiveCampaign campaign = activeCampaign(dual, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(dual, m1);
        acceptedEvidence(dual, admin, m1);
        UUID request = requestFunds(dual, m1, 1_000_000);
        assertError(() -> disbursementService.startReview(dual, request), ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        assertError(() -> evidenceService.review(dual, submittedEvidence(dual, m1), true, "self"), ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
    }

    @Test
    void duplicateReleaseIsRejected() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        acceptedEvidence(creator, admin, m1);
        UUID request = approvedRequest(creator, admin, m1, 4_000_000);
        disbursementService.release(admin, request);
        assertError(() -> disbursementService.release(admin, request), ErrorCode.DUPLICATE_OPERATION);
        assertThat(escrowQueryService.summary(admin, campaign.id()).totalDisbursed()).isEqualByComparingTo("4000000");
    }

    @Test
    void rejectionReturnsReservationToAvailable() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        UUID request = requestFunds(creator, m1, 6_000_000);
        disbursementService.reject(admin, request, "Insufficient detail");

        EscrowSummary summary = escrowQueryService.summary(admin, campaign.id());
        assertThat(summary.availableBalance()).isEqualByComparingTo("10000000");
        assertThat(summary.totalReserved()).isEqualByComparingTo("0");
        assertThat(milestoneRepository.findById(m1).orElseThrow().getReservedAmount()).isEqualByComparingTo("0");
        assertThat(milestoneRepository.findById(m1).orElseThrow().getMilestoneStatus()).isEqualTo(MilestoneStatus.EVIDENCE_SUBMITTED);
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void fullyDisbursedMilestoneCanBeCompletedAndNextBecomesReady() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 5_000_000, 5_000_000);
        confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        acceptedEvidence(creator, admin, m1);
        disbursementService.release(admin, approvedRequest(creator, admin, m1, 5_000_000));
        assertThat(milestoneRepository.findById(m1).orElseThrow().getMilestoneStatus()).isEqualTo(MilestoneStatus.DISBURSED);
        milestoneService.complete(admin, m1, "done");
        assertThat(milestoneRepository.findById(campaign.milestones().get(1)).orElseThrow().getMilestoneStatus()).isEqualTo(MilestoneStatus.READY);
    }
}
