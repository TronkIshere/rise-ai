package com.aicore.simulator.client;

import com.aicore.simulator.testsupport.StubCoreServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CoreClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StubCoreServer server;
    private CoreClient client;

    @BeforeEach
    void setUp() {
        server = StubCoreServer.start();
        client = new CoreClient(URI.create(server.getBaseUrl() + "/"), Duration.ofMillis(500), objectMapper);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void postsEventsAndParsesJsonResponses() {
        CoreResponse response = client.postEvent("{\"eventId\":\"evt-1\"}", "application/json");

        assertThat(response.isReceived()).isTrue();
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getBody().at("/data/eventId").asText()).isEqualTo("evt-1");
        assertThat(response.getLatencyNanos()).isPositive();
        assertThat(server.getLastContentType()).isEqualTo("application/json");
    }

    @Test
    void sendsMalformedBodiesAndCustomContentTypesVerbatim() {
        CoreResponse response = client.postEvent("{\"eventId\":", "text/plain");

        assertThat(server.getLastBody()).isEqualTo("{\"eventId\":");
        assertThat(server.getLastContentType()).isEqualTo("text/plain");
        assertThat(response.getHttpStatus()).isEqualTo(400);
        assertThat(response.getBody().path("status").asInt()).isEqualTo(1001);
    }

    @Test
    void keepsResponsesWithUnreadableBodies() {
        server.onMonitor(body -> new StubCoreServer.Reply(500, "Internal Server Error"));

        CoreResponse response = client.postEvent("{}", "application/json");

        assertThat(response.isReceived()).isTrue();
        assertThat(response.getHttpStatus()).isEqualTo(500);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void reportsTimeouts() {
        server.delayResponses(Duration.ofSeconds(3));
        long startedAt = System.nanoTime();

        CoreResponse response = client.postEvent("{}", "application/json");

        assertThat(response.getOutcome()).isEqualTo(TransportOutcome.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void reportsRefusedConnections() {
        CoreClient offline = new CoreClient(URI.create("http://127.0.0.1:" + StubCoreServer.unusedPort()), Duration.ofMillis(500), objectMapper);

        CoreResponse response = offline.get("/actuator/health");

        assertThat(response.getOutcome()).isEqualTo(TransportOutcome.CONNECTION_FAILED);
        assertThat(response.getError()).isNotBlank();
    }
}
