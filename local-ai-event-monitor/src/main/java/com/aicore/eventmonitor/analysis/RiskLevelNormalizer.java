package com.aicore.eventmonitor.analysis;

import com.aicore.eventmonitor.core.analysis.RiskLevel;
import com.aicore.eventmonitor.core.event.Severity;
import org.springframework.stereotype.Component;

@Component
public class RiskLevelNormalizer {

    public RiskLevel fromSeverity(Severity severity) {
        return switch (severity) {
            case INFO, LOW -> RiskLevel.LOW;
            case MEDIUM -> RiskLevel.MEDIUM;
            case HIGH -> RiskLevel.HIGH;
            case CRITICAL -> RiskLevel.CRITICAL;
        };
    }
}
