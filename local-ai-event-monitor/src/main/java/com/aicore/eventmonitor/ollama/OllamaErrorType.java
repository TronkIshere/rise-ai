package com.aicore.eventmonitor.ollama;

public enum OllamaErrorType {
    CONNECTION_FAILED,
    TIMEOUT,
    MODEL_NOT_FOUND,
    HTTP_ERROR,
    INVALID_RESPONSE,
    EMPTY_OUTPUT,
    INTERRUPTED
}
