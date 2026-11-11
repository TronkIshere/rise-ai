package com.riseai.avalanche.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.avalanche.env.AvalancheEnvironment;
import com.riseai.avalanche.env.EnvironmentGuardException;
import com.riseai.avalanche.startup.ValidatedEnvironment;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Network-free manifest rules: parsing, environment and chain binding, only simulated rVND accepted, plus ABI parity of the events. */
class ManifestVerifierTest {

    private static final String ESCROW = "0x" + "e1".repeat(20);
    private static final String TOKEN = "0x" + "a1".repeat(20);
    private static final ValidatedEnvironment LOCAL = new ValidatedEnvironment(AvalancheEnvironment.LOCAL_SIMULATION, 31337, "anvil", "127.0.0.1");
    private static final ValidatedEnvironment FUJI = new ValidatedEnvironment(AvalancheEnvironment.AVALANCHE_FUJI, 43113, "avalanchego", "api.avax-test.network");

    private static DeploymentManifest manifest(String environment, long chainId, String token, String kind) {
        return new DeploymentManifest(2, environment, chainId, ESCROW, token, kind, TOKEN, TOKEN, TOKEN, TOKEN, TOKEN, 60, "1", 0);
    }

    private static String code(DeploymentManifest m, ValidatedEnvironment v) {
        return assertThrows(EnvironmentGuardException.class, () -> ManifestVerifier.checkStatic(m, v)).code();
    }

    @Test
    void rvndIsAcceptedOnBothEnvironments() {
        ManifestVerifier.checkStatic(manifest("LOCAL_SIMULATION", 31337, TOKEN, "RISE_VND"), LOCAL);
        ManifestVerifier.checkStatic(manifest("AVALANCHE_FUJI", 43113, TOKEN, "RISE_VND"), FUJI);
    }

    @Test
    void environmentAndChainMustEqualTheVerifiedEndpoint() {
        assertEquals("MANIFEST_ENVIRONMENT_MISMATCH", code(manifest("AVALANCHE_FUJI", 43113, TOKEN, "RISE_VND"), LOCAL));
        assertEquals("MANIFEST_ENVIRONMENT_MISMATCH", code(manifest("LOCAL_SIMULATION", 43113, TOKEN, "RISE_VND"), FUJI));
        assertEquals("MANIFEST_CHAIN_MISMATCH", code(manifest("LOCAL_SIMULATION", 43113, TOKEN, "RISE_VND"), LOCAL));
        assertEquals("MANIFEST_CHAIN_MISMATCH", code(manifest("AVALANCHE_FUJI", 31337, TOKEN, "RISE_VND"), FUJI));
    }

    @Test
    void realUsdcAddressesAreDenyListedWhateverTheKind() {
        for (String denied : new String[]{"0xB97EF9Ef8734C71904D8002F8b6Bc66Dd9c48a6E", "0xA7D7079b0FEaD91F3e65f86E8915Cb59c1a4C664",
                "0x5425890298aed601595a70AB815c96711a31Bc65"}) {
            assertEquals("ASSET_DENY_LISTED", code(manifest("LOCAL_SIMULATION", 31337, denied.toLowerCase(), "RISE_VND"), LOCAL));
            assertEquals("ASSET_DENY_LISTED", code(manifest("AVALANCHE_FUJI", 43113, denied.toLowerCase(), "RISE_VND"), FUJI));
        }
    }

    @Test
    void onlyTheSimulatedTokenKindIsAccepted() {
        for (String kind : new String[]{"MOCK", "CIRCLE_FUJI_USDC", "WRAPPED", ""}) {
            assertEquals("ASSET_NOT_ALLOWED", code(manifest("LOCAL_SIMULATION", 31337, TOKEN, kind), LOCAL), kind);
        }
    }

    @Test
    void parsingRejectsIncompleteOrMalformedManifests() {
        String good = "{\"schemaVersion\":2,\"environment\":\"LOCAL_SIMULATION\",\"chainId\":31337,\"escrow\":\"" + ESCROW + "\",\"token\":\"" + TOKEN
                + "\",\"tokenKind\":\"RISE_VND\",\"anchor\":\"" + TOKEN + "\",\"admin\":\"" + TOKEN + "\",\"treasury\":\"" + TOKEN + "\",\"operator\":\"" + TOKEN
                + "\",\"approver\":\"" + TOKEN + "\",\"minReleaseDelay\":60,\"startBlock\":7}";
        assertEquals(7, DeploymentManifest.parse(good).startBlock());
        for (String bad : new String[]{"not json", "{}", good.replace("\"schemaVersion\":2", "\"schemaVersion\":1"),
                good.replace(ESCROW, "0x123"), good.replace("\"tokenKind\":\"RISE_VND\",", ""), good.replace("\"chainId\":31337,", ""),
                good.replace("\"minReleaseDelay\":60,", ""), good.replace("\"anchor\":\"" + TOKEN + "\",", "")}) {
            assertEquals("MANIFEST_INVALID", assertThrows(EnvironmentGuardException.class, () -> DeploymentManifest.parse(bad)).code(), bad);
        }
    }

    @Test
    void theEventListMatchesTheCompiledAbiExactly() throws Exception {
        var abi = new ObjectMapper().readTree(Files.readString(Path.of("contracts", "abi", "CampaignEscrow.abi.json")));
        Set<String> fromAbi = new HashSet<>();
        for (var entry : abi) {
            if ("event".equals(entry.path("type").asText()) && !entry.path("name").asText().matches("Paused|Unpaused|RoleGranted|RoleRevoked|RoleAdminChanged")) {
                StringBuilder types = new StringBuilder();
                for (var input : entry.path("inputs")) {
                    types.append(types.isEmpty() ? "" : ",").append(input.path("type").asText());
                }
                fromAbi.add(entry.path("name").asText() + "(" + types + ")");
            }
        }
        assertEquals(fromAbi, new HashSet<>(CampaignEvents.SIGNATURES));
    }
}
