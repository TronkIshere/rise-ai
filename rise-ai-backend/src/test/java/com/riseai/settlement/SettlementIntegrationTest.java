package com.riseai.settlement;

import com.riseai.common.exception.ErrorCode;
import com.riseai.donation.Donation;
import com.riseai.donation.DonationStatus;
import com.riseai.escrow.EscrowDtos.EscrowSummary;
import com.riseai.escrow.EscrowLedgerRepository;
import com.riseai.escrow.LedgerTransactionType;
import com.riseai.refund.RefundDtos.CreateRefundRequest;
import com.riseai.security.AuthUser;
import com.riseai.settlement.SettlementDtos.SettlementResponse;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    EscrowLedgerRepository ledgerRepository;

    AuthUser admin;
    AuthUser secondAdmin;
    AuthUser creator;
    AuthUser donorA;
    AuthUser donorB;
    AuthUser donorC;

    @BeforeEach
    void setUp() {
        admin = user(Role.ADMIN);
        secondAdmin = user(Role.ADMIN);
        creator = user(Role.CAMPAIGN_CREATOR);
        donorA = user(Role.DONOR);
        donorB = user(Role.DONOR);
        donorC = user(Role.DONOR);
    }

    record Funded(UUID campaignId, UUID a, UUID b, UUID c) {
    }

    private Funded completedWithLeftover() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID a = confirmedDonation(donorA, admin, campaign.id(), 6_000_000);
        UUID b = confirmedDonation(donorB, admin, campaign.id(), 3_000_000);
        UUID c = confirmedDonation(donorC, admin, campaign.id(), 1_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        releasedRequest(creator, admin, m1, 4_000_000);
        campaignService.complete(admin, campaign.id(), "Done");
        return new Funded(campaign.id(), a, b, c);
    }

    @Test
    void leftoverBalanceIsRefundedProRataAfterASecondAdminApproves() {
        Funded f = completedWithLeftover();

        SettlementResponse proposed = settlementService.propose(admin, f.campaignId(), "Return unused funds");
        assertThat(proposed.amount()).isEqualByComparingTo("6000000");
        assertThat(proposed.settlementStatus()).isEqualTo(SettlementStatus.PROPOSED);
        assertError(() -> settlementService.approve(admin, proposed.id(), "self"), ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
        assertError(() -> settlementService.propose(secondAdmin, f.campaignId(), "again"), ErrorCode.OPEN_REQUEST_EXISTS);

        SettlementResponse executed = settlementService.approve(secondAdmin, proposed.id(), "Approved");

        assertThat(executed.settlementStatus()).isEqualTo(SettlementStatus.EXECUTED);
        assertThat(executed.donationCount()).isEqualTo(3);
        assertThat(refunded(f.a())).isEqualByComparingTo("3600000");
        assertThat(refunded(f.b())).isEqualByComparingTo("1800000");
        assertThat(refunded(f.c())).isEqualByComparingTo("600000");
        assertThat(donationRepository.findById(f.a()).orElseThrow().getDonationStatus()).isEqualTo(DonationStatus.PARTIALLY_REFUNDED);
        EscrowSummary summary = escrowQueryService.summary(admin, f.campaignId());
        assertThat(summary.availableBalance()).isEqualByComparingTo("0");
        assertThat(summary.totalRefunded()).isEqualByComparingTo("6000000");
        assertThat(summary.totalDisbursed()).isEqualByComparingTo("4000000");
        assertThat(ledgerRepository.findByCampaignIdAndTransactionType(f.campaignId(), LedgerTransactionType.SETTLEMENT_REFUND_RECORDED, Pageable.unpaged())
                .getTotalElements()).isEqualTo(3);
        assertThat(escrowQueryService.reconcile(f.campaignId()).consistent()).isTrue();
        assertError(() -> settlementService.approve(secondAdmin, proposed.id(), "again"), ErrorCode.INVALID_STATE_TRANSITION);
        assertError(() -> settlementService.propose(admin, f.campaignId(), "again"), ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    void cancelledCampaignRefundsEveryDonationInFull() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID a = confirmedDonation(donorA, admin, campaign.id(), 2_000_000);
        UUID b = confirmedDonation(donorB, admin, campaign.id(), 1_000_000);
        campaignService.cancel(admin, campaign.id(), "Organizer withdrew");

        UUID settlement = settlementService.propose(admin, campaign.id(), "Return all funds").id();
        settlementService.approve(secondAdmin, settlement, null);

        assertThat(donationRepository.findById(a).orElseThrow().getDonationStatus()).isEqualTo(DonationStatus.REFUNDED);
        assertThat(donationRepository.findById(b).orElseThrow().getDonationStatus()).isEqualTo(DonationStatus.REFUNDED);
        assertThat(escrowQueryService.summary(admin, campaign.id()).availableBalance()).isEqualByComparingTo("0");
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void settlementPreconditionsAreEnforced() {
        ActiveCampaign active = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donorA, admin, active.id(), 1_000_000);
        assertError(() -> settlementService.propose(admin, active.id(), "too early"), ErrorCode.INVALID_STATE_TRANSITION);

        AuthUser adminCreator = user(Role.ADMIN, Role.CAMPAIGN_CREATOR);
        ActiveCampaign own = activeCampaign(adminCreator, admin, 10_000_000);
        confirmedDonation(donorA, admin, own.id(), 1_000_000);
        campaignService.cancel(admin, own.id(), "stop");
        assertError(() -> settlementService.propose(adminCreator, own.id(), "mine"), ErrorCode.CONFLICT_OF_INTEREST);

        Funded f = completedWithLeftover();
        refundService.request(donorB, f.b(), new CreateRefundRequest(vnd(100_000), "Need it"));
        assertError(() -> settlementService.propose(admin, f.campaignId(), "open refund"), ErrorCode.OPEN_REQUEST_EXISTS);
    }

    @Test
    void proposalBecomesStaleWhenTheBalanceChanges() {
        Funded f = completedWithLeftover();
        UUID settlement = settlementService.propose(admin, f.campaignId(), "Return unused funds").id();
        UUID refund = refundService.request(donorC, f.c(), new CreateRefundRequest(vnd(500_000), "Need it")).id();
        refundService.approve(secondAdmin, refund, "ok");
        refundService.record(secondAdmin, refund);

        assertError(() -> settlementService.approve(secondAdmin, settlement, "go"), ErrorCode.CONCURRENT_MODIFICATION);
        assertError(() -> settlementService.cancel(secondAdmin, settlement, "not mine"), ErrorCode.ACCESS_DENIED);
        assertThat(settlementService.cancel(admin, settlement, "stale").settlementStatus()).isEqualTo(SettlementStatus.CANCELLED);

        UUID fresh = settlementService.propose(admin, f.campaignId(), "Return unused funds").id();
        assertThat(settlementService.get(fresh).amount()).isEqualByComparingTo("5500000");
        assertThat(settlementService.reject(secondAdmin, fresh, "Use reallocation instead").settlementStatus()).isEqualTo(SettlementStatus.REJECTED);
        assertThat(escrowQueryService.reconcile(f.campaignId()).consistent()).isTrue();
    }

    private java.math.BigDecimal refunded(UUID donationId) {
        Donation donation = donationRepository.findById(donationId).orElseThrow();
        return donation.getRefundedAmount();
    }
}
