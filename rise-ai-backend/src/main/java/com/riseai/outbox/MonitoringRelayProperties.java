package com.riseai.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "rise.monitoring")
public record MonitoringRelayProperties(boolean enabled, URI baseUrl, String serviceKey, Integer batchSize, Duration lease,
                                        Duration connectTimeout, Duration readTimeout, Integer maxAttempts,
                                        Duration initialBackoff, Duration maxBackoff, Duration publishedRetention) {
    public MonitoringRelayProperties {
        baseUrl = baseUrl == null ? URI.create("http://localhost:8081") : baseUrl;
        batchSize = batchSize == null || batchSize < 1 ? 20 : batchSize;
        lease = lease == null ? Duration.ofSeconds(120) : lease;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(90) : readTimeout;
        maxAttempts = maxAttempts == null || maxAttempts < 1 ? 12 : maxAttempts;
        initialBackoff = initialBackoff == null ? Duration.ofSeconds(5) : initialBackoff;
        maxBackoff = maxBackoff == null ? Duration.ofMinutes(15) : maxBackoff;
        publishedRetention = publishedRetention == null ? Duration.ofDays(30) : publishedRetention;
    }

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
