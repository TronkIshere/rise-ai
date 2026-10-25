package com.riseai.monitoring.analysis;

public class InvalidAiOutputException extends RuntimeException {

    public InvalidAiOutputException(String message) {
        super(message);
    }
}
