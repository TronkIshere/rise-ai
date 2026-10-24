package com.riseai.donation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum DonationStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED,
    REFUND_PENDING,
    PARTIALLY_REFUNDED,
    REFUNDED;

    public static final Set<DonationStatus> COUNTED = Collections.unmodifiableSet(EnumSet.of(CONFIRMED, REFUND_PENDING, PARTIALLY_REFUNDED, REFUNDED));
}
