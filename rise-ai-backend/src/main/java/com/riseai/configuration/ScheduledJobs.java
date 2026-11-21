package com.riseai.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import com.riseai.enums.ReconciliationTrigger;
import com.riseai.service.FundraisingCloseService;
import com.riseai.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@ConditionalOnExpression("!${rise.avalanche.read-only:false}")
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "rise.jobs", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ScheduledJobs {
    private final ReconciliationService reconciliationService;
    private final FundraisingCloseService fundraisingCloseService;

    @Scheduled(cron = "${rise.jobs.reconciliation-cron}")
    public void reconcileEscrow() {
        reconciliationService.run(ReconciliationTrigger.SCHEDULED, null);
    }

    @Scheduled(cron = "${rise.jobs.fundraising-close-cron}")
    public void closeEndedFundraising() {
        Instant now = Instant.now();
        for (UUID campaignId : fundraisingCloseService.findDue(now)) {
            try {
                if (fundraisingCloseService.close(campaignId, now)) {
                    log.info("Fundraising closed for campaign {}", campaignId);
                }
            } catch (RuntimeException ex) {
                log.error("Closing fundraising failed for campaign {}", campaignId, ex);
            }
        }
    }
}
