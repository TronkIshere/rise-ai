package com.riseai.monitoring.controller;

import com.riseai.monitoring.rise.RiseFindingEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "monitoring.ai.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RiseProjectionRetentionIntegrationTest {
    @Autowired
    RiseFindingEngine engine;
    @Autowired
    JdbcClient jdbcClient;
    @Autowired
    MockMvc mockMvc;

    @Test
    void purgeDeletesOnlyRowsOlderThanTheRetentionPeriod() {
        Timestamp old = Timestamp.from(Instant.now().minus(500, ChronoUnit.DAYS));
        Timestamp recent = Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS));
        insertProcessed("retention-old", old);
        insertProcessed("retention-recent", recent);
        insertEvidence("retention-old", old);
        insertEvidence("retention-recent", recent);
        insertDisbursement("retention-old", old);
        insertDisbursement("retention-recent", recent);

        engine.purgeExpiredProjection();

        for (String table : new String[]{"rise_processed_events", "rise_evidence_hashes", "rise_disbursement_events"}) {
            assertThat(count(table, "retention-old")).as(table).isZero();
            assertThat(count(table, "retention-recent")).as(table).isEqualTo(1);
        }
    }

    @Test
    void rulesEndpointListsTheCharityRules() throws Exception {
        mockMvc.perform(get("/api/v1/monitoring/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.category=='CHARITY')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.id=='rise.evidence.duplicate-file-hash')].active").value(true));
    }

    private void insertProcessed(String id, Timestamp at) {
        jdbcClient.sql("insert into rise_processed_events (event_id, event_type, occurred_at, processed_at) values (?, 'X', ?, ?)")
                .params(id, at, at).update();
    }

    private void insertEvidence(String id, Timestamp at) {
        jdbcClient.sql("insert into rise_evidence_hashes (event_id, evidence_id, file_hash, milestone_id, campaign_id, occurred_at) values (?, 'e', ?, 'm', 'c', ?)")
                .params(id, "a".repeat(64), at).update();
    }

    private void insertDisbursement(String id, Timestamp at) {
        jdbcClient.sql("insert into rise_disbursement_events (event_id, event_type, request_id, milestone_id, campaign_id, amount, occurred_at) values (?, 'X', 'r', 'm', 'c', 1, ?)")
                .params(id, at).update();
    }

    private int count(String table, String id) {
        return jdbcClient.sql("select count(*) from " + table + " where event_id = ?").param(id).query(Integer.class).single();
    }
}
