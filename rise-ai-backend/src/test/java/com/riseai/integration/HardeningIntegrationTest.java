package com.riseai.integration;

import com.riseai.dto.request.LoginRequest;
import com.riseai.enums.Role;
import com.riseai.security.AuthUser;
import com.riseai.service.AuthService;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rise.hardening.auth-requests-per-window=3",
        "rise.hardening.rate-limit-window=PT1H",
        "rise.hardening.max-request-body=2KB"
})
class HardeningIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    AuthService authService;

    private String bearer(AuthUser user) {
        return "Bearer " + authService.login(new LoginRequest(user.email(), PASSWORD)).accessToken();
    }

    @Test
    void repeatedAuthRequestsFromOneClientAreRateLimitedWithRetryAfter() throws Exception {
        String body = "{\"email\":\"nobody@example.com\",\"password\":\"Wrong-pass-1\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void oversizedRequestBodyIsRejectedBeforeProcessing() throws Exception {
        String padding = "x".repeat(4096);
        mvc.perform(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + padding + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void responsesCarryHardenedSecurityHeaders() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
    }

    @Test
    void probesArePublicButMetricsRequireAnAdministrator() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").header("Authorization", bearer(user(Role.DONOR)))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").header("Authorization", bearer(user(Role.ADMIN)))).andExpect(status().isOk());
    }
}
