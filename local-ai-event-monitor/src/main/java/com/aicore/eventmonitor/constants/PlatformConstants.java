package com.aicore.eventmonitor.constants;

import java.util.regex.Pattern;

public final class PlatformConstants {

    private PlatformConstants() {
    }

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    public static final String CORRELATION_ID_ATTRIBUTE = "eventmonitor.correlationId";

    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    public static final String EVENT_ID_MDC_KEY = "eventId";

    public static final String CORRELATION_ID_REGEX = "^[A-Za-z0-9._:-]{1,64}$";

    public static final Pattern CORRELATION_ID_PATTERN = Pattern.compile(CORRELATION_ID_REGEX);

    public static final String IDENTIFIER_REGEX = "^[A-Za-z0-9._:-]+$";

    public static final String EVENT_API_PATH = "/api/v1/events";
}
