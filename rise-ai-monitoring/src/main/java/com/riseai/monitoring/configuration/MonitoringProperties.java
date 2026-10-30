package com.riseai.monitoring.configuration;

import com.riseai.monitoring.core.analysis.AiAssessment;
import com.riseai.monitoring.core.event.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "monitoring")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MonitoringProperties {

    @Valid
    @NotNull
    Limits limits = new Limits();

    @Valid
    @NotNull
    Rules rules = new Rules();

    @Valid
    @NotNull
    Ai ai = new Ai();

    @Valid
    @NotNull
    Policy policy = new Policy();

    @Valid
    @NotNull
    Deduplication deduplication = new Deduplication();

    @Valid
    @NotNull
    Delivery delivery = new Delivery();

    @Valid
    @NotNull
    Security security = new Security();

    @Valid
    @NotNull
    Rise rise = new Rise();

    public enum AiTrigger {
        ALWAYS,
        ON_RULE_FLAG
    }

    public enum AiFailureMode {
        INCONCLUSIVE,
        RULES_ONLY
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Limits {

        @Min(1024)
        long maxRequestBytes = 262144;

        @Min(1)
        int maxPayloadBytes = 65536;

        @Min(1)
        @Max(100)
        int maxPayloadDepth = 20;

        @Min(0)
        @Max(200)
        int maxAttributes = 50;

        @Min(1)
        @Max(10000)
        int maxAttributeValueLength = 1000;
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Rules {

        boolean enabled = true;

        @NotNull
        List<String> disabledRules = new ArrayList<>();

        @NotNull
        List<String> allowedEventTypes = new ArrayList<>();

        @NotNull
        List<String> requiredAttributes = new ArrayList<>();

        @NotNull
        Duration maxClockSkew = Duration.ofMinutes(5);

        @NotNull
        Duration maxEventAge = Duration.ofDays(7);

        @NotNull
        Severity flagReportedSeverityAt = Severity.HIGH;

        @Valid
        @NotNull
        List<AttributeThreshold> thresholds = new ArrayList<>();
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AttributeThreshold {

        @NotBlank
        String attribute;

        BigDecimal min;

        BigDecimal max;

        @NotNull
        List<String> eventTypes = new ArrayList<>();

        @NotNull
        Severity severity = Severity.HIGH;

        @AssertTrue(message = "a threshold must define min, max or both")
        public boolean isBoundDefined() {
            return min != null || max != null;
        }
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Ai {

        boolean enabled = true;

        @NotNull
        AiTrigger trigger = AiTrigger.ON_RULE_FLAG;

        @NotNull
        List<String> alwaysAnalyzeEventTypes = new ArrayList<>();

        @NotNull
        AiFailureMode onFailure = AiFailureMode.INCONCLUSIVE;

        @Min(500)
        int maxPromptEventCharacters = 8000;

        boolean includePayload = false;

        @NotNull
        List<String> redactedFieldNames = new ArrayList<>(List.of("password", "secret", "token", "authorization", "apikey", "credential"));

        @Min(1)
        @Max(20)
        int maxListItems = 5;

        @Min(20)
        int maxItemLength = 300;

        @Min(50)
        int maxExplanationLength = 1000;
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Policy {

        @NotNull
        Severity reviewRuleSeverity = Severity.HIGH;

        @NotNull
        List<AiAssessment> reviewAiAssessments = new ArrayList<>(List.of(
                AiAssessment.NEEDS_REVIEW, AiAssessment.LIKELY_ANOMALOUS, AiAssessment.INSUFFICIENT_EVIDENCE));

        boolean reviewWhenAiSuggests = true;
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Deduplication {

        boolean enabled = true;

        @NotNull
        Duration retention = Duration.ofHours(24);

        @Min(1)
        int maxTrackedEvents = 10000;
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Delivery {

        boolean logResults = true;
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Security {

        String serviceKey;

        String previousServiceKey;

        boolean requireServiceKey = false;

        public boolean hasServiceKey() {
            return serviceKey != null && !serviceKey.isBlank();
        }

        public boolean hasPreviousServiceKey() {
            return previousServiceKey != null && !previousServiceKey.isBlank();
        }

        @AssertTrue(message = "monitoring.security.service-key must be set when require-service-key is true")
        public boolean isServiceKeyConfigured() {
            return !requireServiceKey || hasServiceKey();
        }

        @AssertTrue(message = "monitoring.security.service-key and previous-service-key must be at least 16 characters when require-service-key is true")
        public boolean isServiceKeyLongEnough() {
            return !requireServiceKey || (!hasServiceKey() || serviceKey.length() >= 16) && (!hasPreviousServiceKey() || previousServiceKey.length() >= 16);
        }
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Rise {

        boolean enabled = true;

        @NotBlank
        String source = "rise-ai-backend";

        @NotEmpty
        List<Integer> supportedSchemaVersions = new ArrayList<>(List.of(1));

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal minimalEvidenceApprovalAmount = new BigDecimal("50000000");

        @Min(1)
        int minimalEvidenceCount = 1;

        @NotNull
        Duration resubmissionWindow = Duration.ofHours(24);

        @Min(2)
        int maxRequestsPerMilestone = 3;

        boolean aiNotesEnabled = false;

        @Min(80)
        int aiNoteMaxLength = 400;

        @NotNull
        Duration aiNoteBudget = Duration.ofSeconds(20);

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        BigDecimal adjustmentReviewAmount = new BigDecimal("10000000");

        @NotNull
        Duration projectionRetention = Duration.ofDays(400);

        @AssertTrue(message = "monitoring.rise durations must be positive")
        public boolean isDurationsPositive() {
            return isPositive(resubmissionWindow) && isPositive(aiNoteBudget) && isPositive(projectionRetention);
        }

        private static boolean isPositive(Duration value) {
            return value != null && !value.isNegative() && !value.isZero();
        }
    }
}
