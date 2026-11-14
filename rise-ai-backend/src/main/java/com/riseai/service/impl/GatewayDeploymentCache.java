package com.riseai.service.impl;

import com.riseai.client.AvalancheGatewayClient;
import com.riseai.client.AvalancheGatewayClient.DeploymentInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** The gateway's verified deployment, cached for a minute. Null when the engine is simulated or the gateway cannot answer. */
@Component
public class GatewayDeploymentCache {
    private static final Duration TTL = Duration.ofMinutes(1);

    private final AvalancheGatewayClient gateway;
    private volatile Cached cached;

    private record Cached(DeploymentInfo info, Instant fetchedAt) {
    }

    public GatewayDeploymentCache(ObjectProvider<AvalancheGatewayClient> gateway) {
        this.gateway = gateway.getIfAvailable();
    }

    public DeploymentInfo get() {
        if (gateway == null) {
            return null;
        }
        Cached current = cached;
        if (current != null && current.fetchedAt().plus(TTL).isAfter(Instant.now())) {
            return current.info();
        }
        try {
            DeploymentInfo info = gateway.deployment();
            cached = new Cached(info, Instant.now());
            return info;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
