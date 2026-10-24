package com.aicore.simulator.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class StubCoreServer implements AutoCloseable {

    public static final String STATUS_JSON = "{\"code\":200,\"data\":{\"ruleEngineEnabled\":true,\"aiAnalysisEnabled\":true,"
            + "\"aiTrigger\":\"ON_RULE_FLAG\",\"aiFailureMode\":\"INCONCLUSIVE\",\"deduplicationEnabled\":true,"
            + "\"aiRuntime\":{\"runtime\":\"ollama\",\"state\":\"UNREACHABLE\",\"modelName\":\"test-model\"}}}";
    public static final String RULES_JSON = "{\"code\":200,\"data\":[{\"id\":\"required-fields\",\"active\":true},"
            + "{\"id\":\"timestamp-validity\",\"active\":true},{\"id\":\"supported-event-type\",\"active\":true},"
            + "{\"id\":\"payload-limits\",\"active\":true},{\"id\":\"attribute-threshold\",\"active\":true},"
            + "{\"id\":\"reported-severity\",\"active\":true}]}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicInteger monitorRequests = new AtomicInteger();
    private volatile Function<String, Reply> monitorHandler = this::normalReply;
    private volatile Duration delay = Duration.ZERO;
    private volatile String lastBody;
    private volatile String lastContentType;

    private StubCoreServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        server.createContext("/actuator/health", exchange -> respond(exchange, new Reply(200, "{\"status\":\"UP\"}")));
        server.createContext("/api/v1/monitoring/status", exchange -> respond(exchange, new Reply(200, STATUS_JSON)));
        server.createContext("/api/v1/monitoring/rules", exchange -> respond(exchange, new Reply(200, RULES_JSON)));
        server.createContext("/api/v1/events/monitor", this::handleMonitor);
        server.setExecutor(executor);
        server.start();
    }

    public static StubCoreServer start() {
        return new StubCoreServer();
    }

    public static int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void onMonitor(Function<String, Reply> handler) {
        monitorHandler = handler;
    }

    public void delayResponses(Duration duration) {
        delay = duration;
    }

    public int getMonitorRequests() {
        return monitorRequests.get();
    }

    public String getLastBody() {
        return lastBody;
    }

    public String getLastContentType() {
        return lastContentType;
    }

    public Reply normalReply(String body) {
        try {
            JsonNode event = objectMapper.readTree(body);
            String eventId = event.path("eventId").asText();
            return new Reply(200, "{\"code\":200,\"message\":\"Event processed\",\"data\":{\"eventId\":\"" + eventId
                    + "\",\"monitoringStatus\":\"COMPLETED\",\"duplicate\":false,\"ruleDecision\":\"RULES_SUFFICIENT\","
                    + "\"ruleFindings\":[],\"aiAnalysis\":{\"analysisStatus\":\"SKIPPED\"},\"overallRiskLevel\":\"LOW\","
                    + "\"reviewRequired\":false,\"createdAt\":\"2026-10-24T00:00:00Z\"}}");
        } catch (IOException ex) {
            return new Reply(400, "{\"status\":1001,\"error\":\"Malformed request body\"}");
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handleMonitor(HttpExchange exchange) throws IOException {
        monitorRequests.incrementAndGet();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastBody = body;
        lastContentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
        }
        respond(exchange, monitorHandler.apply(body));
    }

    private void respond(HttpExchange exchange, Reply reply) throws IOException {
        byte[] bytes = reply.body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            }
        } catch (IOException ex) {
            exchange.close();
        }
    }

    public static final class Reply {
        private final int status;
        private final String body;

        public Reply(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
