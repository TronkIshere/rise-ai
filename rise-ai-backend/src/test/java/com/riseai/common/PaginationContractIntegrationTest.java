package com.riseai.common;

import com.riseai.security.AuthDtos.LoginRequest;
import com.riseai.security.AuthService;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PaginationContractIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    AuthService authService;

    @Test
    void pagedResponsesExposeTheDocumentedEnvelope() throws Exception {
        AuthUser admin = user(Role.ADMIN);
        AuthUser creator = user(Role.CAMPAIGN_CREATOR);
        AuthUser donor = user(Role.DONOR);
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        for (int i = 0; i < 5; i++) {
            confirmedDonation(donor, admin, campaign.id(), 100_000 + i);
        }

        mvc.perform(get("/api/v1/campaigns/" + campaign.id() + "/donations?page=1&size=2&sort=amount,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.currentPage").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.data", hasSize(2)))
                .andExpect(jsonPath("$.data.data[0].amount").value(100_002))
                .andExpect(jsonPath("$.data.data[1].amount").value(100_003))
                .andExpect(jsonPath("$.data.data[0].donorId").doesNotExist());
        mvc.perform(get("/api/v1/campaigns/" + campaign.id() + "/donations?page=9&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(5));
    }

    @Test
    void pageSizeIsCappedAtOneHundredAndDefaultsToTen() throws Exception {
        mvc.perform(get("/api/v1/campaigns?size=500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(100));
        mvc.perform(get("/api/v1/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.currentPage").value(0));
    }

    @Test
    void unknownSortPropertyIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/campaigns?sort=passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void adminListsSupportStatusFilteringAndPaging() throws Exception {
        AuthUser admin = user(Role.ADMIN);
        AuthUser creator = user(Role.CAMPAIGN_CREATOR);
        AuthUser donor = user(Role.DONOR);
        ActiveCampaign campaign = activeCampaign(creator, admin, 10_000_000);
        pendingDonation(donor, campaign.id(), 100_000);
        pendingDonation(donor, campaign.id(), 100_000);

        mvc.perform(get("/api/v1/admin/donations?status=PENDING&size=1").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.data[0].donationStatus").value("PENDING"));
        mvc.perform(get("/api/v1/admin/donations?status=NOT_A_STATUS").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/campaigns/" + campaign.id() + "/donations/manage?size=10").header("Authorization", bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/v1/campaigns/" + UUID.randomUUID() + "/donations"))
                .andExpect(status().isNotFound());
    }

    private String bearer(AuthUser user) {
        return "Bearer " + authService.login(new LoginRequest(user.email(), PASSWORD)).accessToken();
    }
}
