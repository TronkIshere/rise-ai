package com.riseai.configuration;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSizeLimitFilterTest {
    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(
            new HardeningProperties(null, null, null, null, DataSize.ofBytes(1024)), JsonMapper.builder().findAndAddModules().build());

    private static MockHttpServletRequest post(byte[] body, boolean declareLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/campaigns");
        request.setContent(body);
        if (declareLength) {
            request.setContentType("application/json");
            request.addHeader("Content-Length", body.length);
        }
        return request;
    }

    @Test
    void declaredLengthAboveTheLimitIsRejectedWithoutReachingTheChain() throws Exception {
        MockHttpServletRequest request = post(new byte[4096], true);
        request.setContentType("application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger reached = new AtomicInteger();

        filter.doFilter(new DeclaredLength(request, 4096), response, (req, res) -> reached.incrementAndGet());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
        assertThat(reached).hasValue(0);
    }

    @Test
    void undeclaredLengthBodyAboveTheLimitIsRejectedWhileItIsRead() throws Exception {
        MockHttpServletRequest request = post(new byte[4096], false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> req.getInputStream().readAllBytes());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
    }

    @Test
    void undeclaredLengthBodyWithinTheLimitPassesThroughUnchanged() throws Exception {
        MockHttpServletRequest request = post(new byte[512], false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger bytesRead = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> bytesRead.set(req.getInputStream().readAllBytes().length));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(bytesRead).hasValue(512);
    }

    @Test
    void bodyAtExactlyTheLimitIsAccepted() throws Exception {
        MockHttpServletRequest request = post(new byte[1024], false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger bytesRead = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> bytesRead.set(req.getInputStream().readAllBytes().length));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(bytesRead).hasValue(1024);
    }

    private static final class DeclaredLength extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final long length;

        DeclaredLength(MockHttpServletRequest request, long length) {
            super(request);
            this.length = length;
        }

        @Override
        public long getContentLengthLong() {
            return length;
        }
    }
}
