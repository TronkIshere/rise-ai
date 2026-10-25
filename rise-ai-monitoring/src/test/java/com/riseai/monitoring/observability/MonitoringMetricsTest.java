package com.riseai.monitoring.observability;

import com.riseai.monitoring.core.analysis.AiAnalysis;
import com.riseai.monitoring.core.analysis.AnalysisStatus;
import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.result.MonitoringResult;
import com.riseai.monitoring.core.result.MonitoringStatus;
import com.riseai.monitoring.core.rule.RuleFinding;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MonitoringMetrics metrics = new MonitoringMetrics(meterRegistry);

    @Test
    void countsLifecycleEvents() {
        metrics.eventReceived();
        metrics.eventReceived();
        metrics.duplicateEvent("replayed");
        metrics.aiAnalysisAttempted();
        metrics.aiAnalysisFinished(AnalysisStatus.TIMEOUT);

        assertThat(meterRegistry.get(MonitoringMetrics.EVENTS_RECEIVED).counter().count()).isEqualTo(2.0);
        assertThat(meterRegistry.get(MonitoringMetrics.EVENTS_DUPLICATE).tag("outcome", "replayed").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MonitoringMetrics.AI_ATTEMPTED).counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MonitoringMetrics.AI_FINISHED).tag("status", "timeout").counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordsRuleFindingsRejectionsAndProcessingTime() {
        metrics.resultProduced(MonitoringResult.builder()
                .eventId("evt-1")
                .monitoringStatus(MonitoringStatus.REJECTED)
                .ruleFindings(List.of(RuleFinding.reject("timestamp-validity", "future"),
                        RuleFinding.flag("attribute-threshold", Severity.HIGH, "above")))
                .aiAnalysis(AiAnalysis.skipped("rejected"))
                .processingDurationMs(7)
                .build());

        assertThat(meterRegistry.get(MonitoringMetrics.EVENTS_REJECTED).tag("stage", "rules").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MonitoringMetrics.RULE_FINDINGS).tag("rule", "timestamp-validity").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MonitoringMetrics.PROCESSING).tag("status", "rejected").timer().count()).isEqualTo(1);
    }
}
