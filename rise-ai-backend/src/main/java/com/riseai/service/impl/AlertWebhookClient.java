package com.riseai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.configuration.MonitoringRelayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class AlertWebhookClient {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private final MonitoringRelayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI webhook;
    private final Duration timeout;

    @Autowired
    public AlertWebhookClient(MonitoringRelayProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, DEFAULT_TIMEOUT);
    }

    AlertWebhookClient(MonitoringRelayProperties properties, ObjectMapper objectMapper, Duration timeout) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
        this.webhook = resolveWebhook(properties);
    }

    public boolean configured() {
        return webhook != null;
    }

    public Delivery post(String kind, Map<String, String> details, Instant at) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(webhook)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload(kind, details, at)), StandardCharsets.UTF_8));
            if (properties.hasAlertWebhookToken()) {
                request.header("Authorization", "Bearer " + properties.alertWebhookToken());
            }
            HttpResponse<Void> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            if (code / 100 == 2) {
                return Delivery.success();
            }
            log.warn("Alert webhook answered HTTP {} for {}", code, kind);
            return Delivery.failure(isTransient(code), "HTTP " + code);
        } catch (IOException ex) {
            log.warn("Alert webhook delivery failed for {}: {}", kind, ex.getClass().getSimpleName());
            return Delivery.failure(true, ex.getClass().getSimpleName());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Delivery.failure(true, "Interrupted");
        }
    }

    private static boolean isTransient(int code) {
        return code >= 500 || code == 408 || code == 429;
    }

    public record Delivery(boolean delivered, boolean retryable, String error) {
        static Delivery success() {
            return new Delivery(true, false, null);
        }

        static Delivery failure(boolean retryable, String error) {
            return new Delivery(false, retryable, error);
        }
    }

    private static Map<String, Object> payload(String kind, Map<String, String> details, Instant at) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "rise-ai-backend");
        body.put("kind", kind);
        body.put("at", at.toString());
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
