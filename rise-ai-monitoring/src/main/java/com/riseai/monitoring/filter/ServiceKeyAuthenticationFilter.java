package com.riseai.monitoring.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.constants.PlatformConstants;
import com.riseai.monitoring.dto.response.common.ErrorResponse;
import com.riseai.monitoring.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class ServiceKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MonitoringProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.getSecurity().hasServiceKey()) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.startsWith(PlatformConstants.API_PATH_PREFIX) || path.startsWith(PlatformConstants.METRICS_PATH));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (matches(request.getHeader("Authorization"))) {
            filterChain.doFilter(request, response);
            return;
        }
        Object correlationId = request.getAttribute(PlatformConstants.CORRELATION_ID_ATTRIBUTE);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(new Date())
                .status(ErrorCode.UNAUTHENTICATED.getCode())
                .error(ErrorCode.UNAUTHENTICATED.formatMessage())
                .path(request.getRequestURI())
                .correlationId(correlationId instanceof String value ? value : null)
                .build();
        response.setStatus(ErrorCode.UNAUTHENTICATED.getHttpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }

    private boolean matches(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] presented = header.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        byte[] expected = properties.getSecurity().getServiceKey().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presented, expected);
    }
}
