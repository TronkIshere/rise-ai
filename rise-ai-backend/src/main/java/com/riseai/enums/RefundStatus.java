package com.riseai.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum RefundStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    CANCELLED,
    RECORDED;

    public static final Set<RefundStatus> OPEN = Collections.unmodifiableSet(EnumSet.of(REQUESTED, APPROVED));
}
