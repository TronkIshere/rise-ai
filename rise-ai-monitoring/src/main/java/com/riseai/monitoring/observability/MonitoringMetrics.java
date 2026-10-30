package com.riseai.monitoring.observability;

import com.riseai.monitoring.core.analysis.AnalysisStatus;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.core.result.MonitoringResult;
import com.riseai.monitoring.core.result.MonitoringStatus;
import com.riseai.monitoring.core.rule.RuleFinding;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MonitoringMetrics {

    public static final String EVENTS_RECEIVED = "riseai.monitoring.events.received";
    public static final String EVENTS_REJECTED = "riseai.monitoring.events.rejected";
    public static final String EVENTS_DUPLICATE = "riseai.monitoring.events.duplicates";
    public static final String RULE_FINDINGS = "riseai.monitoring.rule.findings";
    public static final String AI_ATTEMPTED = "riseai.monitoring.ai.analyses.attempted";
    public static final String AI_FINISHED = "riseai.monitoring.ai.analyses";
    public static final String PROCESSING = "riseai.monitoring.processing";
    public static final String FINDINGS = "riseai.monitoring.findings";
    public static final String RISE_RULE_FAILURES = "riseai.monitoring.rise.rule.failures";

    MeterRegistry meterRegistry;

    public void findingProduced(Finding finding) {
        Counter.builder(FINDINGS).description("Charity monitoring findings produced")
                .tag("rule", finding.getRuleId())
                .tag("severity", finding.getSeverity().name().toLowerCase(Locale.ROOT))
                .register(meterRegistry).increment();
    }

    public void riseRuleFailed(String ruleId) {
        Counter.builder(RISE_RULE_FAILURES).description("Rise finding rules that threw while evaluating an event")
                .tag("rule", ruleId).register(meterRegistry).increment();
    }

    public void eventReceived() {
        Counter.builder(EVENTS_RECEIVED).description("Events received for monitoring").register(meterRegistry).increment();
    }

    public void eventRejected(String stage) {
        Counter.builder(EVENTS_REJECTED).description("Events rejected by validation or rules")
                .tag("stage", stage).register(meterRegistry).increment();
    }

    public void duplicateEvent(String outcome) {
        Counter.builder(EVENTS_DUPLICATE).description("Events whose identifier was already received")
                .tag("outcome", outcome).register(meterRegistry).increment();
    }

    public void aiAnalysisAttempted() {
        Counter.builder(AI_ATTEMPTED).description("AI analyses attempted").register(meterRegistry).increment();
    }

    public void aiAnalysisFinished(AnalysisStatus status) {
        Counter.builder(AI_FINISHED).description("AI analyses by outcome")
                .tag("status", status.name().toLowerCase(Locale.ROOT)).register(meterRegistry).increment();
    }

    public void resultProduced(MonitoringResult result) {
        if (result.getMonitoringStatus() == MonitoringStatus.REJECTED) {
            eventRejected("rules");
        }
        for (RuleFinding finding : result.getRuleFindings()) {
            Counter.builder(RULE_FINDINGS).description("Deterministic rule findings")
                    .tag("rule", finding.getRuleId())
                    .tag("effect", finding.getEffect().name().toLowerCase(Locale.ROOT))
                    .register(meterRegistry).increment();
        }
        Timer.builder(PROCESSING).description("Event monitoring processing duration")
                .tag("status", result.getMonitoringStatus().name().toLowerCase(Locale.ROOT))
                .register(meterRegistry)
                .record(Duration.ofMillis(result.getProcessingDurationMs()));
    }
}
