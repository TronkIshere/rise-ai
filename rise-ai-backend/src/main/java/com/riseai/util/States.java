package com.riseai.util;

import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;

import java.util.Set;

public final class States {
    private States() {
    }

    public static <E extends Enum<E>> void require(E current, Set<E> allowed) {
        if (!allowed.contains(current)) {
            throw invalid(current);
        }
    }

    @SafeVarargs
    public static <E extends Enum<E>> void require(E current, E... allowed) {
        for (E candidate : allowed) {
            if (candidate == current) {
                return;
            }
        }
        throw invalid(current);
    }

    private static ApplicationException invalid(Object current) {
        return new ApplicationException(ErrorCode.INVALID_STATE_TRANSITION, "Operation is not allowed while status is " + current);
    }
}
