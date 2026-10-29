package com.riseai.service.impl;

import com.riseai.client.MonitoringClient;
import com.riseai.client.MonitoringClient.DeliveryResult;
import com.riseai.configuration.MonitoringRelayProperties;
import com.riseai.entity.OutboxEvent;
import com.riseai.enums.OutboxStatus;
import com.riseai.exception.FindingContractException;
import com.riseai.repository.OutboxEventRepository;
import com.riseai.service.FindingIngestionService;
import com.riseai.service.AlertNotifier;
import com.riseai.service.OutboxRelay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class OutboxRelayImpl implements OutboxRelay {
    private static final int MAX_ERROR = 500;

    private final OutboxEventRepository outboxEventRepository;
    private final MonitoringClient monitoringClient;
    private final FindingIngestionService findingIngestionService;
    private final MonitoringRelayProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final AlertNotifier alertNotifier;

    public OutboxRelayImpl(OutboxEventRepository outboxEventRepository, MonitoringClient monitoringClient,
                           FindingIngestionService findingIngestionService, MonitoringRelayProperties properties,
                           PlatformTransactionManager transactionManager, AlertNotifier alertNotifier) {
        this.outboxEventRepository = outboxEventRepository;
        this.monitoringClient = monitoringClient;
        this.findingIngestionService = findingIngestionService;
        this.properties = properties;
        this.alertNotifier = alertNotifier;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public RelayRun relayOnce() {
        List<UUID> claimed = claim();
        int published = 0;
        int retried = 0;
        int failed = 0;
        for (UUID id : claimed) {
            switch (deliver(id)) {
                case PUBLISHED -> published++;
                case FAILED -> failed++;
                default -> retried++;
            }
        }
        if (!claimed.isEmpty()) {
            log.info("Outbox relay run: claimed={} published={} retried={} failed={}", claimed.size(), published, retried, failed);
        }
        return new RelayRun(claimed.size(), published, retried, failed);
    }

    @Override
    public int purgePublished() {
        Instant before = Instant.now().minus(properties.publishedRetention());
        Integer deleted = transactionTemplate.execute(status -> outboxEventRepository.deletePublishedBefore(before));
        return deleted == null ? 0 : deleted;
    }

    private List<UUID> claim() {
        List<UUID> ids = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<UUID> claimed = new ArrayList<>();
            for (String id : outboxEventRepository.lockDueIds(now, properties.batchSize())) {
                OutboxEvent event = outboxEventRepository.findById(UUID.fromString(id)).orElseThrow();
                event.setStatus(OutboxStatus.IN_FLIGHT);
                event.setLeaseUntil(now.plus(properties.lease()));
                event.setAttempts(event.getAttempts() + 1);
                claimed.add(event.getId());
            }
            return claimed;
        });
        return ids == null ? List.of() : ids;
    }

    private OutboxStatus deliver(UUID id) {
        OutboxEvent snapshot = outboxEventRepository.findById(id).orElse(null);
        if (snapshot == null) {
            return OutboxStatus.FAILED;
        }
        DeliveryResult result = monitoringClient.deliver(snapshot);
        try {
            return transactionTemplate.execute(status -> applyOutcome(id, result));
        } catch (FindingContractException ex) {
            log.error("Monitoring findings for event {} violate the finding contract: {}", snapshot.getEventId(), ex.getMessage());
            return transactionTemplate.execute(status -> markFailed(id, "Invalid finding contract: " + ex.getMessage()));
        } catch (RuntimeException ex) {
            log.error("Recording relay outcome for event {} failed; the lease will expire and the event will be retried",
                    snapshot.getEventId(), ex);
            return OutboxStatus.IN_FLIGHT;
        }
    }

    private OutboxStatus applyOutcome(UUID id, DeliveryResult result) {
        OutboxEvent event = outboxEventRepository.findById(id).orElseThrow();
        switch (result.outcome()) {
            case DELIVERED -> {
                int stored = findingIngestionService.ingest(event, result.data().path("findings"));
                String monitoringStatus = result.data().path("monitoringStatus").asText("");
                if ("REJECTED".equals(monitoringStatus)) {
                    log.warn("Monitoring service rejected event {} ({}); check the event contract", event.getEventId(), event.getEventType());
                }
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLeaseUntil(null);
                event.setLastError("REJECTED".equals(monitoringStatus) ? "Monitoring status REJECTED" : null);
                if (stored > 0) {
                    log.info("Stored {} monitoring finding occurrence(s) for event {}", stored, event.getEventId());
                }
                return OutboxStatus.PUBLISHED;
            }
            case RETRY -> {
                if (event.getAttempts() >= properties.maxAttempts()) {
                    return fail(event, "Attempts exhausted; last error: " + result.error());
                }
                event.setStatus(OutboxStatus.PENDING);
                event.setLeaseUntil(null);
                event.setNextAttemptAt(Instant.now().plus(backoff(event.getAttempts())));
                event.setLastError(truncate(result.error()));
                return OutboxStatus.PENDING;
            }
            default -> {
                return fail(event, result.error());
            }
        }
    }

    private OutboxStatus markFailed(UUID id, String error) {
        return fail(outboxEventRepository.findById(id).orElseThrow(), error);
    }

    private OutboxStatus fail(OutboxEvent event, String error) {
        event.setStatus(OutboxStatus.FAILED);
        event.setLeaseUntil(null);
        event.setLastError(truncate(error));
        alertNotifier.alert("OUTBOX_EVENT_FAILED", Map.of("eventId", String.valueOf(event.getEventId()), "eventType", event.getEventType(),
                "error", String.valueOf(event.getLastError())));
        return OutboxStatus.FAILED;
    }

    Duration backoff(int attempts) {
        long base = properties.initialBackoff().toMillis() * (1L << Math.min(Math.max(attempts - 1, 0), 20));
        long capped = Math.min(base, properties.maxBackoff().toMillis());
        long jitter = capped / 5 == 0 ? 0 : ThreadLocalRandom.current().nextLong(-capped / 5, capped / 5 + 1);
        return Duration.ofMillis(Math.max(capped + jitter, 0));
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_ERROR ? value.substring(0, MAX_ERROR) : value;
    }
}
