package com.riseai.service.impl;

import com.riseai.configuration.MonitoringRelayProperties;
import com.riseai.entity.AlertNotification;
import com.riseai.enums.AlertStatus;
import com.riseai.repository.AlertNotificationRepository;
import com.riseai.service.AlertDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class AlertDispatcherImpl implements AlertDispatcher {
    private static final String EXHAUSTED = "Attempts exhausted";
    private static final int MAX_ERROR = 200;

    private final AlertNotificationRepository repository;
    private final AlertWebhookClient webhookClient;
    private final MonitoringRelayProperties properties;
    private final TransactionTemplate transactionTemplate;

    public AlertDispatcherImpl(AlertNotificationRepository repository, AlertWebhookClient webhookClient,
                               MonitoringRelayProperties properties, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.webhookClient = webhookClient;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public int dispatchDue() {
        if (!webhookClient.configured()) {
            return 0;
        }
        int sent = 0;
        for (UUID id : claim()) {
            if (deliver(id)) {
                sent++;
            }
        }
        return sent;
    }

    @Override
    public int purgeSent() {
        Instant before = Instant.now().minus(properties.publishedRetention());
        Integer deleted = transactionTemplate.execute(status -> repository.deleteSentBefore(before));
        return deleted == null ? 0 : deleted;
    }

    private List<UUID> claim() {
        List<UUID> claimed = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            List<UUID> ids = new ArrayList<>();
            for (String id : repository.lockDueIds(now, properties.batchSize())) {
                AlertNotification alert = repository.findById(UUID.fromString(id)).orElseThrow();
                if (alert.getAttempts() >= properties.maxAttempts()) {
                    markFailed(alert, now, EXHAUSTED + "; last error: " + alert.getLastError());
                    continue;
                }
                alert.setAttempts(alert.getAttempts() + 1);
                alert.setNextAttemptAt(now.plus(properties.lease()));
                ids.add(alert.getId());
            }
            return ids;
        });
        return claimed == null ? List.of() : claimed;
    }

    private boolean deliver(UUID id) {
        AlertNotification snapshot = repository.findById(id).orElse(null);
        if (snapshot == null) {
            return false;
        }
        AlertWebhookClient.Delivery delivery = webhookClient.post(snapshot.getKind(), snapshot.getDetails(), snapshot.getCreatedAt());
        transactionTemplate.executeWithoutResult(status -> record(id, delivery));
        return delivery.delivered();
    }

    private void record(UUID id, AlertWebhookClient.Delivery delivery) {
        AlertNotification alert = repository.findById(id).orElseThrow();
        if (alert.getStatus() == AlertStatus.SENT || alert.getStatus() == AlertStatus.FAILED) {
            return;
        }
        Instant now = Instant.now();
        if (delivery.delivered()) {
            alert.setStatus(AlertStatus.SENT);
            alert.setDeliveredAt(now);
            alert.setLastError(null);
            return;
        }
        if (!delivery.retryable() || alert.getAttempts() >= properties.maxAttempts()) {
            markFailed(alert, now, delivery.error());
            return;
        }
        alert.setStatus(AlertStatus.RETRYING);
        alert.setLastError(truncate(delivery.error()));
        alert.setNextAttemptAt(now.plus(backoff(alert.getAttempts())));
    }

    private void markFailed(AlertNotification alert, Instant now, String error) {
        alert.setStatus(AlertStatus.FAILED);
        alert.setFailedAt(now);
        alert.setLastError(truncate(error));
        log.error("Alert {} {} was not delivered after {} attempt(s) ({}) and stays in alert_notifications as FAILED",
                alert.getKind(), alert.getId(), alert.getAttempts(), alert.getLastError());
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_ERROR ? value : value.substring(0, MAX_ERROR);
    }

    Duration backoff(int attempts) {
        long base = properties.initialBackoff().toMillis() * (1L << Math.min(Math.max(attempts - 1, 0), 20));
        return Duration.ofMillis(Math.min(base, properties.maxBackoff().toMillis()));
    }
}
