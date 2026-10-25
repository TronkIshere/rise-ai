package com.riseai.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum DisputeStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED_UPHELD,
    RESOLVED_DISMISSED,
    WITHDRAWN;

    public static final Set<DisputeStatus> OPEN_STATES = Collections.unmodifiableSet(EnumSet.of(OPEN, UNDER_REVIEW));
}
