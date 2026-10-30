package com.riseai.monitoring.rise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.finding.Finding;
import com.riseai.monitoring.exception.ApplicationException;
import com.riseai.monitoring.exception.ErrorCode;
import com.riseai.monitoring.observability.MonitoringMetrics;
import com.riseai.monitoring.testsupport.MutableClock;
import com.riseai.monitoring.testsupport.TestEvents;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiseFindingEngineFailureTest {
    private final MonitoringProperties properties = TestEvents.properties();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void aFailingRuleFailsTheEventLoudlyAfterEveryOtherRuleHasRun() throws Exception {
        AtomicInteger healthyEvaluations = new AtomicInteger();
        RiseFindingEngine engine = new RiseFindingEngine(new RiseEnvelopeReader(properties), null,
                List.of(rule("rise.test.broken", () -> {
                    throw new IllegalStateException("rule bug");
                }), rule("rise.test.healthy", () -> {
                    healthyEvaluations.incrementAndGet();
                    return List.of();
                })), properties, new MonitoringMetrics(meterRegistry), new MutableClock(TestEvents.NOW));

        assertThatThrownBy(() -> engine.evaluate(riseEvent()))
                .isInstanceOfSatisfying(ApplicationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RULE_EVALUATION_FAILED);
                    assertThat(ex.getMessage()).contains("rise.test.broken").doesNotContain("rise.test.healthy");
                });

        assertThat(healthyEvaluations).hasValue(1);
        assertThat(meterRegistry.get(MonitoringMetrics.RISE_RULE_FAILURES).tag("rule", "rise.test.broken").counter().count()).isEqualTo(1.0);
    }

    private MonitoringEvent riseEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String envelope = "{\"schemaVersion\":1,\"eventId\":\"" + eventId + "\",\"eventType\":\"RECONCILIATION_RUN_COMPLETED\","
                + "\"occurredAt\":\"2026-10-23T11:00:00Z\",\"aggregateType\":\"RECONCILIATION_RUN\",\"aggregateId\":\"" + UUID.randomUUID()
                + "\",\"payload\":{}}";
        return TestEvents.event()
                .eventId(eventId)
                .eventType("reconciliation_run_completed")
                .source(properties.getRise().getSource())
                .payload(new ObjectMapper().readTree(envelope))
                .build();
    }

    private static RiseFindingRule rule(String id, java.util.function.Supplier<List<Finding>> evaluation) {
        return new RiseFindingRule() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getVersion() {
                return "1";
            }

            @Override
            public String getDescription() {
                return id;
            }

            @Override
            public List<Finding> evaluate(RiseEnvelope envelope) {
                return evaluation.get();
            }
        };
    }
}
