package com.riseai.service.impl;

import com.riseai.service.AlertNotifier;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlertDispatcherFencingIntegrationTest extends AbstractMySqlIntegrationTest {
    @DynamicPropertySource
    static void webhook(DynamicPropertyRegistry registry) {
        registry.add("rise.monitoring.alert-webhook-url", () -> "http://127.0.0.1:9/alerts");
        registry.add("rise.monitoring.alert-dispatch-interval", () -> "PT1H");
        registry.add("rise.monitoring.alert-dispatch-initial-delay", () -> "PT1H");
        registry.add("rise.monitoring.max-attempts", () -> "5");
    }

    @Autowired
    AlertNotifier alertNotifier;
    @Autowired
    AlertDispatcherImpl dispatcher;

    private UUID id;

    @BeforeEach
    void alertClaimedTwice() {
        jdbcTemplate.update("delete from alert_notifications");
        alertNotifier.alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));
        jdbcTemplate.update("update alert_notifications set status = 'RETRYING', attempts = 2, last_error = 'HTTP 503', next_attempt_at = date_add(utc_timestamp(6), interval 1 hour)");
        id = UUID.fromString(jdbcTemplate.queryForObject("select id from alert_notifications", String.class));
    }

    private String column(String name) {
        return jdbcTemplate.queryForObject("select cast(" + name + " as char) from alert_notifications", String.class);
    }

    @Test
    void aFailureReportedByAWorkerWhoseClaimWasSupersededChangesNothing() {
        String nextAttempt = column("next_attempt_at");

        dispatcher.recordOutcome(id, 1, AlertWebhookClient.Delivery.failure(true, "IOException"));
        dispatcher.recordOutcome(id, 1, AlertWebhookClient.Delivery.failure(false, "HTTP 404"));

        assertThat(column("status")).isEqualTo("RETRYING");
        assertThat(column("attempts")).isEqualTo("2");
        assertThat(column("last_error")).isEqualTo("HTTP 503");
        assertThat(column("next_attempt_at")).isEqualTo(nextAttempt);
    }

    @Test
    void aFailureReportedByTheCurrentClaimHolderIsApplied() {
        dispatcher.recordOutcome(id, 2, AlertWebhookClient.Delivery.failure(true, "IOException"));

        assertThat(column("status")).isEqualTo("RETRYING");
        assertThat(column("last_error")).isEqualTo("IOException");
    }

    @Test
    void aSuccessfulDeliveryIsRecordedEvenByASupersededWorkerAndCannotBeUndone() {
        dispatcher.recordOutcome(id, 1, AlertWebhookClient.Delivery.success());
        assertThat(column("status")).isEqualTo("SENT");

        dispatcher.recordOutcome(id, 2, AlertWebhookClient.Delivery.failure(false, "HTTP 404"));

        assertThat(column("status")).isEqualTo("SENT");
        assertThat(column("last_error")).isNull();
    }
}
