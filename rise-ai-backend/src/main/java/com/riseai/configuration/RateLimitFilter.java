package com.riseai.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.dto.response.ApiError;
import com.riseai.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String AUTH_PREFIX = "/api/v1/auth/";

    private final HardeningProperties properties;
    private final ObjectMapper objectMapper;
    private final FixedWindowRateLimiter limiter;

    public RateLimitFilter(HardeningProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.limiter = new FixedWindowRateLimiter(properties.rateLimitWindow());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.rateLimitEnabled() || !MUTATING.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String client = request.getRemoteAddr();
        long now = System.nanoTime();
        boolean allowed = request.getRequestURI().startsWith(AUTH_PREFIX)
                ? limiter.tryAcquire("auth|" + client, properties.authRequestsPerWindow(), now)
                : limiter.tryAcquire("mutation|" + client, properties.mutationRequestsPerWindow(), now);
        if (allowed) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(ErrorCode.RATE_LIMITED.getHttpStatus().value());
        response.setHeader("Retry-After", Long.toString(limiter.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.getMessage(), request.getRequestURI(), null));
    }
}
