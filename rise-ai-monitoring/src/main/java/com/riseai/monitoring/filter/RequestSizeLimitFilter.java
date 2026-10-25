package com.riseai.monitoring.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.constants.PlatformConstants;
import com.riseai.monitoring.dto.response.common.ErrorResponse;
import com.riseai.monitoring.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final MonitoringProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PlatformConstants.EVENT_API_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long limit = properties.getLimits().getMaxRequestBytes();
        if (request.getContentLengthLong() > limit) {
            writeTooLarge(request, response, limit);
            return;
        }
        filterChain.doFilter(new LimitedRequest(request, limit), response);
    }

    private void writeTooLarge(HttpServletRequest request, HttpServletResponse response, long limit) throws IOException {
        Object correlationId = request.getAttribute(PlatformConstants.CORRELATION_ID_ATTRIBUTE);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(new Date())
                .status(ErrorCode.REQUEST_TOO_LARGE.getCode())
                .error(ErrorCode.REQUEST_TOO_LARGE.formatMessage(limit))
                .path(request.getRequestURI())
                .correlationId(correlationId instanceof String value ? value : null)
                .build();
        response.setStatus(ErrorCode.REQUEST_TOO_LARGE.getHttpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream inputStream;

        private LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedInputStream(super.getInputStream(), limit);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long bytesRead;

        private LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private void count(int read) throws RequestBodyTooLargeException {
            bytesRead += read;
            if (bytesRead > limit) {
                throw new RequestBodyTooLargeException(limit);
            }
        }
    }
}
