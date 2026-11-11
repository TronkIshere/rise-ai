package com.riseai.enums;

import java.util.List;

public enum ChainCommandStatus {
    PENDING,
    SUBMITTED,
    CONFIRMED,
    FAILED,
    CANCELLED;

    public static final List<ChainCommandStatus> OPEN = List.of(PENDING, SUBMITTED);
}
