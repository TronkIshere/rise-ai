package com.riseai.configuration;

import com.riseai.service.AlertDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class AlertDispatchScheduler {
    private final AlertDispatcher alertDispatcher;

    @Scheduled(fixedDelayString = "${rise.monitoring.alert-dispatch-interval:PT10S}", initialDelayString = "${rise.monitoring.alert-dispatch-initial-delay:PT10S}")
    public void dispatch() {
        int sent = alertDispatcher.dispatchDue();
        if (sent > 0) {
            log.info("Delivered {} alert(s) to the webhook", sent);
        }
    }

    @Scheduled(cron = "${rise.monitoring.purge-cron:0 30 3 * * *}")
    public void purgeSent() {
        int deleted = alertDispatcher.purgeSent();
        if (deleted > 0) {
            log.info("Purged {} delivered alerts past retention", deleted);
        }
    }
}
