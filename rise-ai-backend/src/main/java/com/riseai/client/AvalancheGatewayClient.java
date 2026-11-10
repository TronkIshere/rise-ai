package com.riseai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.riseai.configuration.AvalancheEscrowProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** The only path from the backend to the chain gateway. Runs outside business transactions, from the command relay. */
@Component
@ConditionalOnProperty(prefix = "rise.escrow", name = "engine", havingValue = "avalanche")
public class AvalancheGatewayClient {
    private static final String BASE = "/api/v1/avalanche";
    private static final Set<String> SUCCESS = Set.of("CONFIRMED", "FINALIZED");
    private static final Set<String> FAILURE = Set.of("REVERTED", "FAILED");

    private final AvalancheEscrowProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AvalancheGatewayClient(AvalancheEscrowProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public enum Outcome {
        ACCEPTED,
        SUCCEEDED,
        REVERTED,
        IN_PROGRESS,
        RETRY,
        REJECTED
    }

    public record Result(Outcome outcome, String txHash, String error) {
    }

    public Result execute(String campaignBytes32, String action, String payload, String operationKey) {
        String body;
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(payload);
            node.put("operationKey", operationKey);
            body = objectMapper.writeValueAsString(node);
        } catch (IOException | ClassCastException ex) {
            return new Result(Outcome.REJECTED, null, "Command payload is not a JSON object");
        }
        HttpRequest request = builder(BASE + "/campaigns/" + campaignBytes32 + "/actions/" + action + "/execute")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(request, false);
    }

    public Result transaction(String operationKey) {
        return send(builder(BASE + "/transactions/" + operationKey).GET().build(), true);
    }

    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create(properties.gatewayUrl().toString().replaceAll("/+$", "") + path))
                .timeout(properties.readTimeout())
                .header("Accept", "application/json")
                .header("X-Internal-Api-Key", properties.internalApiKey() == null ? "" : properties.internalApiKey());
    }

    private Result send(HttpRequest request, boolean lookup) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException ex) {
            return new Result(Outcome.RETRY, null, "Gateway request timed out");
        } catch (IOException ex) {
            return new Result(Outcome.RETRY, null, "Gateway unreachable: " + ex.getClass().getSimpleName());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(Outcome.RETRY, null, "Relay interrupted");
        }
        int status = response.statusCode();
        if (status >= 500 || status == 429 || status == 408 || (lookup && status == 404)) {
            return new Result(Outcome.RETRY, null, "Gateway answered HTTP " + status);
        }
        if (status != 200) {
            return new Result(Outcome.REJECTED, null, "Gateway answered HTTP " + status + errorCode(response.body()));
        }
        return classify(response.body(), lookup);
    }

    private Result classify(String body, boolean lookup) {
        JsonNode json;
        try {
            json = objectMapper.readTree(body);
        } catch (IOException ex) {
            return new Result(Outcome.RETRY, null, "Gateway returned a non-JSON body");
        }
        String state = json.path("state").asText("");
        String hash = json.hasNonNull("txHash") ? json.get("txHash").asText() : null;
        if (!lookup) {
            return FAILURE.contains(state) ? new Result(Outcome.REVERTED, hash, "Transaction state " + state) : new Result(Outcome.ACCEPTED, hash, null);
        }
        if (SUCCESS.contains(state)) {
            return new Result(Outcome.SUCCEEDED, hash, null);
        }
        if (FAILURE.contains(state)) {
            return new Result(Outcome.REVERTED, hash, "Transaction state " + state);
        }
        return new Result(Outcome.IN_PROGRESS, hash, null);
    }

    private String errorCode(String body) {
        try {
            String code = objectMapper.readTree(body).path("code").asText("");
            return code.isEmpty() ? "" : " " + code;
        } catch (IOException ex) {
            return "";
        }
    }
}
