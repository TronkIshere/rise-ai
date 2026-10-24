package com.aicore.eventmonitor.ollama;

import lombok.Getter;

@Getter
public class OllamaException extends RuntimeException {

    private final OllamaErrorType type;

    public OllamaException(OllamaErrorType type, String message) {
        super(message);
        this.type = type;
    }

    public OllamaException(OllamaErrorType type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }
}
