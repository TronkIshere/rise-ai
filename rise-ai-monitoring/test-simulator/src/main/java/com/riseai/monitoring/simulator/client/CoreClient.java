package com.riseai.monitoring.simulator.client;

import com.riseai.monitoring.simulator.scenario.CoreContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class CoreClient {

    private static final Duration MAX_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_ERROR_LENGTH = 200;

    private final String baseUrl;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CoreClient(URI baseUrl, Duration requestTimeout, ObjectMapper objectMapper) {
        String url = baseUrl.toString();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.requestTimeout = requestTimeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout.compareTo(MAX_CONNECT_TIMEOUT) < 0 ? requestTimeout : MAX_CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public CoreResponse postEvent(String body, String contentType) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + CoreContract.MONITOR_PATH))
                .timeout(requestTimeout)
                .header("Content-Type", contentType)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build());
    }

    public CoreResponse get(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build());
    }

    private CoreResponse send(HttpRequest request) {
        long startedAt = System.nanoTime();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return CoreResponse.builder()
                    .outcome(TransportOutcome.RESPONSE)
                    .httpStatus(response.statusCode())
                    .body(parse(response.body()))
                    .latencyNanos(System.nanoTime() - startedAt)
                    .build();
        } catch (HttpConnectTimeoutException ex) {
            return failure(TransportOutcome.CONNECTION_FAILED, ex, startedAt);
        } catch (HttpTimeoutException ex) {
            return failure(TransportOutcome.TIMEOUT, ex, startedAt);
        } catch (ConnectException ex) {
            return failure(TransportOutcome.CONNECTION_FAILED, ex, startedAt);
        } catch (IOException ex) {
            return failure(TransportOutcome.IO_ERROR, ex, startedAt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failure(TransportOutcome.IO_ERROR, ex, startedAt);
        }
    }

    private JsonNode parse(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            return null;
        }
    }

    private CoreResponse failure(TransportOutcome outcome, Exception ex, long startedAt) {
        String message = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
        return CoreResponse.builder()
                .outcome(outcome)
                .latencyNanos(System.nanoTime() - startedAt)
                .error(message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH))
                .build();
    }
}
