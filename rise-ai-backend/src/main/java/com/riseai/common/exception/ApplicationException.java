package com.riseai.common.exception;

import lombok.Getter;

@Getter
public class ApplicationException extends RuntimeException {
    private final ErrorCode errorCode;

    public ApplicationException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public ApplicationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static ApplicationException notFound(String resource) {
        return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, resource + " not found");
    }
}
