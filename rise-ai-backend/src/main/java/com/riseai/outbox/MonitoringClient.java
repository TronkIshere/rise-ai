package com.riseai.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.riseai.common.CorrelationIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class MonitoringClient {
    public static final String MONITOR_PATH = "/api/v1/events/monitor";
    private static final Set<Integer> RETRYABLE_CODES = Set.of(2002, 2003, 9000, 9999);
    private static final Set<String> ENVELOPE_ATTRIBUTES = Set.of("schemaVersion", "aggregateType", "actorRole");
    private static final int MAX_ATTRIBUTE_LENGTH = 1000;

    private final MonitoringRelayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MonitoringClient(MonitoringRelayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public DeliveryResult deliver(OutboxEvent event) {
        String body;
        try {
            body = objectMapper.writeValueAsString(toMonitorRequest(event));
        } catch (IOException ex) {
            return DeliveryResult.failed(0, null, "Envelope could not be mapped: " + ex.getMessage());
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(properties.baseUrl().toString().replaceAll("/+$", "") + MONITOR_PATH))
                .timeout(properties.readTimeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (event.getCorrelationId() != null) {
            request.header(CorrelationIdFilter.HEADER, event.getCorrelationId());
        }
        if (properties.hasServiceKey()) {
            request.header("Authorization", "Bearer " + properties.serviceKey());
        }
        try {
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return classify(response.statusCode(), response.body());
        } catch (HttpTimeoutException ex) {
            return DeliveryResult.retry(0, null, "Monitoring request timed out");
        } catch (IOException ex) {
            return DeliveryResult.retry(0, null, "Monitoring service unreachable: " + ex.getClass().getSimpleName());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DeliveryResult.retry(0, null, "Relay interrupted");
        }
    }

    ObjectNode toMonitorRequest(OutboxEvent event) throws IOException {
        JsonNode envelope = objectMapper.readTree(event.getEnvelope());
        ObjectNode request = objectMapper.createObjectNode();
        request.put("eventId", envelope.path("eventId").asText());
        request.put("eventType", envelope.path("eventType").asText());
        request.put("source", envelope.path("producer").asText());
        request.put("occurredAt", envelope.path("occurredAt").asText());
        if (envelope.hasNonNull("correlationId")) {
            request.put("correlationId", envelope.get("correlationId").asText());
        }
        ObjectNode attributes = request.putObject("attributes");
        for (String name : ENVELOPE_ATTRIBUTES) {
            if (envelope.hasNonNull(name)) {
                attributes.set(name, envelope.get(name));
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = envelope.path("payload").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (isAttributeCandidate(field.getKey()) && value.isValueNode() && !value.isNull()
                    && (!value.isTextual() || value.asText().length() <= MAX_ATTRIBUTE_LENGTH)) {
                attributes.set(field.getKey(), value);
            }
        }
        request.set("payload", envelope);
        return request;
    }

    private static boolean isAttributeCandidate(String name) {
        return !(name.endsWith("Id") || name.endsWith("Ids") || name.endsWith("Code") || name.endsWith("Hash") || name.endsWith("By"));
    }

    private DeliveryResult classify(int status, String body) {
        JsonNode json = parse(body);
        if (status == 200) {
            JsonNode data = json == null ? null : json.path("data");
            if (data == null || data.isMissingNode() || !data.isObject()) {
                return DeliveryResult.failed(status, null, "Monitoring response had no data object");
            }
            return DeliveryResult.delivered(data);
        }
        Integer code = json != null && json.path("status").isInt() ? json.path("status").asInt() : null;
        String detail = "HTTP " + status + (code == null ? "" : " code " + code);
        if (status == 401 || status == 403) {
            return DeliveryResult.failed(status, code, detail + ": monitoring service rejected the service key");
        }
        if (code != null && RETRYABLE_CODES.contains(code)) {
            return DeliveryResult.retry(status, code, detail);
        }
        if (status >= 500 || status == 429 || status == 408) {
            return DeliveryResult.retry(status, code, detail);
        }
        return DeliveryResult.failed(status, code, detail);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            log.warn("Monitoring service returned a non-JSON body");
            return null;
        }
    }

    public enum Outcome {
        DELIVERED,
        RETRY,
        FAILED
    }

    public record DeliveryResult(Outcome outcome, int httpStatus, Integer errorCode, JsonNode data, String error) {
        static DeliveryResult delivered(JsonNode data) {
            return new DeliveryResult(Outcome.DELIVERED, 200, null, data, null);
        }

        static DeliveryResult retry(int status, Integer code, String error) {
            return new DeliveryResult(Outcome.RETRY, status, code, null, error);
        }

        static DeliveryResult failed(int status, Integer code, String error) {
            return new DeliveryResult(Outcome.FAILED, status, code, null, error);
        }
    }
}
