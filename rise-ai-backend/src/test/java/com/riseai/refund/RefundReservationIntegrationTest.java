package com.riseai.refund;

import com.riseai.common.exception.ErrorCode;
import com.riseai.donation.DonationStatus;
import com.riseai.escrow.EscrowDtos.EscrowSummary;
import com.riseai.escrow.EscrowLedgerEntry;
import com.riseai.escrow.EscrowLedgerRepository;
import com.riseai.escrow.LedgerTransactionType;
import com.riseai.refund.RefundDtos.CreateRefundRequest;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefundReservationIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    EscrowLedgerRepository ledgerRepository;
    @Autowired
    RefundRepository refundRepository;

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
    void approvalReservesFundsSoADisbursementCannotTakeThem() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID donation = confirmedDonation(donor, admin, campaign.id(), 3_000_000);
        UUID refund = refundService.request(donor, donation, new CreateRefundRequest(vnd(1_000_000), "Need it back")).id();

        RefundDtos.RefundResponse approved = refundService.approve(admin, refund, "ok");

        assertThat(approved.fundsReserved()).isTrue();
        EscrowSummary summary = escrowQueryService.summary(admin, campaign.id());
        assertThat(summary.totalRefundReserved()).isEqualByComparingTo("1000000");
        assertThat(summary.availableBalance()).isEqualByComparingTo("2000000");
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        assertError(() -> requestFunds(creator, m1, 2_500_000), ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);
        requestFunds(creator, m1, 2_000_000);

        refundService.record(admin, refund);

        summary = escrowQueryService.summary(admin, campaign.id());
        assertThat(summary.totalRefundReserved()).isEqualByComparingTo("0");
        assertThat(summary.totalRefunded()).isEqualByComparingTo("1000000");
        assertThat(summary.availableBalance()).isEqualByComparingTo("0");
        assertThat(types(campaign.id())).contains(LedgerTransactionType.REFUND_RESERVED, LedgerTransactionType.REFUND_RESERVATION_RELEASED,
                LedgerTransactionType.REFUND_RECORDED);
        assertThat(refundRepository.findById(refund).orElseThrow().isFundsReserved()).isFalse();
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void rejectingAnApprovedRefundReturnsTheReservation() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID donation = confirmedDonation(donor, admin, campaign.id(), 3_000_000);
        UUID refund = refundService.request(donor, donation, new CreateRefundRequest(vnd(1_000_000), "Need it back")).id();
        refundService.approve(admin, refund, "ok");

        refundService.reject(admin, refund, "Changed decision");

        EscrowSummary summary = escrowQueryService.summary(admin, campaign.id());
        assertThat(summary.totalRefundReserved()).isEqualByComparingTo("0");
        assertThat(summary.availableBalance()).isEqualByComparingTo("3000000");
        assertThat(donationRepository.findById(donation).orElseThrow().getDonationStatus()).isEqualTo(DonationStatus.CONFIRMED);
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void approvedRefundCanStillBeRecordedWhileCampaignIsPaused() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID donation = confirmedDonation(donor, admin, campaign.id(), 3_000_000);
        UUID refund = refundService.request(donor, donation, new CreateRefundRequest(vnd(3_000_000), "All of it")).id();
        refundService.approve(admin, refund, "ok");
        campaignService.pause(admin, campaign.id(), "Investigation");

        refundService.record(admin, refund);

        assertThat(donationRepository.findById(donation).orElseThrow().getDonationStatus()).isEqualTo(DonationStatus.REFUNDED);
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    private List<LedgerTransactionType> types(UUID campaignId) {
        return ledgerRepository.findByCampaignId(campaignId, Pageable.unpaged()).map(EscrowLedgerEntry::getTransactionType).toList();
    }
}
