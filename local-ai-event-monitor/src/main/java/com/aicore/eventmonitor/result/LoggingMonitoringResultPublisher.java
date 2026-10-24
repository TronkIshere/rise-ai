package com.aicore.eventmonitor.result;

import com.aicore.eventmonitor.configuration.MonitoringProperties;
import com.aicore.eventmonitor.core.result.MonitoringResult;
import com.aicore.eventmonitor.core.result.MonitoringResultPublisher;
import com.aicore.eventmonitor.core.result.MonitoringStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LoggingMonitoringResultPublisher implements MonitoringResultPublisher {

    private static final String MESSAGE = "Monitoring result eventId={} source={} eventType={} status={} overallRisk={} "
            + "ruleFindings={} aiStatus={} durationMs={}";

    MonitoringProperties properties;

    @Override
    public void publish(MonitoringResult result) {
        if (!properties.getDelivery().isLogResults()) {
            return;
        }
        Object[] arguments = {
                result.getEventId(),
                result.getSource(),
                result.getEventType(),
                result.getMonitoringStatus(),
                result.getOverallRiskLevel(),
                result.getRuleFindings().size(),
                result.getAiAnalysis().getStatus(),
                result.getProcessingDurationMs()
        };
        if (result.getMonitoringStatus() == MonitoringStatus.INCONCLUSIVE) {
            log.warn(MESSAGE, arguments);
        } else {
            log.info(MESSAGE, arguments);
        }
    }
}
