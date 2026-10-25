package com.riseai.monitoring.filter;

import lombok.Getter;

import java.io.IOException;

@Getter
public class RequestBodyTooLargeException extends IOException {

    private final long limit;

    public RequestBodyTooLargeException(long limit) {
        super("Request body exceeds " + limit + " bytes");
        this.limit = limit;
    }
}
