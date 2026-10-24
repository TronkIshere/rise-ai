package com.riseai.config;

import com.riseai.campaign.CampaignRepository;
import com.riseai.common.Slugs;
import com.riseai.disbursement.DisbursementStatus;
import com.riseai.escrow.EscrowDtos.ReconciliationReport;
import com.riseai.escrow.EscrowDtos.ReconciliationRunResponse;
import com.riseai.escrow.EscrowLedgerRepository;
import com.riseai.escrow.ReconciliationRunRepository;
import com.riseai.escrow.ReconciliationRunStatus;
import com.riseai.escrow.ReconciliationTrigger;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"rise.seed.enabled=true", "rise.jobs.enabled=true",
        "rise.jobs.reconciliation-cron=0 0 0 1 1 *", "rise.jobs.fundraising-close-cron=0 0 0 1 1 *"})
class DemoDataSeederIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    DemoDataSeeder seeder;
    @Autowired
    ScheduledJobs scheduledJobs;
    @Autowired
    CampaignRepository campaignRepository;
    @Autowired
    ReconciliationRunRepository reconciliationRunRepository;
    @Autowired
    EscrowLedgerRepository ledgerRepository;

    @Test
    void seedIsCompleteConsistentAndIdempotent() {
        assertThat(userRepository.findByEmailIgnoreCase(DemoDataSeeder.ADMIN_EMAIL)).get()
                .satisfies(admin -> assertThat(admin.getRoles()).containsExactly(Role.ADMIN));
        DemoDataSeeder.CAMPAIGN_TITLES.forEach(title -> assertThat(campaignRepository.existsBySlug(Slugs.slugify(title))).isTrue());
        assertThat(disbursementRepository.countByRequestStatusIn(EnumSet.of(DisbursementStatus.RELEASED))).isGreaterThanOrEqualTo(2);

        long users = userRepository.count();
        long campaigns = campaignRepository.count();
        long ledger = ledgerRepository.count();
        seeder.seed();
        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(campaignRepository.count()).isEqualTo(campaigns);
        assertThat(ledgerRepository.count()).isEqualTo(ledger);

        assertThat(escrowQueryService.reconcileAll()).allSatisfy(report -> assertThat(report.consistent()).isTrue())
                .extracting(ReconciliationReport::ledgerEntryCount).isNotEmpty();
    }

    @Test
    void scheduledReconciliationJobFindsNoInconsistenciesOnSeededData() {
        long before = reconciliationRunRepository.count();

        scheduledJobs.reconcileEscrow();

        assertThat(reconciliationRunRepository.count()).isEqualTo(before + 1);
        ReconciliationRunResponse latest = reconciliationService.history(null, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "startedAt")))
                .getContent().get(0);
        assertThat(latest.triggerType()).isEqualTo(ReconciliationTrigger.SCHEDULED);
        assertThat(latest.runStatus()).isEqualTo(ReconciliationRunStatus.CONSISTENT);
        assertThat(latest.inconsistentAccounts()).isZero();
        assertThat(latest.accountsChecked()).isGreaterThanOrEqualTo(4);
        assertThat(latest.findings()).isEmpty();
    }
}
