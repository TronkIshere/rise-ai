package com.riseai.escrow;

import com.riseai.escrow.EscrowDtos.ReconciliationRunResponse;
import com.riseai.security.AuthDtos.LoginRequest;
import com.riseai.security.AuthService;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReconciliationJobIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    AuthService authService;

    @Test
    void runDetectsAggregateDriftRecordsFindingsAndHistory() {
        AuthUser admin = user(Role.ADMIN);
        AuthUser creator = user(Role.CAMPAIGN_CREATOR);
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        confirmedDonation(user(Role.DONOR), admin, campaign.id(), 1_000_000);
        UUID accountId = escrowAccountRepository.findByCampaignIdAndCurrency(campaign.id(), "VND").orElseThrow().getId();

        jdbcTemplate.update("update escrow_accounts set total_confirmed_contributions = total_confirmed_contributions + 1, "
                + "available_balance = available_balance + 1 where id = ?", accountId.toString());
        try {
            ReconciliationRunResponse run = reconciliationService.run(ReconciliationTrigger.MANUAL, admin.id());

            assertThat(run.runStatus()).isEqualTo(ReconciliationRunStatus.INCONSISTENT);
            assertThat(run.triggeredBy()).isEqualTo(admin.id());
            assertThat(run.inconsistentAccounts()).isGreaterThanOrEqualTo(1);
            assertThat(run.findings()).anySatisfy(finding -> {
                assertThat(finding.escrowAccountId()).isEqualTo(accountId);
                assertThat(finding.stored().confirmed()).isEqualByComparingTo("1000001");
                assertThat(finding.computedFromLedger().confirmed()).isEqualByComparingTo("1000000");
            });
            assertThat(reconciliationService.get(run.id()).findings()).extracting(EscrowDtos.ReconciliationReport::escrowAccountId).contains(accountId);
            assertThat(reconciliationService.history(ReconciliationRunStatus.INCONSISTENT, PageRequest.of(0, 100)).getContent())
                    .extracting(ReconciliationRunResponse::id).contains(run.id());
            assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
                assertThat(log.getAction()).isEqualTo("RECONCILIATION_RUN_COMPLETED");
                assertThat(log.getEntityId()).isEqualTo(run.id());
            });
        } finally {
            jdbcTemplate.update("update escrow_accounts set total_confirmed_contributions = total_confirmed_contributions - 1, "
                    + "available_balance = available_balance - 1 where id = ?", accountId.toString());
        }
        assertThat(escrowQueryService.reconcile(campaign.id()).consistent()).isTrue();
    }

    @Test
    void runHistoryIsAppendOnly() {
        ReconciliationRunResponse run = reconciliationService.run(ReconciliationTrigger.MANUAL, null);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                jdbcTemplate.update("delete from reconciliation_runs where id = ?", run.id().toString())))
                .hasMessageContaining("append-only");
    }

    @Test
    void manualRunEndpointIsAdminOnly() throws Exception {
        AuthUser donor = user(Role.DONOR);
        AuthUser admin = user(Role.ADMIN);
        mvc.perform(post("/api/v1/admin/escrow/reconciliation/runs").header("Authorization", bearer(donor)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/escrow/reconciliation/runs").header("Authorization", bearer(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.data.triggeredBy").value(admin.id().toString()));
        mvc.perform(get("/api/v1/admin/escrow/reconciliation/runs?size=5").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(5));
    }

    private String bearer(AuthUser user) {
        return "Bearer " + authService.login(new LoginRequest(user.email(), PASSWORD)).accessToken();
    }
}
