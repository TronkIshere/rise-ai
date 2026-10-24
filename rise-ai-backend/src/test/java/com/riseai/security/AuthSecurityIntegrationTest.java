package com.riseai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.security.AuthDtos.LoginRequest;
import com.riseai.security.AuthDtos.TokenResponse;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
import com.riseai.user.UserService;
import com.riseai.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthSecurityIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    AuthService authService;
    @Autowired
    UserService userService;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private TokenResponse login(AuthUser user) {
        return authService.login(new LoginRequest(user.email(), PASSWORD));
    }

    private String bearer(AuthUser user) {
        return "Bearer " + login(user).accessToken();
    }

    @Test
    void registrationCannotSelectAdminRole() throws Exception {
        Map<String, Object> body = Map.of("email", "x-" + UUID.randomUUID() + "@test.example", "password", "Password123",
                "fullName", "Mallory", "accountType", "ADMIN");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void injectedRolesAreIgnoredOnRegistration() throws Exception {
        Map<String, Object> body = Map.of("email", "x-" + UUID.randomUUID() + "@test.example", "password", "Password123",
                "fullName", "Mallory", "accountType", "DONOR", "roles", new String[]{"ADMIN"});
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles").value(containsInAnyOrder("DONOR")));
    }

    @Test
    void loginGivesAccessToProfile() throws Exception {
        AuthUser donor = user(Role.DONOR);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(donor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(donor.email()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordIsRejectedWithGenericMessage() throws Exception {
        AuthUser donor = user(Role.DONOR);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", donor.email(), "password", "Wrong12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mvc.perform(get("/api/v1/donations/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void nonAdminCannotUseAdminEndpoints() throws Exception {
        AuthUser donor = user(Role.DONOR);
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(donor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesFamily() throws Exception {
        AuthUser donor = user(Role.DONOR);
        TokenResponse first = login(donor);
        String rotated = objectMapper.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", first.refreshToken()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/refreshToken").asText();

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", first.refreshToken()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", rotated))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        AuthUser donor = user(Role.DONOR);
        TokenResponse tokens = login(donor);
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedUserAccessTokenStopsWorking() throws Exception {
        AuthUser donor = user(Role.DONOR);
        AuthUser admin = user(Role.ADMIN);
        String token = bearer(donor);
        userService.changeStatus(admin, donor.id(), UserStatus.SUSPENDED, "test");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotChangeOwnRoles() throws Exception {
        AuthUser admin = user(Role.ADMIN);
        mvc.perform(put("/api/v1/admin/users/" + admin.id() + "/roles").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("roles", new String[]{"DONOR"}))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_ROLE_CHANGE_NOT_ALLOWED"));
    }

    @Test
    void creatorCannotModifyAnotherCreatorsCampaign() throws Exception {
        AuthUser admin = user(Role.ADMIN);
        AuthUser owner = user(Role.CAMPAIGN_CREATOR);
        AuthUser other = user(Role.CAMPAIGN_CREATOR);
        ActiveCampaign campaign = activeCampaign(owner, admin, 1_000_000);
        UUID otherOrg = verifiedOrganization(other, admin);
        mvc.perform(put("/api/v1/campaigns/" + campaign.id()).header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content(json(campaignRequest(otherOrg, 1_000_000))))
                .andExpect(status().isForbidden());
    }

    @Test
    void draftCampaignIsHiddenFromOtherUsers() throws Exception {
        AuthUser admin = user(Role.ADMIN);
        AuthUser owner = user(Role.CAMPAIGN_CREATOR);
        ActiveCampaign draft = draftCampaign(owner, verifiedOrganization(owner, admin), 1_000_000);
        mvc.perform(get("/api/v1/campaigns/" + draft.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/campaigns/" + draft.id()).header("Authorization", bearer(owner))).andExpect(status().isOk());
    }

    @Test
    void donorCannotReadAnotherDonorsDonation() throws Exception {
        AuthUser admin = user(Role.ADMIN);
        AuthUser creator = user(Role.CAMPAIGN_CREATOR);
        AuthUser donorA = user(Role.DONOR);
        AuthUser donorB = user(Role.DONOR);
        ActiveCampaign campaign = activeCampaign(creator, admin, 1_000_000);
        UUID donation = pendingDonation(donorA, campaign.id(), 50_000);
        mvc.perform(get("/api/v1/donations/" + donation).header("Authorization", bearer(donorB))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/donations/" + donation).header("Authorization", bearer(donorA))).andExpect(status().isOk());
    }

    @Test
    void donorCannotConfirmOwnDonation() throws Exception {
        AuthUser admin = user(Role.ADMIN);
        AuthUser creator = user(Role.CAMPAIGN_CREATOR);
        AuthUser donor = user(Role.DONOR);
        ActiveCampaign campaign = activeCampaign(creator, admin, 1_000_000);
        UUID donation = pendingDonation(donor, campaign.id(), 50_000);
        mvc.perform(post("/api/v1/admin/donations/" + donation + "/confirm").header("Authorization", bearer(donor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void errorResponsesDoNotLeakInternals() throws Exception {
        mvc.perform(get("/api/v1/campaigns/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com."))));
    }
}
