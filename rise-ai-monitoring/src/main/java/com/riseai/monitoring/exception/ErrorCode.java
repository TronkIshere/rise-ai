package com.riseai.monitoring.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_DATA(1000, "Invalid request data", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(1001, "Malformed request body", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(1002, "Resource not found: %s", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(1003, "HTTP method not supported: %s", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(1004, "Content type not supported: %s", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    REQUEST_TOO_LARGE(1005, "Request body exceeds the limit of %s bytes", HttpStatus.PAYLOAD_TOO_LARGE),
    UNAUTHENTICATED(1006, "A valid service key is required", HttpStatus.UNAUTHORIZED),

    INVALID_EVENT(2000, "Invalid event: %s", HttpStatus.BAD_REQUEST),
    EVENT_ID_CONFLICT(2001, "Event ID was already received with different content: %s", HttpStatus.CONFLICT),
    EVENT_IN_PROGRESS(2002, "Event is already being processed: %s", HttpStatus.CONFLICT),
    DEDUPLICATION_CAPACITY_EXHAUSTED(2003, "Too many events are being processed concurrently, please retry later", HttpStatus.SERVICE_UNAVAILABLE),

    MONITORING_FAILED(9000, "Event monitoring failed", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(9999, "An unexpected error occurred, please try again later", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String formatMessage(Object... params) {
        if (params == null || params.length == 0) {
            return message;
        }
        return String.format(message, params);
    }
}
