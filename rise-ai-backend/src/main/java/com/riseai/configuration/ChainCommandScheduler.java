package com.riseai.configuration;

import com.riseai.service.impl.ChainCommandRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "rise.escrow", name = "engine", havingValue = "avalanche")
@RequiredArgsConstructor
public class ChainCommandScheduler {
    private final ChainCommandRelay relay;

    @Scheduled(fixedDelayString = "${rise.avalanche.relay-interval:PT2S}", initialDelayString = "${rise.avalanche.relay-initial-delay:PT10S}")
    public void relay() {
        relay.relayOnce();
    }
}
