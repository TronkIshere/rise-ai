package com.riseai.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import com.riseai.service.impl.ChainAnchorRelay;
import com.riseai.service.impl.ChainCommandRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@ConditionalOnExpression("!${rise.avalanche.read-only:false}")
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "rise.escrow", name = "engine", havingValue = "avalanche")
@RequiredArgsConstructor
public class ChainCommandScheduler {
    private final ChainCommandRelay relay;
    private final ChainAnchorRelay anchors;

    @Scheduled(fixedDelayString = "${rise.avalanche.relay-interval:PT2S}", initialDelayString = "${rise.avalanche.relay-initial-delay:PT10S}")
    public void relay() {
        relay.relayOnce();
    }

    @Scheduled(fixedDelayString = "${rise.avalanche.anchor-interval:PT5M}", initialDelayString = "${rise.avalanche.anchor-initial-delay:PT1M}")
    public void anchor() {
        anchors.anchorOnce();
    }
}
