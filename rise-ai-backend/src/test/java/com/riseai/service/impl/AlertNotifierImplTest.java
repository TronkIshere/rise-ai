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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertNotifierImplTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofMillis(300);
    private static final Duration RETRY = Duration.ofMillis(20);

    private HttpServer server;
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final AtomicInteger redirectTargetHits = new AtomicInteger();
    private volatile int status = 200;
    private volatile CountDownLatch gate;
    private AlertNotifierImpl notifier;

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
        if (notifier != null) {
            notifier.shutdown();
        }
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private AlertNotifierImpl notifier(String url, String token) {
        MonitoringRelayProperties properties = new MonitoringRelayProperties(true, null, "key", null, null, null, null, null, null, null, null, url, token);
        notifier = new AlertNotifierImpl(properties, MAPPER, TIMEOUT, RETRY);
        return notifier;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 200 && !condition.getAsBoolean(); i++) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @Test
    void deliversKindAndDetailsWithTheBearerToken() throws Exception {
        notifier(url("/alerts"), "secret-token").alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));

        await(() -> notifier.delivery().delivered() == 1);
        assertThat(bodies.get(0).path("kind").asText()).isEqualTo("CRITICAL_FINDING");
        assertThat(bodies.get(0).path("details").path("ruleId").asText()).isEqualTo("r1");
        assertThat(bodies.get(0).toString()).doesNotContain("secret-token");
        assertThat(authorizations).containsExactly("Bearer secret-token");
    }

    @Test
    void withoutATokenNoAuthorizationHeaderIsSent() throws Exception {
        notifier(url("/alerts"), null).alert("OUTBOX_EVENT_FAILED", Map.of("eventId", "e1"));

        await(() -> notifier.delivery().delivered() == 1);
        assertThat(authorizations).containsExactly("null");
    }

    @Test
    void aRejectingDestinationIsRetriedOnceThenGivenUp() throws Exception {
        status = 500;
        notifier(url("/alerts"), null).alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));

        await(() -> notifier.delivery().failed() == 1);
        assertThat(bodies).hasSize(AlertNotifierImpl.ATTEMPTS);
        assertThat(notifier.delivery().delivered()).isZero();
    }

    @Test
    void aSlowDestinationTimesOutAndIsCountedAsFailed() throws Exception {
        gate = new CountDownLatch(1);
        notifier(url("/alerts"), null).alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));

        await(() -> notifier.delivery().failed() == 1);
    }

    @Test
    void anUnreachableDestinationIsCountedAsFailedWithoutThrowing() throws Exception {
        int port = server.getAddress().getPort();
        server.stop(0);
        notifier("http://127.0.0.1:" + port + "/alerts", null).alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));

        await(() -> notifier.delivery().failed() == 1);
    }

    @Test
    void redirectsAreNeverFollowed() throws Exception {
        notifier(url("/redirect"), "secret-token").alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));

        await(() -> notifier.delivery().failed() == 1);
        assertThat(redirectTargetHits.get()).isZero();
    }

    @Test
    void withoutADestinationAlertsAreOnlyLogged() throws Exception {
        notifier(null, null).alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));
        notifier("  ", "ignored-token").alert("CRITICAL_FINDING", Map.of("ruleId", "r1"));

        Thread.sleep(200);
        assertThat(bodies).isEmpty();
        assertThat(notifier.delivery()).isEqualTo(new AlertNotifierImpl.Delivery(0, 0, 0));
    }

    @Test
    void invalidDestinationsFailStartupInsteadOfSilentlyDroppingAlerts() {
        assertThatThrownBy(() -> notifier("ftp://example.com/alerts", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> notifier("/relative", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> notifier("not a uri", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> notifier("https://user:pass@example.com/alerts", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> notifier("https://example.com/alerts", "line1\r\nline2")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFullQueueDropsAlertsAndNeverBlocksTheCaller() {
        gate = new CountDownLatch(1);
        AlertNotifierImpl target = notifier(url("/alerts"), null);
        int total = AlertNotifierImpl.QUEUE_CAPACITY + 20;
        long startedAt = System.nanoTime();

        for (int i = 0; i < total; i++) {
            target.alert("CRITICAL_FINDING", Map.of("n", String.valueOf(i)));
        }

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
        assertThat(target.delivery().dropped()).isBetween(1L, 20L);
    }
}
