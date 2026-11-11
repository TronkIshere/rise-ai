package com.riseai.integration;

import com.riseai.enums.Role;
import com.riseai.security.AuthUser;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.util.MerkleRoot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With the default simulated engine the transparency endpoint is public and says that nothing is on-chain. */
@AutoConfigureMockMvc
class TransparencyIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Test
    void anonymousCallersSeeThatTheSimulatedEngineAnchorsNothing() throws Exception {
        mvc.perform(get("/api/v1/transparency/anchors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onChain").value(false))
                .andExpect(jsonPath("$.data.anchors").isEmpty());
    }

    @Test
    void anonymousCallersSeeThatTheSimulatedEngineHasNoChain() throws Exception {
        mvc.perform(get("/api/v1/transparency/chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onChain").value(false))
                .andExpect(jsonPath("$.data.escrowContract").doesNotExist());
    }

    @Test
    void aCampaignsChainViewIsPublicForVisibleCampaignsOnly() throws Exception {
        AuthUser creator = user(Role.CAMPAIGN_CREATOR);
        AuthUser admin = user(Role.ADMIN);
        UUID active = activeCampaign(creator, admin, 3_000_000).id();
        UUID draft = draftCampaign(creator, verifiedOrganization(creator, admin), 3_000_000).id();

        mvc.perform(get("/api/v1/transparency/campaigns/" + active))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onChain").value(false))
                .andExpect(jsonPath("$.data.openStatus").value("NOT_ON_CHAIN"))
                .andExpect(jsonPath("$.data.transactions").isEmpty());
        mvc.perform(get("/api/v1/transparency/campaigns/" + draft)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/transparency/campaigns/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void theMerkleRootIsDeterministicAndOrderSensitive() {
        String root = MerkleRoot.of(List.of("a", "b", "c"));
        assertThat(MerkleRoot.of(List.of("a", "b", "c"))).isEqualTo(root);
        assertThat(MerkleRoot.of(List.of("b", "a", "c"))).isNotEqualTo(root);
        assertThat(MerkleRoot.of(List.of("a", "b", "c", "c"))).as("an odd node is paired with itself").isEqualTo(root);
        assertThat(MerkleRoot.of(List.of("a"))).isEqualTo("0xca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb");
    }
}
