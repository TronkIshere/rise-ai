package com.riseai.monitoring.analysis;

import com.riseai.monitoring.core.analysis.RiskLevel;
import com.riseai.monitoring.core.event.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskLevelNormalizerTest {

    private final RiskLevelNormalizer normalizer = new RiskLevelNormalizer();

    @Test
    void mapsEverySeverityToARiskLevel() {
        assertThat(normalizer.fromSeverity(Severity.INFO)).isEqualTo(RiskLevel.LOW);
        assertThat(normalizer.fromSeverity(Severity.LOW)).isEqualTo(RiskLevel.LOW);
        assertThat(normalizer.fromSeverity(Severity.MEDIUM)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(normalizer.fromSeverity(Severity.HIGH)).isEqualTo(RiskLevel.HIGH);
        assertThat(normalizer.fromSeverity(Severity.CRITICAL)).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void highestIgnoresUnknown() {
        assertThat(RiskLevel.highest(RiskLevel.UNKNOWN, RiskLevel.LOW)).isEqualTo(RiskLevel.LOW);
        assertThat(RiskLevel.highest(RiskLevel.HIGH, RiskLevel.MEDIUM)).isEqualTo(RiskLevel.HIGH);
    }
}
