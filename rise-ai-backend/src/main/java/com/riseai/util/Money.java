package com.riseai.util;

import com.riseai.exception.ApplicationException;
import com.riseai.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

public final class Money {
    public static final int SCALE = 2;

    private Money() {
    }

    public static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal sum(Stream<BigDecimal> values) {
        return values.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Amount must be positive");
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Amount supports at most 2 decimal places");
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static String requireCurrency(String requested, String supported) {
        String currency = requested == null || requested.isBlank() ? supported : requested.trim().toUpperCase(Locale.ROOT);
        if (!currency.equals(supported)) {
            throw new ApplicationException(ErrorCode.UNSUPPORTED_CURRENCY, "Only " + supported + " is supported in Phase 0");
        }
        return currency;
    }

    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return orZero(part).multiply(BigDecimal.valueOf(100)).divide(whole, SCALE, RoundingMode.HALF_UP);
    }
}
