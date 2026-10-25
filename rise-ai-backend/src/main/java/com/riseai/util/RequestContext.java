package com.riseai.util;

import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestContext {
    public static final String CORRELATION_ID = "correlationId";

    private RequestContext() {
    }

    public static String correlationId() {
        return MDC.get(CORRELATION_ID);
    }

    public static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
