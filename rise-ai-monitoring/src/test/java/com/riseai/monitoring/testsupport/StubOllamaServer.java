package com.riseai.monitoring.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StubOllamaServer implements AutoCloseable {

    private static StubOllamaServer shared;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<String> chatRequests = new CopyOnWriteArrayList<>();
    private final List<String> requestPaths = new CopyOnWriteArrayList<>();

    private volatile int chatStatus;
    private volatile String chatBody;
    private volatile Duration chatDelay;
    private volatile String tagsBody;

    private StubOllamaServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        server.createContext("/api/chat", this::handleChat);
        server.createContext("/api/tags", exchange -> {
            requestPaths.add(exchange.getRequestURI().getPath());
            respond(exchange, 200, tagsBody);
        });
        server.createContext("/", exchange -> {
            requestPaths.add(exchange.getRequestURI().getPath());
            respond(exchange, 404, "{\"error\":\"not found\"}");
        });
        server.setExecutor(executor);
        reset();
        server.start();
    }

    public static StubOllamaServer start() {
        return new StubOllamaServer();
    }

    public static synchronized StubOllamaServer shared() {
        if (shared == null) {
            shared = new StubOllamaServer();
        }
        return shared;
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

    public void reset() {
        chatStatus = 200;
        chatBody = chatEnvelope("{}");
        chatDelay = Duration.ZERO;
        tagsBody = "{\"models\":[]}";
        chatRequests.clear();
        requestPaths.clear();
    }

    public void respondWithAnalysis(String modelContent) {
        chatStatus = 200;
        chatBody = chatEnvelope(modelContent);
    }

    public void respondWithStatus(int status, String body) {
        chatStatus = status;
        chatBody = body;
    }

    public void delayChatResponses(Duration delay) {
        chatDelay = delay;
    }

    public void installedModels(String... names) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode models = root.putArray("models");
        for (String name : names) {
            models.addObject().put("name", name).put("model", name);
        }
        tagsBody = root.toString();
    }

    public List<String> getRequestPaths() {
        return List.copyOf(requestPaths);
    }

    public List<String> getChatRequests() {
        return List.copyOf(chatRequests);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private String chatEnvelope(String content) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", "test-model");
        root.putObject("message").put("role", "assistant").put("content", content);
        root.put("done", true);
        root.put("done_reason", "stop");
        return root.toString();
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        requestPaths.add(exchange.getRequestURI().getPath());
        chatRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Duration delay = chatDelay;
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
        }
        respond(exchange, chatStatus, chatBody);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            }
        } catch (IOException ex) {
            exchange.close();
        }
    }
}
