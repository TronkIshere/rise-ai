package com.riseai.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/** Used only with rise.escrow.engine=avalanche. The default engine is simulated and never contacts a chain gateway. */
@ConfigurationProperties(prefix = "rise.avalanche")
public record AvalancheEscrowProperties(URI gatewayUrl, String internalApiKey, String beneficiaryAddress, Duration releaseDelay,
                                        Duration connectTimeout, Duration readTimeout, Duration retryInterval, Integer batchSize) {
    public AvalancheEscrowProperties {
        gatewayUrl = gatewayUrl == null ? URI.create("http://localhost:8082") : gatewayUrl;
        releaseDelay = releaseDelay == null ? Duration.ofHours(24) : releaseDelay;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        retryInterval = retryInterval == null ? Duration.ofSeconds(15) : retryInterval;
        batchSize = batchSize == null || batchSize < 1 ? 200 : batchSize;
    }
}
