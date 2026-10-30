package com.riseai.exception;

import com.riseai.dto.response.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");

    private static HttpMessageNotReadableException unreadable(Throwable cause) {
        return new HttpMessageNotReadableException("I/O error while reading input message", cause, new MockHttpInputMessage(new byte[0]));
    }

    @Test
    void anOversizedBodyWrappedByTheMessageConverterStillAnswersPayloadTooLarge() {
        Throwable wrapped = new RuntimeException("converter", new ApplicationException(ErrorCode.REQUEST_TOO_LARGE));

        ResponseEntity<ApiError> response = handler.handleBadRequest(unreadable(wrapped), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().code()).isEqualTo("REQUEST_TOO_LARGE");
    }

    @Test
    void otherUnreadableBodiesRemainBadRequests() {
        ResponseEntity<ApiError> response = handler.handleBadRequest(unreadable(new IllegalStateException("bad json")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void anUnrelatedApplicationExceptionInTheCauseChainDoesNotChangeTheStatus() {
        ResponseEntity<ApiError> response = handler.handleBadRequest(
                unreadable(new ApplicationException(ErrorCode.ACCESS_DENIED)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
