package com.riseai.integration;

import com.riseai.configuration.DemoDataSeeder;
import com.riseai.configuration.ScheduledJobs;
import com.riseai.dto.response.ReconciliationReport;
import com.riseai.dto.response.ReconciliationRunResponse;
import com.riseai.enums.DisbursementStatus;
import com.riseai.enums.ReconciliationRunStatus;
import com.riseai.enums.ReconciliationTrigger;
import com.riseai.enums.Role;
import com.riseai.repository.CampaignRepository;
import com.riseai.repository.EscrowLedgerRepository;
import com.riseai.repository.ReconciliationRunRepository;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.util.Slugs;
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
    com.riseai.repository.OutboxEventRepository outboxEventRepository;
    @Autowired
    com.fasterxml.jackson.databind.ObjectMapper objectMapper;
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
    void everySeededEventConformsToTheEnvelopeSchema() throws Exception {
        java.util.List<com.riseai.entity.OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).isNotEmpty();
        for (com.riseai.entity.OutboxEvent event : events) {
            assertThat(com.riseai.support.EventSchemaContract.validate(objectMapper.readTree(event.getEnvelope())))
                    .as(event.getEventType()).isEmpty();
        }
        assertThat(events.stream().map(com.riseai.entity.OutboxEvent::getEventType).distinct().count()).isGreaterThanOrEqualTo(15);
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
