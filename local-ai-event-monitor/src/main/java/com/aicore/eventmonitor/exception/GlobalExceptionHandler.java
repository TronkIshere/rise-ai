package com.aicore.eventmonitor.exception;

import com.aicore.eventmonitor.constants.PlatformConstants;
import com.aicore.eventmonitor.dto.response.common.ErrorResponse;
import com.aicore.eventmonitor.filter.RequestBodyTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Date;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(
            ApplicationException ex, HttpServletRequest request) {

        ErrorCode errorCode = ex.getErrorCode();
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.warn("Request to {} failed with {}", request.getRequestURI(), errorCode);
        }
        return buildResponse(errorCode, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.INVALID_DATA.getMessage());

        return buildResponse(ErrorCode.INVALID_DATA, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {

        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(ErrorCode.INVALID_DATA.getMessage());

        return buildResponse(ErrorCode.INVALID_DATA, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof RequestBodyTooLargeException tooLarge) {
                return buildResponse(ErrorCode.REQUEST_TOO_LARGE,
                        ErrorCode.REQUEST_TOO_LARGE.formatMessage(tooLarge.getLimit()), request);
            }
            cause = cause.getCause();
        }
        return buildResponse(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpServletRequest request) {

        return buildResponse(ErrorCode.RESOURCE_NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND.formatMessage(request.getRequestURI()), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        return buildResponse(ErrorCode.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED.formatMessage(ex.getMethod()), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        return buildResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE.formatMessage(String.valueOf(ex.getContentType())), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        if (ex instanceof org.springframework.web.ErrorResponse webError
                && webError.getStatusCode().is4xxClientError()) {
            return buildResponse(ErrorCode.INVALID_DATA, webError.getStatusCode(),
                    ErrorCode.INVALID_DATA.getMessage(), request);
        }
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return buildResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(ErrorCode errorCode, String message,
                                                        HttpServletRequest request) {
        return buildResponse(errorCode, errorCode.getHttpStatus(), message, request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(ErrorCode errorCode, HttpStatusCode httpStatus,
                                                        String message, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(new Date())
                .status(errorCode.getCode())
                .error(message)
                .path(request.getRequestURI())
                .correlationId(resolveCorrelationId(request))
                .build();

        return ResponseEntity.status(httpStatus).body(errorResponse);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        Object correlationId = request.getAttribute(PlatformConstants.CORRELATION_ID_ATTRIBUTE);
        return correlationId instanceof String value ? value : null;
    }
}
