package com.riseai.monitoring.filter;

import com.riseai.monitoring.constants.PlatformConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(PlatformConstants.CORRELATION_ID_HEADER));
        request.setAttribute(PlatformConstants.CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader(PlatformConstants.CORRELATION_ID_HEADER, correlationId);
        MDC.put(PlatformConstants.CORRELATION_ID_MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(PlatformConstants.CORRELATION_ID_MDC_KEY);
        }
    }

    private String resolveCorrelationId(String headerValue) {
        if (headerValue != null && PlatformConstants.CORRELATION_ID_PATTERN.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }
}
