package com.riseai.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum FindingReviewStatus {
    OPEN,
    ACKNOWLEDGED,
    CONCERN_CONFIRMED,
    DISMISSED;

    public static final Set<FindingReviewStatus> REVIEW_TARGETS = Collections.unmodifiableSet(EnumSet.of(ACKNOWLEDGED, CONCERN_CONFIRMED, DISMISSED));

    public boolean canMoveTo(FindingReviewStatus target) {
        return switch (this) {
            case OPEN -> REVIEW_TARGETS.contains(target);
            case ACKNOWLEDGED -> target == CONCERN_CONFIRMED || target == DISMISSED;
            case CONCERN_CONFIRMED, DISMISSED -> false;
        };
    }
}
