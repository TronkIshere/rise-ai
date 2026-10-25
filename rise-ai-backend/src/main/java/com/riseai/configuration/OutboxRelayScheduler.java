package com.riseai.configuration;

import com.riseai.service.OutboxRelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "rise.monitoring", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OutboxRelayScheduler {
    private final OutboxRelay outboxRelay;

    @Scheduled(fixedDelayString = "${rise.monitoring.relay-interval:PT5S}", initialDelayString = "${rise.monitoring.relay-initial-delay:PT10S}")
    public void relay() {
        try {
            outboxRelay.relayOnce();
        } catch (RuntimeException ex) {
            log.error("Outbox relay run failed", ex);
        }
    }

    @Scheduled(cron = "${rise.monitoring.purge-cron:0 30 3 * * *}")
    public void purgePublished() {
        int deleted = outboxRelay.purgePublished();
        if (deleted > 0) {
            log.info("Purged {} published outbox events past retention", deleted);
        }
    }
}
