package com.aicore.eventmonitor.ollama;

import com.aicore.eventmonitor.configuration.OllamaProperties;
import com.aicore.eventmonitor.ollama.dto.OllamaChatRequest;
import com.aicore.eventmonitor.ollama.dto.OllamaChatResponse;
import com.aicore.eventmonitor.ollama.dto.OllamaModel;
import com.aicore.eventmonitor.ollama.dto.OllamaTagsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class OllamaClient implements DisposableBean {

    private static final String CHAT_PATH = "/api/chat";
    private static final String TAGS_PATH = "/api/tags";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaClient(OllamaProperties properties, ObjectMapper objectMapper) {
        validateBaseUrl(properties.getBaseUrl());
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public OllamaChatResponse chat(OllamaChatRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint(CHAT_PATH))
                .timeout(properties.getRequestTimeout())
                .header("Content-Type", JSON_CONTENT_TYPE)
                .header("Accept", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(toJson(request)))
                .build();

        HttpResponse<byte[]> response = send(httpRequest);
        if (response.statusCode() == 404 && isModelNotFound(response.body())) {
            throw new OllamaException(OllamaErrorType.MODEL_NOT_FOUND,
                    "Model " + request.getModel() + " is not installed in Ollama");
        }
        ensureSuccess(response);

        OllamaChatResponse chatResponse = fromJson(response.body(), OllamaChatResponse.class);
        if (chatResponse.getMessage() == null) {
            throw new OllamaException(OllamaErrorType.INVALID_RESPONSE, "Ollama response does not contain a message");
        }
        String content = chatResponse.getMessage().getContent();
        if (content == null || content.isBlank()) {
            throw new OllamaException(OllamaErrorType.EMPTY_OUTPUT, "Ollama returned an empty message");
        }
        return chatResponse;
    }

    public List<String> listModelNames() {
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint(TAGS_PATH))
                .timeout(properties.getStatusTimeout())
                .header("Accept", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse<byte[]> response = send(httpRequest);
        ensureSuccess(response);
        OllamaTagsResponse tags = fromJson(response.body(), OllamaTagsResponse.class);
        if (tags.getModels() == null) {
            return List.of();
        }
        return tags.getModels().stream()
                .map(OllamaModel::getName)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public void destroy() {
        httpClient.shutdownNow();
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpConnectTimeoutException ex) {
            throw new OllamaException(OllamaErrorType.CONNECTION_FAILED, "Timed out connecting to Ollama", ex);
        } catch (HttpTimeoutException ex) {
            throw new OllamaException(OllamaErrorType.TIMEOUT, "Ollama did not respond within the configured timeout", ex);
        } catch (IOException ex) {
            throw new OllamaException(OllamaErrorType.CONNECTION_FAILED, "Could not communicate with Ollama", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OllamaException(OllamaErrorType.INTERRUPTED, "Ollama request was interrupted", ex);
        }
    }

    private void ensureSuccess(HttpResponse<byte[]> response) {
        if (response.statusCode() / 100 != 2) {
            throw new OllamaException(OllamaErrorType.HTTP_ERROR, "Ollama returned HTTP " + response.statusCode());
        }
    }

    private boolean isModelNotFound(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root == null ? null : root.get("error");
            return error != null && error.asText().toLowerCase(Locale.ROOT).contains("not found");
        } catch (IOException ex) {
            return false;
        }
    }

    private byte[] toJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Ollama request could not be serialized", ex);
        }
    }

    private <T> T fromJson(byte[] body, Class<T> type) {
        T value;
        try {
            value = objectMapper.readValue(body, type);
        } catch (IOException ex) {
            throw new OllamaException(OllamaErrorType.INVALID_RESPONSE, "Ollama returned a body that is not valid JSON", ex);
        }
        if (value == null) {
            throw new OllamaException(OllamaErrorType.INVALID_RESPONSE, "Ollama returned an empty body");
        }
        return value;
    }

    private URI endpoint(String path) {
        String baseUrl = properties.getBaseUrl().toString();
        return URI.create((baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + path);
    }

    private void validateBaseUrl(URI baseUrl) {
        String scheme = baseUrl == null ? null : baseUrl.getScheme();
        if (scheme == null || baseUrl.getHost() == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalStateException("ollama.base-url must be an absolute http or https URL");
        }
    }
}
