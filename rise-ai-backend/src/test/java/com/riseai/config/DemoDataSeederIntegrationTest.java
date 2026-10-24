package com.riseai.config;

import com.riseai.campaign.CampaignRepository;
import com.riseai.common.Slugs;
import com.riseai.disbursement.DisbursementStatus;
import com.riseai.escrow.EscrowDtos.ReconciliationReport;
import com.riseai.escrow.EscrowLedgerRepository;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "rise.seed.enabled=true")
class DemoDataSeederIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    DemoDataSeeder seeder;
    @Autowired
    CampaignRepository campaignRepository;
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
}
