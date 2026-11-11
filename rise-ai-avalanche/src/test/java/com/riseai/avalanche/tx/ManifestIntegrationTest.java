package com.riseai.avalanche.tx;

import com.riseai.avalanche.chain.ChainClient;
import com.riseai.avalanche.chain.ChainClients;
import com.riseai.avalanche.contract.DeploymentManifest;
import com.riseai.avalanche.contract.ManifestVerifier;
import com.riseai.avalanche.contract.VerifiedDeployment;
import com.riseai.avalanche.env.AvalancheEnvironment;
import com.riseai.avalanche.env.EnvironmentGuardException;
import com.riseai.avalanche.signer.SignerRegistry;
import com.riseai.avalanche.signer.SignerRole;
import com.riseai.avalanche.tx.SubmissionService.SubmitRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The manifest against live chains: real deployments, the manifest produced by the real forge script, and the submission allow-list. */
@Testcontainers(disabledWithoutDocker = true)
class ManifestIntegrationTest {

    private final List<Harness> harnesses = new ArrayList<>();
    private final List<ChainClient> clients = new ArrayList<>();

    @BeforeAll
    static void start() {
        LocalStack.start();
    }

    @AfterEach
    void close() {
        harnesses.forEach(Harness::close);
        clients.forEach(ChainClient::shutdown);
    }

    private ChainClient client(String url) {
        ChainClient c = ChainClients.create(AvalancheEnvironment.LOCAL_SIMULATION, url, 5000, 0);
        clients.add(c);
        return c;
    }

    private static String refusal(Path manifest, ChainClient chain) {
        return assertThrows(EnvironmentGuardException.class, () -> ManifestVerifier.verify(DeploymentManifest.read(manifest), Harness.LOCAL, chain)).code();
    }

    @Test
    void aRealDeploymentVerifiesAndAWrongOneIsRefused(@TempDir Path dir) throws Exception {
        String url = LocalStack.anvilUrl();
        ContractFixtures.Stack stack = ContractFixtures.deployStack(url);
        ContractFixtures.Stack other = ContractFixtures.deployStack(url);
        ChainClient chain = client(url);

        Path ok = ContractFixtures.writeManifest(dir.resolve("ok.json"), "LOCAL_SIMULATION", 31337, stack, 0);
        VerifiedDeployment verified = ManifestVerifier.verify(DeploymentManifest.read(ok), Harness.LOCAL, chain);
        assertEquals(Set.of(stack.escrow(), stack.anchor()), verified.allowedTargets());

        assertEquals("MANIFEST_TOKEN_MISMATCH", refusal(ContractFixtures.writeManifest(dir.resolve("wrong-token.json"), "LOCAL_SIMULATION", 31337,
                new ContractFixtures.Stack(other.token(), stack.escrow(), stack.anchor()), 0), chain));
        assertEquals("MANIFEST_CODE_MISSING", refusal(ContractFixtures.writeManifest(dir.resolve("missing.json"), "LOCAL_SIMULATION", 31337,
                new ContractFixtures.Stack(stack.token(), "0x" + "77".repeat(20), stack.anchor()), 0), chain));
        assertEquals("MANIFEST_CODE_MISSING", refusal(ContractFixtures.writeManifest(dir.resolve("no-anchor.json"), "LOCAL_SIMULATION", 31337,
                new ContractFixtures.Stack(stack.token(), stack.escrow(), "0x" + "78".repeat(20)), 0), chain));
        assertEquals("MANIFEST_CHAIN_MISMATCH", refusal(ContractFixtures.writeManifest(dir.resolve("wrong-chain.json"), "LOCAL_SIMULATION", 43113, stack, 0), chain));
    }

    @Test
    void submissionIsRestrictedToTheManifestContracts(@TempDir Path dir) throws Exception {
        String url = LocalStack.anvilUrl();
        ContractFixtures.Stack stack = ContractFixtures.deployStack(url);
        SignerRegistry registry = Harness.newRegistry();
        AnvilRpc.fund(url, registry.get(SignerRole.RELAYER).address());
        new JdbcTemplate(LocalStack.dataSource()).update("DELETE FROM avalanche_tx");
        new JdbcTemplate(LocalStack.dataSource()).update("DELETE FROM avalanche_nonce");
        Set<String> allowed = Set.of(stack.escrow(), stack.anchor());
        Harness h = new Harness(LocalStack.dataSource(), url, 5000, Harness.props(30_000, 2), registry, null, allowed);
        harnesses.add(h);

        TxRecord ok = h.submission.submit(new SubmitRequest("allow-ok", SignerRole.RELAYER, stack.escrow().toUpperCase(Locale.ROOT).replace("0X", "0x"), ContractFixtures.PAUSED_SELECTOR, 100_000));
        assertEquals(TxState.SUBMITTED, ok.state());
        for (String refused : new String[]{"0x" + "99".repeat(20), stack.token()}) {
            SubmissionService.InvalidRequestException e = assertThrows(SubmissionService.InvalidRequestException.class,
                    () -> h.submission.submit(new SubmitRequest("allow-bad", SignerRole.RELAYER, refused, ContractFixtures.PAUSED_SELECTOR, 100_000)));
            assertTrue(e.getMessage().startsWith("TARGET_NOT_ALLOWED"), e.getMessage());
        }
        assertTrue(h.repository.find("LOCAL_SIMULATION", 31337, "allow-bad").isEmpty(), "a refused target creates no record");
        assertEquals(ok.nonce() + 1, h.allocator.peekNext("LOCAL_SIMULATION", 31337, ok.signerAddress()), "and consumes no nonce");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AVALANCHE_TEST_MANIFEST", matches = ".+")
    void theManifestProducedByTheRealForgeScriptVerifiesAgainstItsAnvil() {
        String url = System.getenv().getOrDefault("AVALANCHE_TEST_ANVIL_URL", "http://127.0.0.1:8545");
        ChainClient chain = client(url);
        DeploymentManifest manifest = DeploymentManifest.read(Path.of(System.getenv("AVALANCHE_TEST_MANIFEST")));
        VerifiedDeployment verified = ManifestVerifier.verify(manifest, Harness.LOCAL, chain);
        assertEquals("RISE_VND", verified.manifest().tokenKind());
        assertEquals(2, verified.allowedTargets().size());
    }
}
