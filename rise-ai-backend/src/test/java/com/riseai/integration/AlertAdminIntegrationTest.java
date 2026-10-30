package com.riseai.integration;

import com.riseai.dto.request.LoginRequest;
import com.riseai.dto.response.AlertResponse;
import com.riseai.dto.response.AlertStatsResponse;
import com.riseai.enums.AlertStatus;
import com.riseai.enums.Role;
import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;
import com.riseai.repository.AlertNotificationRepository;
import com.riseai.security.AuthUser;
import com.riseai.service.AlertNotifier;
import com.riseai.service.AuthService;
import com.riseai.service.FindingService;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AlertAdminIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    AlertNotifier alertNotifier;
    @Autowired
    AlertNotificationRepository repository;
    @Autowired
    FindingService findingService;
    @Autowired
    AuthService authService;
    @Autowired
    MockMvc mvc;

    AuthUser admin;
    AuthUser donor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from alert_notifications");
        admin = user(Role.ADMIN);
        donor = user(Role.DONOR);
    }

    private String bearer(AuthUser user) {
        return "Bearer " + authService.login(new LoginRequest(user.email(), PASSWORD)).accessToken();
    }

    private UUID alertOfKind(String kind) {
        alertNotifier.alert(kind, Map.of("ruleId", "r1"));
        return UUID.fromString(jdbcTemplate.queryForObject("select id from alert_notifications where kind = ?", String.class, kind));
    }

    private UUID failedAlert(String kind) {
        UUID id = alertOfKind(kind);
        jdbcTemplate.update("update alert_notifications set status = 'FAILED', attempts = 12, failed_at = utc_timestamp(6), last_error = 'HTTP 500' where id = ?", id.toString());
        return id;
    }

    @Test
    void statsCountEveryStatusAndReportTheOldestUndeliveredAlert() {
        alertOfKind("CRITICAL_FINDING");
        failedAlert("OUTBOX_EVENT_FAILED");

        AlertStatsResponse stats = findingService.alertStats();

        assertThat(stats.pending()).isEqualTo(1);
        assertThat(stats.failed()).isEqualTo(1);
        assertThat(stats.sent()).isZero();
        assertThat(stats.retrying()).isZero();
        assertThat(stats.oldestUndeliveredCreatedAt()).isNotNull();
        assertThat(stats.oldestUndeliveredAgeSeconds()).isNotNegative();
    }

    @Test
    void requeueingAFailedAlertResetsItAndIsAudited() {
        UUID id = failedAlert("CRITICAL_FINDING");

        AlertResponse requeued = findingService.requeueAlert(admin, id, "Webhook repaired");

        assertThat(requeued.status()).isEqualTo(AlertStatus.PENDING);
        assertThat(requeued.attempts()).isZero();
        assertThat(requeued.failedAt()).isNull();
        assertThat(requeued.lastError()).isNull();
        assertThat(auditLogRepository.findAll()).anyMatch(log -> log.getAction().equals("ALERT_NOTIFICATION_REQUEUED") && id.equals(log.getEntityId()));
    }

    @Test
    void onlyFailedAlertsCanBeRequeuedAndOnlyByAnAdministrator() {
        UUID pending = alertOfKind("CRITICAL_FINDING");
        UUID failed = failedAlert("OUTBOX_EVENT_FAILED");

        assertThatThrownBy(() -> findingService.requeueAlert(admin, pending, null))
                .isInstanceOfSatisfying(ApplicationException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        assertThatThrownBy(() -> findingService.requeueAlert(donor, failed, null)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> findingService.requeueAlert(admin, UUID.randomUUID(), null))
                .isInstanceOfSatisfying(ApplicationException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(repository.findById(failed).orElseThrow().getStatus()).isEqualTo(AlertStatus.FAILED);
    }

    @Test
    void alertEndpointsAreAdminOnlyAndFilterByStatus() throws Exception {
        UUID failed = failedAlert("CRITICAL_FINDING");

        mvc.perform(get("/api/v1/admin/monitoring/alerts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/monitoring/alerts").header("Authorization", bearer(donor))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/monitoring/alerts/stats").header("Authorization", bearer(donor))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/monitoring/alerts/" + failed + "/requeue").header("Authorization", bearer(donor))).andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/monitoring/alerts?status=FAILED").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].id").value(failed.toString()))
                .andExpect(jsonPath("$.data.data[0].status").value("FAILED"));
        mvc.perform(get("/api/v1/admin/monitoring/alerts?status=SENT").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.data").isEmpty());
        mvc.perform(get("/api/v1/admin/monitoring/alerts/stats").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.failed").value(1));
        mvc.perform(post("/api/v1/admin/monitoring/alerts/" + failed + "/requeue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"retry\"}").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void listingIsPaginated() {
        for (int i = 0; i < 3; i++) {
            alertNotifier.alert("CRITICAL_FINDING", Map.of("ruleId", "r" + i));
        }

        assertThat(findingService.alerts(null, PageRequest.of(0, 2)).getContent()).hasSize(2);
        assertThat(findingService.alerts(null, PageRequest.of(0, 2)).getTotalElements()).isEqualTo(3);
    }
}
