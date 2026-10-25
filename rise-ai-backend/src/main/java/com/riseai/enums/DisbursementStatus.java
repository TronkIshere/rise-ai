package com.riseai.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum DisbursementStatus {
    SUBMITTED,
    UNDER_REVIEW,
    NEEDS_MORE_EVIDENCE,
    APPROVED,
    REJECTED,
    RELEASED,
    CANCELLED,
    DISPUTED;

    public static final Set<DisbursementStatus> OPEN = Collections.unmodifiableSet(EnumSet.of(SUBMITTED, UNDER_REVIEW, NEEDS_MORE_EVIDENCE, APPROVED, DISPUTED));
    public static final Set<DisbursementStatus> HOLDABLE = Collections.unmodifiableSet(EnumSet.of(SUBMITTED, UNDER_REVIEW, NEEDS_MORE_EVIDENCE, APPROVED));
}
