package com.riseai.integration;

import com.riseai.dto.request.CreateDisbursementRequest;
import com.riseai.dto.request.CreateRefundRequest;
import com.riseai.dto.response.EscrowSummary;
import com.riseai.enums.DisbursementStatus;
import com.riseai.enums.Role;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyIntegrationTest extends AbstractMySqlIntegrationTest {
    private AuthUser admin;
    private AuthUser creator;
    private AuthUser donor;

    @BeforeEach
    void users() {
        admin = user(Role.ADMIN);
        creator = user(Role.CAMPAIGN_CREATOR);
        donor = user(Role.DONOR);
    }

    private long successes(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        long ok = 0;
        for (Future<Object> future : futures) {
            try {
                future.get(60, TimeUnit.SECONDS);
                ok++;
            } catch (ExecutionException expected) {
                assertThat(expected.getCause()).isInstanceOf(RuntimeException.class);
            }
        }
        pool.shutdown();
        return ok;
    }

    @Test
    void concurrentReleaseOfSameRequestSucceedsOnce() throws Exception {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        acceptedEvidence(creator, admin, m1);
        UUID request = approvedRequest(creator, admin, m1, 5_000_000);

        long ok = successes(List.of(() -> disbursementService.release(admin, request), () -> disbursementService.release(admin, request),
                () -> disbursementService.release(admin, request)));

        assertThat(ok).isEqualTo(1);
        assertThat(disbursementRepository.findById(request).orElseThrow().getRequestStatus()).isEqualTo(DisbursementStatus.RELEASED);
        assertThat(escrowQueryService.summary(admin, campaign.id()).totalDisbursed()).isEqualByComparingTo("5000000");
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void concurrentRequestsOnSameMilestoneCannotOverAllocate() throws Exception {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 10_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);

        long ok = successes(List.of(() -> requestFunds(creator, m1, 7_000_000), () -> requestFunds(creator, m1, 7_000_000)));

        assertThat(ok).isEqualTo(1);
        assertThat(escrowQueryService.summary(admin, campaign.id()).totalReserved()).isEqualByComparingTo("7000000");
        assertThat(milestoneRepository.findById(m1).orElseThrow().getReservedAmount()).isEqualByComparingTo("7000000");
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void concurrentConfirmationsDoNotLoseUpdates() throws Exception {
        ActiveCampaign campaign = activeCampaign(creator, admin, 100_000_000);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            UUID donation = pendingDonation(user(Role.DONOR), campaign.id(), 1_000_000);
            tasks.add(() -> donationService.confirm(admin, donation, null));
        }

        assertThat(successes(tasks)).isEqualTo(8);
        assertThat(escrowQueryService.summary(admin, campaign.id()).totalConfirmedContributions()).isEqualByComparingTo("8000000");
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void concurrentConfirmationOfSameDonationCountsOnce() throws Exception {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID donation = pendingDonation(donor, campaign.id(), 2_000_000);

        long ok = successes(List.of(() -> donationService.confirm(admin, donation, null), () -> donationService.confirm(admin, donation, null)));

        assertThat(ok).isEqualTo(1);
        assertThat(escrowQueryService.summary(admin, campaign.id()).totalConfirmedContributions()).isEqualByComparingTo("2000000");
    }

    @Test
    void concurrentReplaysWithTheSameIdempotencyKeyCreateOneDisbursement() throws Exception {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(donor, admin, campaign.id(), 5_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> disbursementService.create(creator, m1,
                    new CreateDisbursementRequest(vnd(1_000_000), "Supplier"), "same-key").id());
        }

        assertThat(successes(tasks)).isEqualTo(4);
        assertThat(escrowQueryService.summary(admin, campaign.id()).totalReserved()).isEqualByComparingTo("1000000");
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void refundApprovalAndDisbursementRequestCannotBothClaimTheSameFunds() throws Exception {
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        UUID donation = confirmedDonation(donor, admin, campaign.id(), 1_000_000);
        UUID m1 = campaign.milestones().get(0);
        milestoneService.start(creator, m1);
        submittedEvidence(creator, m1);
        UUID refund = refundService.request(donor, donation, new CreateRefundRequest(vnd(800_000), "Need it")).id();

        long ok = successes(List.of(
                () -> refundService.approve(admin, refund, "ok"),
                () -> requestFunds(creator, m1, 800_000)));

        assertThat(ok).isEqualTo(1);
        EscrowSummary summary = escrowQueryService.summary(admin, campaign.id());
        assertThat(summary.totalReserved().add(summary.totalRefundReserved())).isEqualByComparingTo("800000");
        assertThat(summary.availableBalance()).isEqualByComparingTo("200000");
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }
}
