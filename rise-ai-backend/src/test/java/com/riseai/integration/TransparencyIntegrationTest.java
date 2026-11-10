package com.riseai.integration;

import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.util.MerkleRoot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
    void theMerkleRootIsDeterministicAndOrderSensitive() {
        String root = MerkleRoot.of(List.of("a", "b", "c"));
        assertThat(MerkleRoot.of(List.of("a", "b", "c"))).isEqualTo(root);
        assertThat(MerkleRoot.of(List.of("b", "a", "c"))).isNotEqualTo(root);
        assertThat(MerkleRoot.of(List.of("a", "b", "c", "c"))).as("an odd node is paired with itself").isEqualTo(root);
        assertThat(MerkleRoot.of(List.of("a"))).isEqualTo("0xca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb");
    }
}
