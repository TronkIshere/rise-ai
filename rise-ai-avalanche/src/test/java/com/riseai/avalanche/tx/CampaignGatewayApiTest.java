package com.riseai.avalanche.tx;

import com.riseai.avalanche.AvalancheGatewayApplication;
import com.riseai.avalanche.signer.SignerRegistry;
import com.riseai.avalanche.signer.SignerRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The gateway API over HTTP against the real CampaignEscrow, a real Anvil and MySQL. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AvalancheGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class CampaignGatewayApiTest {

    private static final String KEY = "test-internal-key";
    private static final String BENEFICIARY = "0x" + "b0".repeat(20);
    private static final ContractFixtures.Stack STACK;
    private static final Path MANIFEST;

    static {
        LocalStack.start();
        STACK = ContractFixtures.deployStack(LocalStack.anvilUrl());
        try {
            MANIFEST = ContractFixtures.writeManifest(Files.createTempFile("avalanche-manifest", ".json"), "LOCAL_SIMULATION", 31337, STACK, 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("avalanche.environment", () -> "LOCAL_SIMULATION");
        registry.add("avalanche.rpc.primary.url", LocalStack::anvilUrl);
        registry.add("avalanche.rpc.min-gap-ms", () -> "0");
        registry.add("avalanche.signers.enabled", () -> "true");
        registry.add("avalanche.tx.enabled", () -> "true");
        registry.add("avalanche.tx.reconcile-interval-ms", () -> "200");
        registry.add("avalanche.db.url", LocalStack::jdbcUrl);
        registry.add("avalanche.db.username", LocalStack::dbUser);
        registry.add("avalanche.db.password", LocalStack::dbPassword);
        registry.add("avalanche.deployment-manifest", () -> MANIFEST.toString());
        registry.add("avalanche.internal-api-key", () -> KEY);
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    SignerRegistry registry;
    private static boolean prepared;

    @BeforeAll
    static void clean() {
        JdbcTemplate jdbc = new JdbcTemplate(LocalStack.dataSource());
        jdbc.update("DELETE FROM avalanche_tx");
        jdbc.update("DELETE FROM avalanche_nonce");
        AnvilRpc.setAutomine(LocalStack.anvilUrl(), true);
    }

    private void grant(String role, SignerRole signer) {
        ContractFixtures.sendFromAdmin(LocalStack.anvilUrl(), STACK.escrow(), FunctionEncoder.encode(new Function("grantRole", List.of(
                new Bytes32(Numeric.hexStringToByteArray(Hash.sha3String(role))), new Address(registry.get(signer).address())), List.of())));
    }

    private synchronized void prepareChain() {
        if (prepared) {
            return;
        }
        for (SignerRole role : SignerRole.values()) {
            AnvilRpc.fund(LocalStack.anvilUrl(), registry.get(role).address());
        }
        grant("OPERATOR_ROLE", SignerRole.OPERATOR);
        grant("APPROVER_ROLE", SignerRole.APPROVER);
        prepared = true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> exchange(HttpMethod method, String path, String key, String body, int expectedStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("X-Internal-Api-Key", key);
        }
        ResponseEntity<Map> response = rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
        assertEquals(expectedStatus, response.getStatusCode().value(), path + " -> " + response.getBody());
        return response.getBody();
    }

    private Map<String, Object> get(String path) {
        return exchange(HttpMethod.GET, "/api/v1/avalanche" + path, null, null, 200);
    }

    private static String id(String seed) {
        return Hash.sha3String(seed);
    }

    private static String fresh() {
        return id(UUID.randomUUID().toString());
    }

    private static String actionPath(String campaign, String action) {
        return "/api/v1/avalanche/campaigns/" + campaign + "/actions/" + action + "/execute";
    }

    /** Sends a command and waits for its final on-chain outcome. */
    private String run(String campaign, String action, String fields) throws InterruptedException {
        String key = "op-" + UUID.randomUUID();
        String body = "{\"operationKey\":\"" + key + "\"" + (fields.isEmpty() ? "" : "," + fields) + "}";
        exchange(HttpMethod.POST, actionPath(campaign, action), KEY, body, 200);
        return await(key);
    }

    private String await(String key) throws InterruptedException {
        String state = null;
        for (int i = 0; i < 100; i++) {
            state = (String) get("/transactions/" + key).get("state");
            if (state.equals("CONFIRMED") || state.equals("FINALIZED") || state.equals("REVERTED") || state.equals("FAILED")) {
                return state;
            }
            Thread.sleep(100);
        }
        return state;
    }

    private static boolean ok(String state) {
        return state.equals("CONFIRMED") || state.equals("FINALIZED");
    }

    private void assertOk(String state) {
        assertEquals(true, ok(state), "state was " + state);
    }

    /** An open campaign with donated funds and one reservation; returns {campaign, milestone, request}. */
    private String[] reserved() throws InterruptedException {
        prepareChain();
        String campaign = fresh();
        String milestone = fresh();
        String request = fresh();
        assertOk(run(campaign, "open", "\"beneficiary\":\"" + BENEFICIARY + "\",\"target\":\"100000\",\"releaseDelay\":60,\"milestoneIds\":[\"" + milestone + "\"],\"plannedAmounts\":[\"100000\"]"));
        assertOk(run(campaign, "contribution", "\"donationId\":\"" + fresh() + "\",\"amount\":\"80000\""));
        assertOk(run(campaign, "reserve", "\"milestoneId\":\"" + milestone + "\",\"requestId\":\"" + request + "\",\"amount\":\"30000\""));
        return new String[]{campaign, milestone, request};
    }

    private void acceptEvidence(String campaign, String milestone) throws InterruptedException {
        String evidence = fresh();
        assertOk(run(campaign, "anchor-evidence", "\"milestoneId\":\"" + milestone + "\",\"evidenceId\":\"" + evidence + "\",\"contentHash\":\"" + fresh() + "\""));
        assertOk(run(campaign, "decide-evidence", "\"evidenceId\":\"" + evidence + "\",\"accepted\":true"));
    }

    private java.math.BigInteger beneficiaryBalance() {
        return new java.math.BigInteger((String) get("/assets/balance?address=" + BENEFICIARY).get("balanceBaseUnits"));
    }

    private static void passObjectionWindow() {
        AnvilRpc.call(LocalStack.anvilUrl(), "evm_increaseTime", "61");
        AnvilRpc.mine(LocalStack.anvilUrl());
    }

    @Test
    void configNamesTheSimulatedAssetAndNoRealMoney() {
        Map<String, Object> config = get("/config");
        assertEquals("RISE_VND", config.get("tokenKind"));
        assertEquals(true, config.get("isSimulated"));
        assertEquals(STACK.escrow(), config.get("escrow"));
        assertEquals(STACK.anchor(), config.get("anchor"));
    }

    @Test
    void theFullMilestoneReleaseRunsThroughTheObjectionWindow() throws Exception {
        String[] r = reserved();
        String campaign = r[0];
        String milestone = r[1];
        String request = r[2];
        acceptEvidence(campaign, milestone);
        java.math.BigInteger balanceBefore = beneficiaryBalance();

        assertOk(run(campaign, "queue-release", "\"requestId\":\"" + request + "\""));
        assertEquals("QUEUED", get("/campaigns/" + campaign + "/reservations/" + request).get("status"));

        assertEquals("REVERTED", run(campaign, "execute-release", "\"requestId\":\"" + request + "\""), "the window has not passed");
        assertEquals(balanceBefore, beneficiaryBalance(), "a refused release paid nothing");
        assertEquals("30000", get("/campaigns/" + campaign).get("reserved"), "a refused release moved nothing");

        passObjectionWindow();
        assertOk(run(campaign, "execute-release", "\"requestId\":\"" + request + "\""));
        assertEquals(balanceBefore.add(java.math.BigInteger.valueOf(30000)), beneficiaryBalance(), "the beneficiary received the release");
        Map<String, Object> c = get("/campaigns/" + campaign);
        assertEquals("ACTIVE", c.get("status"));
        assertEquals("30000", c.get("disbursed"));
        assertEquals("50000", c.get("available"));
        assertEquals("0", c.get("reserved"));
        assertEquals("30000", get("/campaigns/" + campaign + "/milestones/" + milestone).get("released"));
        assertEquals("RELEASED", get("/campaigns/" + campaign + "/reservations/" + request).get("status"));
    }

    @Test
    void anOpenDisputeStopsTheReleaseEvenAfterTheWindow() throws Exception {
        String[] r = reserved();
        acceptEvidence(r[0], r[1]);
        assertOk(run(r[0], "queue-release", "\"requestId\":\"" + r[2] + "\""));
        String dispute = fresh();
        assertOk(run(r[0], "open-dispute", "\"milestoneId\":\"" + r[1] + "\",\"disputeId\":\"" + dispute + "\""));
        passObjectionWindow();
        assertEquals("REVERTED", run(r[0], "execute-release", "\"requestId\":\"" + r[2] + "\""));
        assertEquals("0", get("/campaigns/" + r[0]).get("disbursed"));

        assertOk(run(r[0], "close-dispute", "\"disputeId\":\"" + dispute + "\""));
        assertOk(run(r[0], "execute-release", "\"requestId\":\"" + r[2] + "\""));
        assertEquals("30000", get("/campaigns/" + r[0]).get("disbursed"));
    }

    @Test
    void aReleaseCannotBeQueuedWithoutAcceptedEvidence() throws Exception {
        String[] r = reserved();
        assertEquals("REVERTED", run(r[0], "queue-release", "\"requestId\":\"" + r[2] + "\""));
        assertEquals("RESERVED", get("/campaigns/" + r[0] + "/reservations/" + r[2]).get("status"));
    }

    @Test
    void refundReturnsToTheTreasuryThroughTheApprover() throws Exception {
        prepareChain();
        String campaign = fresh();
        String donation = fresh();
        String refund = fresh();
        assertOk(run(campaign, "open", "\"beneficiary\":\"" + BENEFICIARY + "\",\"target\":\"100\",\"releaseDelay\":60,\"milestoneIds\":[\"" + fresh() + "\"],\"plannedAmounts\":[\"100\"]"));
        assertOk(run(campaign, "contribution", "\"donationId\":\"" + donation + "\",\"amount\":\"100\""));
        assertOk(run(campaign, "reserve-refund", "\"donationId\":\"" + donation + "\",\"refundId\":\"" + refund + "\",\"amount\":\"40\""));
        assertEquals("40", get("/campaigns/" + campaign).get("refundReserved"));
        assertOk(run(campaign, "record-refund", "\"refundId\":\"" + refund + "\""));
        Map<String, Object> c = get("/campaigns/" + campaign);
        assertEquals("40", c.get("refunded"));
        assertEquals("60", c.get("available"));
    }

    @Test
    void aRepeatedOperationKeyIsIdempotentAndAChangedOneConflicts() throws Exception {
        prepareChain();
        String campaign = fresh();
        String body = "{\"operationKey\":\"op-dup-" + campaign.substring(2, 18) + "\",\"beneficiary\":\"" + BENEFICIARY
                + "\",\"target\":\"5\",\"releaseDelay\":60,\"milestoneIds\":[\"" + id("dup-m") + "\"],\"plannedAmounts\":[\"5\"]}";
        Map<String, Object> first = exchange(HttpMethod.POST, actionPath(campaign, "open"), KEY, body, 200);
        Map<String, Object> second = exchange(HttpMethod.POST, actionPath(campaign, "open"), KEY, body, 200);
        assertNotNull(first.get("txHash"));
        assertEquals(first.get("txHash"), second.get("txHash"));
        assertEquals(first.get("nonce"), second.get("nonce"));
        exchange(HttpMethod.POST, actionPath(campaign, "open"), KEY, body.replace("\"target\":\"5\"", "\"target\":\"6\"").replace("[\"5\"]", "[\"6\"]"), 409);
    }

    @Test
    void mutatingCallsNeedTheInternalKey() {
        exchange(HttpMethod.POST, actionPath(fresh(), "contribution"), null, "{}", 401);
        exchange(HttpMethod.POST, actionPath(fresh(), "contribution"), "wrong-key", "{}", 401);
    }

    @Test
    void malformedCommandsAreRefusedBeforeAnythingIsSigned() {
        String op = "\"operationKey\":\"op-bad\"";
        exchange(HttpMethod.POST, actionPath(fresh(), "steal"), KEY, "{" + op + "}", 400);
        exchange(HttpMethod.POST, actionPath("0x12", "status"), KEY, "{" + op + ",\"status\":\"ACTIVE\"}", 400);
        exchange(HttpMethod.POST, actionPath(fresh(), "status"), KEY, "{" + op + ",\"status\":\"NONE\"}", 400);
        exchange(HttpMethod.POST, actionPath(fresh(), "contribution"), KEY, "{" + op + ",\"donationId\":\"" + fresh() + "\",\"amount\":\"-1\"}", 400);
        exchange(HttpMethod.POST, actionPath(fresh(), "queue-release"), KEY, "{" + op + ",\"requestId\":\"" + fresh() + "\",\"signerRole\":\"ADMIN\"}", 400);
    }

    @Test
    void anUnknownCampaignIsNotFound() {
        exchange(HttpMethod.GET, "/api/v1/avalanche/campaigns/" + fresh(), null, null, 404);
    }
}
