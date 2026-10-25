package com.riseai.monitoring.exception;

import lombok.Getter;

@Getter
public class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] params;

    public ApplicationException(ErrorCode errorCode) {
        this(errorCode, (Object[]) null);
    }

    public ApplicationException(ErrorCode errorCode, Object... params) {
        super(errorCode.formatMessage(params));
        this.errorCode = errorCode;
        this.params = params;
    }
}
