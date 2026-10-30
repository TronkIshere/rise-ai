package com.riseai.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties(prefix = "rise.hardening")
public record HardeningProperties(Boolean rateLimitEnabled, Integer authRequestsPerWindow, Integer mutationRequestsPerWindow,
                                  Duration rateLimitWindow, DataSize maxRequestBody) {
    public HardeningProperties {
        rateLimitEnabled = rateLimitEnabled == null || rateLimitEnabled;
        authRequestsPerWindow = authRequestsPerWindow == null || authRequestsPerWindow < 1 ? 20 : authRequestsPerWindow;
        mutationRequestsPerWindow = mutationRequestsPerWindow == null || mutationRequestsPerWindow < 1 ? 300 : mutationRequestsPerWindow;
        rateLimitWindow = rateLimitWindow == null || rateLimitWindow.isZero() || rateLimitWindow.isNegative() ? Duration.ofMinutes(1) : rateLimitWindow;
        maxRequestBody = maxRequestBody == null || maxRequestBody.toBytes() < 1 ? DataSize.ofMegabytes(1) : maxRequestBody;
    }
}
