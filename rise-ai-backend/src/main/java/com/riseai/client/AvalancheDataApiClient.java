package com.riseai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.configuration.AvalancheEscrowProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads the escrow's transactions from the Avalanche Data API: an indexer run by Avalanche, not by this platform, so the public
 * activity check does not depend on the platform's own records. Read-only, outside any business transaction, never sends a key
 * other than the optional Data API key.
 */
@Component
@ConditionalOnProperty(prefix = "rise.escrow", name = "engine", havingValue = "avalanche")
public class AvalancheDataApiClient {
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final Pattern PAGE_TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,200}");

    private final AvalancheEscrowProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AvalancheDataApiClient(AvalancheEscrowProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** One rVND (or other ERC-20) movement inside a transaction; value is in the token's base units. */
    public record TokenTransfer(String from, String to, String value, String symbol) {
    }

    public record IndexedTransaction(String txHash, long blockNumber, long blockTimestamp, String from, String to, String methodHash, boolean success,
                                     List<TokenTransfer> transfers) {
    }

    public record Page(List<IndexedTransaction> transactions, String nextPageToken) {
    }

    /** Newest first. Throws when the Data API cannot answer. */
    public Page transactions(long chainId, String address, int pageSize, String pageToken) {
        if (!ADDRESS.matcher(address).matches()) {
            throw new IllegalArgumentException("not an address");
        }
        StringBuilder path = new StringBuilder("/chains/").append(chainId).append("/addresses/").append(address).append("/transactions?pageSize=")
                .append(pageSize).append("&sortOrder=desc");
        if (pageToken != null) {
            if (!PAGE_TOKEN.matcher(pageToken).matches()) {
                throw new IllegalArgumentException("invalid page token");
            }
            path.append("&pageToken=").append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(properties.dataApiUrl().toString().replaceAll("/+$", "") + path))
                .timeout(properties.readTimeout())
                .header("Accept", "application/json")
                .GET();
        if (properties.dataApiKey() != null && !properties.dataApiKey().isBlank()) {
            request.header("x-glacier-api-key", properties.dataApiKey());
        }
        HttpResponse<String> response;
        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Data API unreachable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Data API answered HTTP " + response.statusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            List<IndexedTransaction> result = new ArrayList<>();
            for (JsonNode item : root.path("transactions")) {
                JsonNode tx = item.path("nativeTransaction");
                List<TokenTransfer> transfers = new ArrayList<>();
                for (JsonNode t : item.path("erc20Transfers")) {
                    transfers.add(new TokenTransfer(lower(t.path("from").path("address").asText(null)), lower(t.path("to").path("address").asText(null)),
                            t.path("value").asText(null), t.path("erc20Token").path("symbol").asText(null)));
                }
                result.add(new IndexedTransaction(lower(tx.path("txHash").asText(null)), tx.path("blockNumber").asLong(0), tx.path("blockTimestamp").asLong(0),
                        lower(tx.path("from").path("address").asText(null)), lower(tx.path("to").path("address").asText(null)),
                        lower(tx.path("method").path("methodHash").asText(null)), "1".equals(tx.path("txStatus").asText()), transfers));
            }
            String next = root.hasNonNull("nextPageToken") ? root.get("nextPageToken").asText() : null;
            return new Page(result, next);
        } catch (IOException ex) {
            throw new IllegalStateException("Data API returned a non-JSON body", ex);
        }
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
