package com.riseai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.common.api.ApiError;
import com.riseai.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        write(request, response, ErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        write(request, response, ErrorCode.ACCESS_DENIED);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(code, code.getMessage(), request.getRequestURI(), null));
    }
}
