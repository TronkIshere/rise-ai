package com.riseai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.dto.request.LoginRequest;
import com.riseai.enums.Role;
import com.riseai.security.AuthUser;
import com.riseai.service.AuthService;
import com.riseai.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrganizationApiIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    AuthService authService;

    AuthUser admin;
    AuthUser creator;
    AuthUser otherCreator;
    AuthUser donor;

    @BeforeEach
    void setUp() {
        admin = user(Role.ADMIN);
        creator = user(Role.CAMPAIGN_CREATOR);
        otherCreator = user(Role.CAMPAIGN_CREATOR);
        donor = user(Role.DONOR);
    }

    @Test
    void creatorCreatesAndManagesOwnOrganization() throws Exception {
        String id = create(creator, "Riverbank Literacy " + UUID.randomUUID());

        mvc.perform(get("/api/v1/organizations/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data.verificationDisclaimer").isNotEmpty());
        mvc.perform(get("/api/v1/organizations/mine").header("Authorization", bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].id").value(id));
        mvc.perform(put("/api/v1/organizations/" + id).header("Authorization", bearer(otherCreator))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Hijacked")))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/organizations").header("Authorization", bearer(donor))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Donor org")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/organizations").header("Authorization", bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"organizationType\":\"NGO\",\"contactEmail\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void verificationWorkflowAndMaterialChangeReset() throws Exception {
        String id = create(creator, "Hillside Clinic " + UUID.randomUUID());

        mvc.perform(post("/api/v1/admin/organizations/" + id + "/verify").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"too early\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mvc.perform(post("/api/v1/organizations/" + id + "/submit-verification").header("Authorization", bearer(otherCreator)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/organizations/" + id + "/submit-verification").header("Authorization", bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING_REVIEW"));
        mvc.perform(post("/api/v1/admin/organizations/" + id + "/verify").header("Authorization", bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"self\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/organizations/" + id + "/verify").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"Documents reviewed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));

        mvc.perform(put("/api/v1/organizations/" + id).header("Authorization", bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Renamed Clinic " + UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("UNVERIFIED"));
    }

    @Test
    void adminCanRejectVerificationAndOwnerCanResubmit() throws Exception {
        String id = create(creator, "Seaside Shelter " + UUID.randomUUID());
        mvc.perform(post("/api/v1/organizations/" + id + "/submit-verification").header("Authorization", bearer(creator)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/organizations/" + id + "/reject-verification").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"Missing documents\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"));
        mvc.perform(post("/api/v1/organizations/" + id + "/submit-verification").header("Authorization", bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING_REVIEW"));
    }

    @Test
    void organizationReferencedByCampaignCannotBeDeleted() throws Exception {
        UUID referenced = verifiedOrganization(creator, admin);
        draftCampaign(creator, referenced, 1_000_000);
        mvc.perform(delete("/api/v1/organizations/" + referenced).header("Authorization", bearer(creator)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        String unused = create(creator, "Temporary Group " + UUID.randomUUID());
        mvc.perform(delete("/api/v1/organizations/" + unused).header("Authorization", bearer(creator)))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/v1/organizations/" + unused))
                .andExpect(status().isNotFound());
    }

    private String create(AuthUser owner, String name) throws Exception {
        String response = mvc.perform(post("/api/v1/organizations").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.path("data").path("id").asText();
    }

    private String body(String name) throws Exception {
        return objectMapper.writeValueAsString(Map.of("name", name, "organizationType", "NGO", "contactEmail", "contact@test.example"));
    }

    private String bearer(AuthUser user) {
        return "Bearer " + authService.login(new LoginRequest(user.email(), PASSWORD)).accessToken();
    }
}
