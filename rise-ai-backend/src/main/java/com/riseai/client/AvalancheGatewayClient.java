package com.riseai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.riseai.configuration.AvalancheEscrowProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${rise.avalanche.read-only:false}")
    private boolean readOnly;

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

    public record LatestAnchor(String anchorContract, long count, String root, Long recordCount, Long anchoredAt) {
    }

    public Result anchor(String kind, String root, String previousRoot, long recordCount, String operationKey) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("operationKey", operationKey);
        node.put("root", root);
        node.put("previousRoot", previousRoot);
        node.put("recordCount", recordCount);
        HttpRequest request = builder(BASE + "/anchors/" + kind + "/execute")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(node.toString(), StandardCharsets.UTF_8))
                .build();
        return send(request, false);
    }

    /** The latest on-chain anchor of a kind. Throws when the gateway cannot answer, which callers report as unavailable. */
    public LatestAnchor latestAnchor(String kind) {
        JsonNode json = getJson(BASE + "/anchors/" + kind + "/latest");
        return new LatestAnchor(json.path("anchorContract").asText(null), json.path("count").asLong(0), json.hasNonNull("root") ? json.get("root").asText() : null,
                json.hasNonNull("recordCount") ? json.get("recordCount").asLong() : null, json.hasNonNull("anchoredAt") ? json.get("anchoredAt").asLong() : null);
    }

    public record DeploymentInfo(String environment, long chainId, String escrow, String token, String tokenKind, String anchor,
                                 String contractVersion, boolean paused, String guardian, Long guardianHoldMaxSeconds, String operator,
                                 String approver) {
    }

    /** The verified deployment the gateway runs against. Throws when the gateway cannot answer. */
    public DeploymentInfo deployment() {
        JsonNode json = getJson(BASE + "/config");
        return new DeploymentInfo(json.path("environment").asText(null), json.path("chainId").asLong(0), json.path("escrow").asText(null),
                json.path("token").asText(null), json.path("tokenKind").asText(null), json.path("anchor").asText(null),
                json.path("contractVersion").asText(null), json.path("paused").asBoolean(false), json.path("guardianAddress").asText(null),
                json.hasNonNull("guardianHoldMaxSeconds") ? json.get("guardianHoldMaxSeconds").asLong() : null,
                json.path("operatorAddress").asText(null), json.path("approverAddress").asText(null));
    }

    /** Open guardian holds on a milestone. Throws when the gateway cannot answer. */
    public int openHolds(String campaignBytes32, String milestoneBytes32) {
        return getJson(BASE + "/campaigns/" + campaignBytes32 + "/milestones/" + milestoneBytes32).path("openHolds").asInt(0);
    }

    /** Times are epoch seconds; null until the payout is recorded or the beneficiary confirms it. */
    public record Payout(boolean recorded, String bankReferenceHash, Long recordedAt, Long confirmedAt) {
    }

    public Payout payout(String campaignBytes32, String requestBytes32) {
        JsonNode json = getJson(BASE + "/campaigns/" + campaignBytes32 + "/payouts/" + requestBytes32);
        return new Payout(json.path("recorded").asBoolean(false), json.path("bankReferenceHash").asText(null),
                json.hasNonNull("recordedAt") ? json.get("recordedAt").asLong() : null, json.hasNonNull("confirmedAt") ? json.get("confirmedAt").asLong() : null);
    }

    /** Amounts are rVND base units (two decimals). */
    public record Reserves(String escrow, String currentHeld, boolean attested, Long period, Long attestedAt, String attester, String bankBalance,
                           String heldAtAttestation, String gap, String statementHash) {
    }

    public Reserves reserves() {
        JsonNode j = getJson(BASE + "/reserves");
        return new Reserves(j.path("escrow").asText(null), j.path("currentHeld").asText(null), j.path("attested").asBoolean(false),
                j.hasNonNull("period") ? j.get("period").asLong() : null, j.hasNonNull("attestedAt") ? j.get("attestedAt").asLong() : null,
                j.path("attester").asText(null), j.path("bankBalance").asText(null), j.path("heldAtAttestation").asText(null), j.path("gap").asText(null),
                j.path("statementHash").asText(null));
    }

    /** When a queued release may execute on-chain, in epoch seconds, or null when the chain has no queued release for it. Throws when unreachable. */
    public Long releaseAt(String campaignBytes32, String requestBytes32) {
        JsonNode json = getJson(BASE + "/campaigns/" + campaignBytes32 + "/reservations/" + requestBytes32);
        long releaseAt = json.path("releaseAt").asLong(0);
        return releaseAt > 0 ? releaseAt : null;
    }

    private JsonNode getJson(String path) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(builder(path).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Gateway unreachable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Gateway answered HTTP " + response.statusCode());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Gateway returned a non-JSON body", ex);
        }
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
        if (readOnly && !"GET".equals(request.method())) {
            return new Result(Outcome.REJECTED, null, "Fuji instance is read-only");
        }
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
