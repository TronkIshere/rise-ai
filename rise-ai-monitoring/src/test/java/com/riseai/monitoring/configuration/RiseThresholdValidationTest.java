package com.riseai.monitoring.configuration;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RiseThresholdValidationTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<MonitoringProperties>> violations(Consumer<MonitoringProperties.Rise> change) {
        MonitoringProperties properties = new MonitoringProperties();
        change.accept(properties.getRise());
        return VALIDATOR.validate(properties);
    }

    @Test
    void defaultsAreValid() {
        assertThat(violations(rise -> {
        })).isEmpty();
    }

    @Test
    void nonPositiveAmountsAreRejectedInsteadOfFlaggingEverything() {
        assertThat(violations(rise -> rise.setMinimalEvidenceApprovalAmount(BigDecimal.ZERO))).isNotEmpty();
        assertThat(violations(rise -> rise.setMinimalEvidenceApprovalAmount(new BigDecimal("-1")))).isNotEmpty();
        assertThat(violations(rise -> rise.setMinimalEvidenceApprovalAmount(null))).isNotEmpty();
        assertThat(violations(rise -> rise.setAdjustmentReviewAmount(BigDecimal.ZERO))).isNotEmpty();
        assertThat(violations(rise -> rise.setAdjustmentReviewAmount(new BigDecimal("0.01")))).isEmpty();
    }

    @Test
    void nonPositiveDurationsAreRejected() {
        assertThat(violations(rise -> rise.setResubmissionWindow(Duration.ZERO))).isNotEmpty();
        assertThat(violations(rise -> rise.setResubmissionWindow(Duration.ofHours(-1)))).isNotEmpty();
        assertThat(violations(rise -> rise.setAiNoteBudget(Duration.ZERO))).isNotEmpty();
        assertThat(violations(rise -> rise.setProjectionRetention(Duration.ZERO))).isNotEmpty();
        assertThat(violations(rise -> rise.setResubmissionWindow(Duration.ofMinutes(1)))).isEmpty();
    }

    @Test
    void countsAndSchemaVersionsAreBounded() {
        assertThat(violations(rise -> rise.setMaxRequestsPerMilestone(1))).isNotEmpty();
        assertThat(violations(rise -> rise.setMinimalEvidenceCount(0))).isNotEmpty();
        assertThat(violations(rise -> rise.setSupportedSchemaVersions(List.of()))).isNotEmpty();
        assertThat(violations(rise -> rise.setAiNoteMaxLength(10))).isNotEmpty();
    }

    @Test
    void requiredServiceKeysMustBeLongEnough() {
        assertThat(violationsOf(security -> {
            security.setRequireServiceKey(true);
            security.setServiceKey("short");
        })).isNotEmpty();
        assertThat(violationsOf(security -> {
            security.setRequireServiceKey(true);
            security.setServiceKey("0123456789abcdef");
            security.setPreviousServiceKey("short");
        })).isNotEmpty();
        assertThat(violationsOf(security -> {
            security.setRequireServiceKey(true);
            security.setServiceKey("0123456789abcdef");
            security.setPreviousServiceKey("fedcba9876543210");
        })).isEmpty();
        assertThat(violationsOf(security -> security.setServiceKey("short"))).isEmpty();
    }

    private Set<ConstraintViolation<MonitoringProperties>> violationsOf(Consumer<MonitoringProperties.Security> change) {
        MonitoringProperties properties = new MonitoringProperties();
        change.accept(properties.getSecurity());
        return VALIDATOR.validate(properties);
    }
}
