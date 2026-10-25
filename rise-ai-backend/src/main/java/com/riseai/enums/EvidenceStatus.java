package com.riseai.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum EvidenceStatus {
    SUBMITTED,
    ACCEPTED,
    REJECTED;

    public static final Set<EvidenceStatus> VALID = Collections.unmodifiableSet(EnumSet.of(SUBMITTED, ACCEPTED));
}
