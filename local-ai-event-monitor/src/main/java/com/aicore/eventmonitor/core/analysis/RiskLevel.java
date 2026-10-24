package com.aicore.eventmonitor.core.analysis;

import lombok.Getter;

@Getter
public enum RiskLevel {

    UNKNOWN(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int rank;

    RiskLevel(int rank) {
        this.rank = rank;
    }

    public static RiskLevel highest(RiskLevel first, RiskLevel second) {
        return second.rank > first.rank ? second : first;
    }
}
