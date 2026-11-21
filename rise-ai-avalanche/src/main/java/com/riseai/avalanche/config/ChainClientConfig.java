package com.riseai.avalanche.config;

import com.riseai.avalanche.chain.ChainClient;
import com.riseai.avalanche.chain.ChainClients;
import com.riseai.avalanche.env.EnvironmentGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChainClientConfig {

    @Bean(destroyMethod = "shutdown")
    ChainClient chainClient(AvalancheProperties properties) {
        EnvironmentGuard.requireEnvironment(properties.environment());
        return ChainClients.create(properties.environment(), properties.primaryUrl(),
                properties.timeoutMs(), properties.minGapMs(), properties.readOnly());
    }
}
