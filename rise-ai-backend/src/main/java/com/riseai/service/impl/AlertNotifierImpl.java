package com.riseai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.configuration.MonitoringRelayProperties;
import com.riseai.service.AlertNotifier;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class AlertNotifierImpl implements AlertNotifier {
    static final int QUEUE_CAPACITY = 100;
    static final int ATTEMPTS = 2;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);

    private final MonitoringRelayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI webhook;
    private final ThreadPoolExecutor worker;
    private final Duration timeout;
    private final Duration retryDelay;
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    @Autowired
    public AlertNotifierImpl(MonitoringRelayProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, DEFAULT_TIMEOUT, DEFAULT_RETRY_DELAY);
    }

    AlertNotifierImpl(MonitoringRelayProperties properties, ObjectMapper objectMapper, Duration timeout, Duration retryDelay) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
        this.retryDelay = retryDelay;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
        this.webhook = resolveWebhook(properties);
        this.worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
            Thread thread = new Thread(runnable, "alert-webhook");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void alert(String kind, Map<String, String> details) {
        Map<String, String> copy = Map.copyOf(details);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(kind, copy);
                }
            });
            return;
        }
        dispatch(kind, copy);
    }

    public record Delivery(long delivered, long failed, long dropped) {
    }

    public Delivery delivery() {
        return new Delivery(delivered.get(), failed.get(), dropped.get());
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    private void dispatch(String kind, Map<String, String> details) {
        log.error("ALERT {} {}", kind, details);
        if (webhook == null) {
            return;
        }
        try {
            worker.execute(() -> deliver(kind, details));
        } catch (RejectedExecutionException ex) {
            dropped.incrementAndGet();
            log.warn("Alert webhook queue is full; {} was logged only", kind);
        }
    }

    private void deliver(String kind, Map<String, String> details) {
        try {
            String body = objectMapper.writeValueAsString(payload(kind, details));
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                if (post(kind, body)) {
                    delivered.incrementAndGet();
                    log.info("Alert webhook delivered {}", kind);
                    return;
                }
                if (attempt == ATTEMPTS) {
                    failed.incrementAndGet();
                    log.warn("Alert webhook gave up on {} after {} attempts; the alert exists only as a log line", kind, ATTEMPTS);
                    return;
                }
                Thread.sleep(retryDelay.toMillis());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            failed.incrementAndGet();
            log.warn("Alert webhook delivery failed for {}: {}", kind, ex.getClass().getSimpleName());
        }
    }

    private boolean post(String kind, String body) throws InterruptedException {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(webhook)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (properties.hasAlertWebhookToken()) {
                request.header("Authorization", "Bearer " + properties.alertWebhookToken());
            }
            HttpResponse<Void> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 == 2) {
                return true;
            }
            log.warn("Alert webhook answered HTTP {} for {}", response.statusCode(), kind);
        } catch (java.io.IOException ex) {
            log.warn("Alert webhook delivery failed for {}: {}", kind, ex.getClass().getSimpleName());
        }
        return false;
    }

    private Map<String, Object> payload(String kind, Map<String, String> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "rise-ai-backend");
        body.put("kind", kind);
        body.put("at", Instant.now().toString());
        body.put("details", details);
        return body;
    }

    private static URI resolveWebhook(MonitoringRelayProperties properties) {
        if (!properties.hasAlertWebhook()) {
            if (properties.hasAlertWebhookToken()) {
                log.warn("Alert webhook token is set without a webhook URL and is ignored");
            }
            return null;
        }
        URI uri;
        try {
            uri = URI.create(properties.alertWebhookUrl().strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("rise.monitoring.alert-webhook-url is not a valid URI");
        }
        boolean http = "http".equals(uri.getScheme());
        if (!(http || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalStateException("rise.monitoring.alert-webhook-url must be an absolute http or https URL without credentials");
        }
        if (properties.hasAlertWebhookToken() && properties.alertWebhookToken().chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            throw new IllegalStateException("rise.monitoring.alert-webhook-token must not contain control characters");
        }
        if (http) {
            log.warn("Alert webhook uses plain http{}; use https outside local testing", properties.hasAlertWebhookToken() ? " and sends a bearer token" : "");
        }
        return uri;
    }
}
