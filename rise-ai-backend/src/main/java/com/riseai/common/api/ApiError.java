package com.riseai.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.riseai.common.RequestContext;
import com.riseai.common.exception.ErrorCode;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String code, String message, String path, String correlationId,
                       List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String path, List<FieldViolation> violations) {
        return new ApiError(Instant.now(), code.getHttpStatus().value(), code.name(), message, path, RequestContext.correlationId(), violations);
    }
}
