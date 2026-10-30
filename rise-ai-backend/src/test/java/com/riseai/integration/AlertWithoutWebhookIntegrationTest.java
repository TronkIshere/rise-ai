package com.riseai.integration;

import com.riseai.service.AlertDispatcher;
import com.riseai.service.AlertNotifier;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertWithoutWebhookIntegrationTest extends AbstractMySqlIntegrationTest {
    @DynamicPropertySource
    static void noWebhook(DynamicPropertyRegistry registry) {
        registry.add("rise.monitoring.alert-webhook-url", () -> "");
        registry.add("rise.monitoring.alert-dispatch-interval", () -> "PT1H");
        registry.add("rise.monitoring.alert-dispatch-initial-delay", () -> "PT1H");
    }

    @Autowired
    AlertNotifier alertNotifier;
    @Autowired
    AlertDispatcher alertDispatcher;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from alert_notifications");
    }

    @Test
    void alertsAreStoredWithoutAWebhookAndStayPendingUntilOneIsConfigured() {
        alertNotifier.alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));

        assertThat(alertDispatcher.dispatchDue()).isZero();

        assertThat(jdbcTemplate.queryForObject("select status from alert_notifications where kind = 'CRITICAL_FINDING'", String.class)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("select attempts from alert_notifications where kind = 'CRITICAL_FINDING'", Integer.class)).isZero();
    }
}
