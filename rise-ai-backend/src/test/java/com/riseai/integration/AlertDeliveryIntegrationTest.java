package com.riseai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.enums.AlertStatus;
import com.riseai.repository.AlertNotificationRepository;
import com.riseai.service.AlertDispatcher;
import com.riseai.service.AlertNotifier;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class AlertDeliveryIntegrationTest extends AbstractMySqlIntegrationTest {
    private static final HttpServer ALERTS;
    private static final List<JsonNode> BODIES = new CopyOnWriteArrayList<>();
    private static volatile int responseStatus = 204;

    static {
        try {
            ALERTS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        ObjectMapper mapper = new ObjectMapper();
        ALERTS.createContext("/alerts", exchange -> {
            BODIES.add(mapper.readTree(exchange.getRequestBody()));
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
        });
        ALERTS.start();
    }

    @DynamicPropertySource
    static void webhook(DynamicPropertyRegistry registry) {
        registry.add("rise.monitoring.alert-webhook-url", () -> "http://127.0.0.1:" + ALERTS.getAddress().getPort() + "/alerts");
        registry.add("rise.monitoring.alert-dispatch-interval", () -> "PT1H");
        registry.add("rise.monitoring.alert-dispatch-initial-delay", () -> "PT1H");
        registry.add("rise.monitoring.max-attempts", () -> "3");
    }

    @Autowired
    AlertNotifier alertNotifier;
    @Autowired
    AlertDispatcher alertDispatcher;
    @Autowired
    AlertNotificationRepository repository;
    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from alert_notifications");
        BODIES.clear();
        responseStatus = 204;
    }

    private String statusOf(String kind) {
        return jdbcTemplate.queryForObject("select status from alert_notifications where kind = ?", String.class, kind);
    }

    @Test
    void anAlertIsStoredFirstAndDeliveredByTheDispatcher() {
        alertNotifier.alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));

        assertThat(statusOf("CRITICAL_FINDING")).isEqualTo("PENDING");
        assertThat(BODIES).isEmpty();

        assertThat(alertDispatcher.dispatchDue()).isEqualTo(1);

        assertThat(statusOf("CRITICAL_FINDING")).isEqualTo("SENT");
        assertThat(BODIES).singleElement().satisfies(body -> {
            assertThat(body.path("kind").asText()).isEqualTo("CRITICAL_FINDING");
            assertThat(body.path("details").path("ruleId").asText()).isEqualTo("r1");
        });
        assertThat(alertDispatcher.dispatchDue()).isZero();
    }

    @Test
    void anAlertThatCannotBeDeliveredStaysQueuedWithBackoffAndIsDeliveredLater() {
        responseStatus = 503;
        alertNotifier.alert("OUTBOX_EVENT_FAILED", Map.of("eventId", "e1"));

        assertThat(alertDispatcher.dispatchDue()).isZero();
        assertThat(statusOf("OUTBOX_EVENT_FAILED")).isEqualTo("RETRYING");
        assertThat(jdbcTemplate.queryForObject("select last_error from alert_notifications where kind = 'OUTBOX_EVENT_FAILED'", String.class)).isEqualTo("HTTP 503");
        assertThat(jdbcTemplate.queryForObject("select attempts from alert_notifications where kind = 'OUTBOX_EVENT_FAILED'", Integer.class)).isEqualTo(1);
        assertThat(alertDispatcher.dispatchDue()).isZero();
        assertThat(BODIES).hasSize(1);

        responseStatus = 204;
        jdbcTemplate.update("update alert_notifications set next_attempt_at = utc_timestamp(6)");
        assertThat(alertDispatcher.dispatchDue()).isEqualTo(1);
        assertThat(statusOf("OUTBOX_EVENT_FAILED")).isEqualTo("SENT");
    }

    @Test
    void anAlertIsMarkedFailedWhenAttemptsAreExhaustedButIsNeverDeleted() {
        responseStatus = 500;
        alertNotifier.alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));

        for (int attempt = 0; attempt < 3; attempt++) {
            jdbcTemplate.update("update alert_notifications set next_attempt_at = utc_timestamp(6) where status in ('PENDING', 'RETRYING')");
            alertDispatcher.dispatchDue();
        }

        assertThat(statusOf("CRITICAL_FINDING")).isEqualTo("FAILED");
        assertThat(repository.countByStatus(AlertStatus.FAILED)).isEqualTo(1);
        jdbcTemplate.update("update alert_notifications set next_attempt_at = utc_timestamp(6)");
        assertThat(alertDispatcher.dispatchDue()).isZero();
        assertThat(BODIES).hasSize(3);
    }

    @Test
    void aPermanentRejectionFailsTheAlertAtOnceAndKeepsItsPayload() {
        responseStatus = 404;
        alertNotifier.alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));

        assertThat(alertDispatcher.dispatchDue()).isZero();

        assertThat(statusOf("CRITICAL_FINDING")).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("select attempts from alert_notifications where kind = 'CRITICAL_FINDING'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select last_error from alert_notifications where kind = 'CRITICAL_FINDING'", String.class)).isEqualTo("HTTP 404");
        assertThat(jdbcTemplate.queryForObject("select count(*) from alert_notifications where kind = 'CRITICAL_FINDING' and failed_at is not null and details like '%r1%'", Integer.class)).isEqualTo(1);
        assertThat(BODIES).hasSize(1);
    }

    @Test
    void anAlertWhoseAttemptsRanOutWhileClaimedIsFailedWithoutAnotherDelivery() {
        alertNotifier.alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));
        jdbcTemplate.update("update alert_notifications set status = 'RETRYING', attempts = 3, last_error = 'HTTP 503'");

        assertThat(alertDispatcher.dispatchDue()).isZero();

        assertThat(statusOf("CRITICAL_FINDING")).isEqualTo("FAILED");
        assertThat(BODIES).isEmpty();
    }

    @Test
    void anAlertRaisedInsideARolledBackTransactionIsNotStored() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            alertNotifier.alert("CRITICAL_FINDING", Map.of("ruleId", "rolled-back"));
            status.setRollbackOnly();
        });

        assertThat(repository.count()).isZero();
    }

    @Test
    void deliveredAlertsAreRemovedAfterTheRetentionPeriodOnly() {
        alertNotifier.alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));
        alertDispatcher.dispatchDue();
        assertThat(alertDispatcher.purgeSent()).isZero();

        jdbcTemplate.update("update alert_notifications set delivered_at = date_sub(utc_timestamp(6), interval 60 day)");

        assertThat(alertDispatcher.purgeSent()).isEqualTo(1);
    }
}
