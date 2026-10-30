package com.riseai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.configuration.MonitoringRelayProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertWebhookClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofMillis(300);
    private static final Instant AT = Instant.parse("2026-10-30T10:15:30Z");

    private HttpServer server;
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final AtomicInteger redirectTargetHits = new AtomicInteger();
    private volatile int status = 200;
    private volatile CountDownLatch gate;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/alerts", exchange -> {
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            bodies.add(MAPPER.readTree(exchange.getRequestBody()));
            CountDownLatch latch = gate;
            if (latch != null) {
                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            redirectTargetHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        if (gate != null) {
            gate.countDown();
        }
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static AlertWebhookClient client(String url, String token) {
        MonitoringRelayProperties properties = new MonitoringRelayProperties(true, null, "key", null, null, null, null, null, null, null, null, url, token);
        return new AlertWebhookClient(properties, MAPPER, TIMEOUT);
    }

    @Test
    void deliversKindDetailsAndTimestampWithTheBearerToken() {
        AlertWebhookClient.Delivery delivery = client(url("/alerts"), "secret-token").post("CRITICAL_FINDING", Map.of("ruleId", "r1"), AT);

        assertThat(delivery.delivered()).isTrue();
        assertThat(delivery.error()).isNull();
        assertThat(bodies.get(0).path("source").asText()).isEqualTo("rise-ai-backend");
        assertThat(bodies.get(0).path("kind").asText()).isEqualTo("CRITICAL_FINDING");
        assertThat(bodies.get(0).path("at").asText()).isEqualTo(AT.toString());
        assertThat(bodies.get(0).path("details").path("ruleId").asText()).isEqualTo("r1");
        assertThat(bodies.get(0).toString()).doesNotContain("secret-token");
        assertThat(authorizations).containsExactly("Bearer secret-token");
    }

    @Test
    void withoutATokenNoAuthorizationHeaderIsSent() {
        client(url("/alerts"), null).post("OUTBOX_EVENT_FAILED", Map.of("eventId", "e1"), AT);

        assertThat(authorizations).containsExactly("null");
    }

    @Test
    void aServerErrorIsATransientFailureThatKeepsTheStatusCode() {
        status = 500;

        AlertWebhookClient.Delivery delivery = client(url("/alerts"), null).post("CRITICAL_FINDING", Map.of("ruleId", "r1"), AT);

        assertThat(delivery.delivered()).isFalse();
        assertThat(delivery.retryable()).isTrue();
        assertThat(delivery.error()).isEqualTo("HTTP 500");
        assertThat(bodies).hasSize(1);
    }

    @Test
    void throttlingAndRequestTimeoutAreTransientButOtherClientErrorsArePermanent() {
        AlertWebhookClient client = client(url("/alerts"), null);
        for (int transientStatus : new int[]{408, 429}) {
            status = transientStatus;
            assertThat(client.post("CRITICAL_FINDING", Map.of(), AT).retryable()).as("HTTP " + transientStatus).isTrue();
        }
        for (int permanentStatus : new int[]{400, 401, 403, 404}) {
            status = permanentStatus;
            AlertWebhookClient.Delivery delivery = client.post("CRITICAL_FINDING", Map.of(), AT);
            assertThat(delivery.delivered()).isFalse();
            assertThat(delivery.retryable()).as("HTTP " + permanentStatus).isFalse();
        }
    }

    @Test
    void aSlowDestinationTimesOutAndIsReportedAsNotDelivered() {
        gate = new CountDownLatch(1);

        AlertWebhookClient.Delivery delivery = client(url("/alerts"), null).post("CRITICAL_FINDING", Map.of("ruleId", "r1"), AT);

        assertThat(delivery.delivered()).isFalse();
        assertThat(delivery.retryable()).isTrue();
    }

    @Test
    void anUnreachableDestinationIsATransientFailureWithoutThrowing() {
        int port = server.getAddress().getPort();
        server.stop(0);

        AlertWebhookClient.Delivery delivery = client("http://127.0.0.1:" + port + "/alerts", null).post("CRITICAL_FINDING", Map.of("ruleId", "r1"), AT);

        assertThat(delivery.delivered()).isFalse();
        assertThat(delivery.retryable()).isTrue();
        assertThat(delivery.error()).isNotBlank();
    }

    @Test
    void redirectsAreNeverFollowedAndArePermanentFailures() {
        AlertWebhookClient.Delivery delivery = client(url("/redirect"), "secret-token").post("CRITICAL_FINDING", Map.of("ruleId", "r1"), AT);

        assertThat(delivery.delivered()).isFalse();
        assertThat(delivery.retryable()).isFalse();
        assertThat(redirectTargetHits.get()).isZero();
    }

    @Test
    void withoutADestinationTheClientIsNotConfigured() {
        assertThat(client(null, null).configured()).isFalse();
        assertThat(client("  ", "ignored-token").configured()).isFalse();
        assertThat(client(url("/alerts"), null).configured()).isTrue();
    }

    @Test
    void invalidDestinationsFailStartupInsteadOfSilentlyDroppingAlerts() {
        assertThatThrownBy(() -> client("ftp://example.com/alerts", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client("/relative", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client("not a uri", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client("https://user:pass@example.com/alerts", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client("https://example.com/alerts", "line1\r\nline2")).isInstanceOf(IllegalStateException.class);
    }
}
