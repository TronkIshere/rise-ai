package com.riseai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.dto.request.CreateRefundRequest;
import com.riseai.dto.request.OpenDisputeRequest;
import com.riseai.dto.request.ResolveDisputeRequest;
import com.riseai.enums.DisbursementStatus;
import com.riseai.enums.DisputeType;
import com.riseai.enums.ResolutionOutcome;
import com.riseai.enums.Role;
import com.riseai.repository.EscrowLedgerRepository;
import com.riseai.security.AuthUser;
import com.riseai.service.impl.ChainCommandRelay;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.util.ChainValues;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** The avalanche engine against a stub gateway: the MySQL ledger, the command order, and the chain-first release. */
class AvalancheEscrowEngineIntegrationTest extends AbstractMySqlIntegrationTest {
    private static final String KEY = "gateway-test-key";
    private static final String BENEFICIARY = "0x" + "b0".repeat(20);
    private static final HttpServer GATEWAY;
    private static final HttpServer DATA_API;
    private static final List<String> DATA_API_PATHS = new CopyOnWriteArrayList<>();
    private static volatile String dataApiBody = "{\"transactions\":[]}";
    private static final String OPERATOR = "0x" + "0a".repeat(20);
    private static final String APPROVER = "0x" + "0b".repeat(20);
    private static final String GUARDIAN = "0x" + "6a".repeat(20);
    private static final List<Sent> SENT = new CopyOnWriteArrayList<>();
    private static final Map<String, String> REVERT_ACTIONS = new ConcurrentHashMap<>();
    private static final Map<String, String> KEY_TO_ACTION = new ConcurrentHashMap<>();
    private static volatile boolean down;
    private static volatile int openHolds;
    private static volatile Long receiptConfirmedAt;
    private static volatile boolean reservesAttested;
    private static final String ZERO = "0x" + "0".repeat(64);
    private static final Map<String, List<String[]>> ANCHORS = new ConcurrentHashMap<>();
    private static final List<JsonNode> ANCHOR_REQUESTS = new CopyOnWriteArrayList<>();
    private static final Set<String> REVERTED_KEYS = ConcurrentHashMap.newKeySet();

    private record Sent(String campaign, String action, JsonNode body, String apiKey) {
    }

    static {
        try {
            GATEWAY = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        ObjectMapper mapper = new ObjectMapper();
        GATEWAY.createContext("/api/v1/avalanche/", exchange -> {
            if (down) {
                respond(exchange, 503, "{\"error\":\"CHAIN_UNAVAILABLE\"}");
                return;
            }
            String[] path = exchange.getRequestURI().getPath().split("/");
            if ("anchors".equals(path[4])) {
                String kind = path[5];
                List<String[]> history = ANCHORS.computeIfAbsent(kind, k -> new CopyOnWriteArrayList<>());
                if ("POST".equals(exchange.getRequestMethod())) {
                    JsonNode body = mapper.readTree(exchange.getRequestBody());
                    String key = body.path("operationKey").asText();
                    String expected = history.isEmpty() ? ZERO : history.get(history.size() - 1)[0];
                    long lastCount = history.isEmpty() ? 0 : Long.parseLong(history.get(history.size() - 1)[1]);
                    ANCHOR_REQUESTS.add(body);
                    if (body.path("previousRoot").asText().equals(expected) && body.path("recordCount").asLong() >= lastCount) {
                        history.add(new String[]{body.path("root").asText(), body.path("recordCount").asText()});
                    } else {
                        REVERTED_KEYS.add(key);
                    }
                    KEY_TO_ACTION.put(key, "anchor");
                    respond(exchange, 200, "{\"state\":\"SUBMITTED\",\"txHash\":\"0xanchor\"}");
                    return;
                }
                String body = history.isEmpty()
                        ? "{\"anchorContract\":\"0xa1\",\"count\":0}"
                        : "{\"anchorContract\":\"0xa1\",\"count\":" + history.size() + ",\"root\":\"" + history.get(history.size() - 1)[0]
                        + "\",\"recordCount\":" + history.get(history.size() - 1)[1] + ",\"anchoredAt\":1700000000}";
                respond(exchange, 200, body);
                return;
            }
            if ("config".equals(path[4])) {
                respond(exchange, 200, "{\"environment\":\"AVALANCHE_FUJI\",\"chainId\":43113,\"escrow\":\"0xe5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5\",\"token\":\"0x70\",\"tokenKind\":\"RISE_VND\","
                        + "\"anchor\":\"0xa1\",\"contractVersion\":\"2\",\"paused\":false,\"guardianAddress\":\"" + GUARDIAN + "\",\"guardianHoldMaxSeconds\":1209600,"
                        + "\"operatorAddress\":\"" + OPERATOR + "\",\"approverAddress\":\"" + APPROVER + "\"}");
                return;
            }
            if ("campaigns".equals(path[4]) && "GET".equals(exchange.getRequestMethod())) {
                String kind = path.length > 6 ? path[6] : "";
                if ("milestones".equals(kind)) {
                    respond(exchange, 200, "{\"openHolds\":" + openHolds + "}");
                } else if ("payouts".equals(kind)) {
                    respond(exchange, 200, "{\"recorded\":true,\"bankReferenceHash\":\"0xbb\",\"recordedAt\":1700000000,\"confirmedAt\":"
                            + receiptConfirmedAt + "}");
                } else {
                    respond(exchange, 200, "{\"status\":\"QUEUED\",\"releaseAt\":1900000000}");
                }
                return;
            }
            if ("reserves".equals(path[4])) {
                respond(exchange, 200, reservesAttested
                        ? "{\"escrow\":\"0xe5\",\"currentHeld\":\"500000000\",\"attested\":true,\"period\":3,\"attestedAt\":1700000000,"
                        + "\"attester\":\"0x6a\",\"bankBalance\":\"490000000\",\"heldAtAttestation\":\"500000000\",\"gap\":\"10000000\","
                        + "\"statementHash\":\"0x5a\"}"
                        : "{\"escrow\":\"0xe5\",\"currentHeld\":\"500000000\",\"attested\":false}");
                return;
            }
            if ("transactions".equals(path[4]) && REVERTED_KEYS.contains(path[5])) {
                respond(exchange, 200, "{\"state\":\"REVERTED\",\"txHash\":\"0xabc\"}");
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                JsonNode body = mapper.readTree(exchange.getRequestBody());
                SENT.add(new Sent(path[5], path[7], body, exchange.getRequestHeaders().getFirst("X-Internal-Api-Key")));
                KEY_TO_ACTION.put(body.path("operationKey").asText(), path[7]);
                respond(exchange, 200, "{\"state\":\"SUBMITTED\",\"txHash\":\"0xabc\"}");
                return;
            }
            String action = KEY_TO_ACTION.get(path[5]);
            String state = action != null && REVERT_ACTIONS.containsKey(action) ? "REVERTED" : "CONFIRMED";
            respond(exchange, 200, "{\"state\":\"" + state + "\",\"txHash\":\"0xabc\"}");
        });
        GATEWAY.start();
        try {
            DATA_API = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        DATA_API.createContext("/v1/", exchange -> {
            DATA_API_PATHS.add(exchange.getRequestURI().toString());
            if (exchange.getRequestURI().getQuery() != null && exchange.getRequestURI().getQuery().contains("pageToken=broken")) {
                respond(exchange, 503, "{}");
                return;
            }
            respond(exchange, 200, dataApiBody);
        });
        DATA_API.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @DynamicPropertySource
    static void avalanche(DynamicPropertyRegistry registry) {
        registry.add("rise.escrow.engine", () -> "avalanche");
        registry.add("rise.avalanche.gateway-url", () -> "http://127.0.0.1:" + GATEWAY.getAddress().getPort());
        registry.add("rise.avalanche.internal-api-key", () -> KEY);
        registry.add("rise.avalanche.beneficiary-address", () -> BENEFICIARY);
        registry.add("rise.avalanche.release-delay", () -> "PT0S");
        registry.add("rise.avalanche.retry-interval", () -> "PT0S");
        registry.add("rise.avalanche.relay-interval", () -> "PT1H");
        registry.add("rise.avalanche.relay-initial-delay", () -> "PT1H");
        registry.add("rise.avalanche.anchor-initial-delay", () -> "PT1H");
        registry.add("rise.avalanche.anchor-lag", () -> "PT0S");
        registry.add("rise.avalanche.data-api-url", () -> "http://127.0.0.1:" + DATA_API.getAddress().getPort() + "/v1");
    }

    @Autowired
    ChainCommandRelay relay;
    @Autowired
    com.riseai.service.impl.ChainAnchorRelay anchorRelay;
    @Autowired
    com.riseai.service.TransparencyService transparencyService;
    @Autowired
    EscrowLedgerRepository ledgerRepository;
    @Autowired
    com.riseai.service.ChainActivityService chainActivityService;

    AuthUser creator;
    AuthUser admin;
    AuthUser donor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from chain_commands");
        jdbcTemplate.update("delete from chain_anchors");
        ANCHORS.clear();
        ANCHOR_REQUESTS.clear();
        REVERTED_KEYS.clear();
        SENT.clear();
        REVERT_ACTIONS.clear();
        KEY_TO_ACTION.clear();
        down = false;
        openHolds = 0;
        receiptConfirmedAt = null;
        reservesAttested = false;
        creator = user(Role.CAMPAIGN_CREATOR);
        admin = user(Role.ADMIN);
        donor = user(Role.DONOR);
    }

    private void drain() {
        for (int i = 0; i < 60; i++) {
            if (relay.relayOnce() == 0) {
                return;
            }
        }
    }

    private List<String> actions() {
        return SENT.stream().map(Sent::action).toList();
    }

    private Sent sent(String action) {
        return SENT.stream().filter(s -> s.action().equals(action)).reduce((first, second) -> second).orElseThrow();
    }

    private DisbursementStatus statusOf(UUID request) {
        return disbursementRepository.findById(request).orElseThrow().getRequestStatus();
    }

    private String commandStatus(UUID reference, String action) {
        return jdbcTemplate.queryForObject("select status from chain_commands where reference_id = ? and action = ? order by seq desc limit 1",
                String.class, reference.toString(), action);
    }

    private ActiveCampaign funded(long... milestones) {
        ActiveCampaign campaign = activeCampaign(creator, admin, milestones);
        confirmedDonation(donor, admin, campaign.id(), 5_000_000);
        milestoneService.start(creator, campaign.milestones().get(0));
        return campaign;
    }

    @Test
    void commandsMirrorTheLedgerInOrderWithExactAmounts() {
        ActiveCampaign campaign = funded(3_000_000, 2_000_000);
        UUID request = requestFunds2(campaign);
        drain();

        assertThat(actions()).containsSubsequence("open", "contribution", "anchor-evidence", "reserve");
        Sent open = sent("open");
        assertThat(open.campaign()).isEqualTo(ChainValues.bytes32(campaign.id()));
        assertThat(open.apiKey()).isEqualTo(KEY);
        assertThat(open.body().path("beneficiary").asText()).isEqualTo(BENEFICIARY);
        assertThat(open.body().path("target").asText()).isEqualTo("500000000");
        assertThat(open.body().path("plannedAmounts").get(0).asText()).isEqualTo("300000000");
        assertThat(open.body().path("milestoneIds").get(1).asText()).isEqualTo(ChainValues.bytes32(campaign.milestones().get(1)));
        assertThat(sent("contribution").body().path("amount").asText()).isEqualTo("500000000");
        assertThat(sent("reserve").body().path("requestId").asText()).isEqualTo(ChainValues.bytes32(request));
        assertThat(sent("reserve").body().path("operationKey").asText()).startsWith("cmd-");
    }

    private UUID requestFunds2(ActiveCampaign campaign) {
        submittedEvidence(creator, campaign.milestones().get(0));
        return requestFunds(creator, campaign.milestones().get(0), 1_000_000);
    }

    private UUID approved(ActiveCampaign campaign) {
        acceptedEvidence(creator, admin, campaign.milestones().get(0));
        return approvedRequest(creator, admin, campaign.milestones().get(0), 1_000_000);
    }

    @Test
    void aReleaseIsRecordedOnlyAfterTheChainHasExecutedIt() {
        ActiveCampaign campaign = funded(3_000_000);
        UUID request = approved(campaign);
        drain();

        disbursementService.release(admin, request);
        assertThat(statusOf(request)).isEqualTo(DisbursementStatus.APPROVED);
        assertThat(ledgerRepository.existsByIdempotencyKey("RELEASE:" + request)).isFalse();

        drain();

        assertThat(actions()).containsSubsequence("queue-release", "execute-release");
        assertThat(statusOf(request)).isEqualTo(DisbursementStatus.RELEASED);
        assertThat(ledgerRepository.existsByIdempotencyKey("RELEASE:" + request)).isTrue();
        assertThat(disbursementRepository.findById(request).orElseThrow().getReleasedBy()).isEqualTo(admin.id());
    }

    @Test
    void askingForTheSameReleaseTwiceQueuesItOnce() {
        ActiveCampaign campaign = funded(3_000_000);
        UUID request = approved(campaign);
        drain();
        disbursementService.release(admin, request);
        disbursementService.release(admin, request);
        assertThat(jdbcTemplate.queryForObject("select count(*) from chain_commands where reference_id = ? and action = 'queue-release'", Integer.class, request.toString())).isEqualTo(1);
    }

    @Test
    void aRevertedExecutionKeepsTheRequestApprovedAndIsTriedAgain() {
        ActiveCampaign campaign = funded(3_000_000);
        UUID request = approved(campaign);
        drain();
        disbursementService.release(admin, request);
        REVERT_ACTIONS.put("execute-release", "window");
        for (int i = 0; i < 8; i++) {
            relay.relayOnce();
        }
        assertThat(statusOf(request)).isEqualTo(DisbursementStatus.APPROVED);
        assertThat(SENT.stream().filter(s -> s.action().equals("execute-release")).map(s -> s.body().path("operationKey").asText()).distinct().count()).isGreaterThan(1);

        REVERT_ACTIONS.clear();
        drain();
        assertThat(statusOf(request)).isEqualTo(DisbursementStatus.RELEASED);
    }

    @Test
    void aRejectedRequestCancelsTheQueuedReleaseOnChain() {
        ActiveCampaign campaign = funded(3_000_000);
        UUID request = approved(campaign);
        drain();
        REVERT_ACTIONS.put("execute-release", "window");
        disbursementService.release(admin, request);
        relay.relayOnce();
        relay.relayOnce();
        disbursementService.reject(admin, request, "No longer valid");
        drain();

        assertThat(actions()).contains("cancel-reservation");
        assertThat(commandStatus(request, "execute-release")).isEqualTo("CANCELLED");
        assertThat(statusOf(request)).isEqualTo(DisbursementStatus.REJECTED);
        assertThat(ledgerRepository.existsByIdempotencyKey("RELEASE:" + request)).isFalse();
    }

    @Test
    void anOpenDisputeIsMirroredAndHoldsTheReleaseUntilClosed() {
        ActiveCampaign campaign = funded(3_000_000);
        UUID request = approved(campaign);
        drain();
        REVERT_ACTIONS.put("execute-release", "window");
        disbursementService.release(admin, request);
        relay.relayOnce();
        relay.relayOnce();

        UUID dispute = disputeService.open(donor, campaign.id(), new OpenDisputeRequest(campaign.milestones().get(0), null, DisputeType.MISSING_EVIDENCE, "Concern", "Details", null)).id();
        drain();
        assertThat(actions()).contains("open-dispute");
        assertThat(statusOf(request)).isEqualTo(DisbursementStatus.DISPUTED);

        REVERT_ACTIONS.clear();
        relay.relayOnce();
        assertThat(statusOf(request)).as("the relay waits while the request is disputed").isEqualTo(DisbursementStatus.DISPUTED);

        AuthUser reviewer = user(Role.ADMIN);
        disputeService.assign(admin, dispute, reviewer.id());
        disputeService.resolve(reviewer, dispute, new ResolveDisputeRequest(ResolutionOutcome.DISMISSED, "Not supported"));
        drain();
        assertThat(actions()).contains("close-dispute");
        assertThat(statusOf(request)).isEqualTo(DisbursementStatus.RELEASED);
    }

    @Test
    void recordingARefundSendsOneTransactionNotARelease() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 3_000_000);
        UUID donation = confirmedDonation(donor, admin, campaign.id(), 1_000_000);
        UUID refund = refundService.request(donor, donation, new CreateRefundRequest(vnd(400_000), "Changed my mind")).id();
        AuthUser reviewer = user(Role.ADMIN);
        refundService.approve(reviewer, refund, "ok");
        refundService.record(reviewer, refund);
        drain();

        assertThat(actions()).containsSubsequence("reserve-refund", "record-refund").doesNotContain("release-refund-reservation");
        assertThat(sent("reserve-refund").body().path("amount").asText()).isEqualTo("40000000");
    }

    @Test
    void aFailedCommandAlertsAndLaterCommandsStillRun() {
        activeCampaign(creator, admin, 3_000_000);
        REVERT_ACTIONS.put("open", "bad");
        drain();
        assertThat(jdbcTemplate.queryForObject("select count(*) from chain_commands where action = 'open' and status = 'FAILED'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from alert_notifications where kind = 'CHAIN_COMMAND_FAILED'", Integer.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aGatewayOutageLeavesCommandsQueuedAndTheyDrainAfterwards() {
        down = true;
        ActiveCampaign campaign = activeCampaign(creator, admin, 3_000_000);
        relay.relayOnce();
        assertThat(commandStatus(campaign.id(), "open")).isEqualTo("PENDING");
        down = false;
        drain();
        assertThat(commandStatus(campaign.id(), "open")).isEqualTo("CONFIRMED");
    }

    @Test
    void aRolledBackActionLeavesNoCommand() {
        ActiveCampaign campaign = activeCampaign(creator, admin, 3_000_000);
        assertError(() -> requestFunds(creator, campaign.milestones().get(0), 1_000_000), com.riseai.exception.ErrorCode.INVALID_STATE_TRANSITION);
        assertThat(jdbcTemplate.queryForObject("select count(*) from chain_commands where action = 'reserve'", Integer.class)).isZero();
    }

    private com.riseai.dto.response.TransparencyReport.Anchor report(String kind) {
        return transparencyService.anchors().anchors().stream().filter(a -> a.kind().equals(kind)).findFirst().orElseThrow();
    }

    @Test
    void eachHistoryIsAnchoredAndVerifiesAgainstTheChain() {
        activeCampaign(creator, admin, 3_000_000);
        anchorRelay.anchorOnce();
        anchorRelay.anchorOnce();

        assertThat(ANCHOR_REQUESTS).extracting(n -> n.path("previousRoot").asText()).contains(ZERO);
        var ledger = report("LEDGER");
        var audit = report("AUDIT");
        assertThat(ledger.status()).isEqualTo("CONSISTENT");
        assertThat(audit.status()).isEqualTo("CONSISTENT");
        assertThat(audit.recomputedRoot()).isEqualTo(audit.anchoredRoot()).startsWith("0x").hasSize(66);
        assertThat(audit.anchoredRecordCount()).isEqualTo(audit.currentRecordCount());
        assertThat(jdbcTemplate.queryForObject("select count(*) from chain_anchors where status = 'CONFIRMED'", Integer.class)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void theNextAnchorChainsOnThePreviousRootAndNothingNewMeansNoAnchor() {
        activeCampaign(creator, admin, 3_000_000);
        anchorRelay.anchorOnce();
        anchorRelay.anchorOnce();
        int requests = ANCHOR_REQUESTS.size();
        anchorRelay.anchorOnce();
        assertThat(ANCHOR_REQUESTS).as("no new records, no new anchor").hasSize(requests);

        String firstAuditRoot = ANCHORS.get("AUDIT").get(0)[0];
        user(Role.DONOR);
        activeCampaign(creator, admin, 2_000_000);
        anchorRelay.anchorOnce();
        anchorRelay.anchorOnce();
        assertThat(ANCHORS.get("AUDIT")).hasSize(2);
        assertThat(ANCHOR_REQUESTS).anySatisfy(n -> assertThat(n.path("previousRoot").asText()).isEqualTo(firstAuditRoot));
        assertThat(report("AUDIT").status()).isEqualTo("CONSISTENT");
    }

    @Test
    void aBackdatedRecordInTheDatabaseIsDetected() {
        activeCampaign(creator, admin, 3_000_000);
        anchorRelay.anchorOnce();
        anchorRelay.anchorOnce();
        assertThat(report("AUDIT").status()).isEqualTo("CONSISTENT");

        jdbcTemplate.update("insert into audit_logs (id, action, entity_type, created_at) values (?, 'FORGED_ENTRY', 'CAMPAIGN', '2000-01-01 00:00:00')",
                UUID.randomUUID().toString());

        assertThat(report("AUDIT").status()).isEqualTo("MISMATCH");
    }

    @Test
    void aForkedAnchorIsRefusedAndTheNextPassStartsFromTheChain() {
        activeCampaign(creator, admin, 3_000_000);
        ANCHORS.put("AUDIT", new CopyOnWriteArrayList<>(List.<String[]>of(new String[]{"0x" + "ab".repeat(32), "1"})));
        jdbcTemplate.update("insert into chain_anchors (id, kind, record_count, root, previous_root, status, created_at, updated_at) "
                + "values (?, 'AUDIT', 1, ?, ?, 'PENDING', utc_timestamp(6), utc_timestamp(6))", UUID.randomUUID().toString(), "0x" + "cd".repeat(32), ZERO);
        anchorRelay.anchorOnce();
        anchorRelay.anchorOnce();
        assertThat(jdbcTemplate.queryForObject("select count(*) from chain_anchors where kind = 'AUDIT' and status = 'FAILED'", Integer.class)).isEqualTo(1);
        anchorRelay.anchorOnce();
        anchorRelay.anchorOnce();
        assertThat(ANCHORS.get("AUDIT")).hasSize(2);
        assertThat(ANCHORS.get("AUDIT").get(1)[0]).isEqualTo(report("AUDIT").recomputedRoot());
    }

    private com.riseai.dto.response.CampaignChainResponse.Disbursement chainView(ActiveCampaign campaign, UUID request) {
        return transparencyService.campaignChain(null, campaign.id()).disbursements().stream()
                .filter(d -> d.disbursementId().equals(request)).findFirst().orElseThrow();
    }

    @Test
    void theChainViewFollowsAReleaseThroughTheObjectionWindow() {
        ActiveCampaign campaign = funded(3_000_000);
        UUID request = approved(campaign);
        drain();
        assertThat(transparencyService.campaignChain(null, campaign.id()).openStatus()).isEqualTo("CONFIRMED");
        assertThat(chainView(campaign, request).chainStatus()).isEqualTo("RESERVED_ON_CHAIN");

        REVERT_ACTIONS.put("execute-release", "window");
        disbursementService.release(admin, request);
        for (int i = 0; i < 4; i++) {
            relay.relayOnce();
        }
        com.riseai.dto.response.CampaignChainResponse.Disbursement waiting = chainView(campaign, request);
        assertThat(waiting.chainStatus()).isEqualTo("IN_OBJECTION_WINDOW");
        assertThat(waiting.releaseAt()).isEqualTo(java.time.Instant.ofEpochSecond(1_900_000_000L));
        assertThat(waiting.queueTxHash()).isEqualTo("0xabc");
        assertThat(waiting.releaseTxHash()).isNull();

        REVERT_ACTIONS.clear();
        drain();
        com.riseai.dto.response.CampaignChainResponse.Disbursement released = chainView(campaign, request);
        assertThat(released.chainStatus()).isEqualTo("RELEASED_ON_CHAIN");
        assertThat(released.requestStatus()).isEqualTo(DisbursementStatus.RELEASED);
        assertThat(released.releaseTxHash()).isEqualTo("0xabc");
        assertThat(transparencyService.campaignChain(null, campaign.id()).transactions())
                .allSatisfy(tx -> assertThat(tx.explorerUrl()).isEqualTo(tx.txHash() == null ? null : "https://testnet.snowtrace.io/tx/" + tx.txHash()));
    }

    @Test
    void aGuardianHoldShowsInTheChainViewAndKeepsTheReleaseWaiting() {
        ActiveCampaign campaign = funded(3_000_000);
        UUID request = approved(campaign);
        drain();
        REVERT_ACTIONS.put("execute-release", "held");
        openHolds = 1;
        disbursementService.release(admin, request);
        for (int i = 0; i < 4; i++) {
            relay.relayOnce();
        }
        com.riseai.dto.response.CampaignChainResponse.Disbursement held = chainView(campaign, request);
        assertThat(held.chainStatus()).isEqualTo("HELD_BY_GUARDIAN");
        assertThat(held.openGuardianHolds()).isEqualTo(1);
        assertThat(held.requestStatus()).as("a hold is chain-side; the backend request stays approved").isEqualTo(DisbursementStatus.APPROVED);
        assertThat(statusOf(request)).isEqualTo(DisbursementStatus.APPROVED);
    }

    @Test
    void afterAReleaseThePayoutIsRecordedAndTheBeneficiarysConfirmationShows() {
        ActiveCampaign campaign = funded(3_000_000);
        UUID request = approved(campaign);
        drain();
        disbursementService.release(admin, request);
        drain();

        assertThat(actions()).containsSubsequence("queue-release", "execute-release", "record-payout");
        Sent payout = sent("record-payout");
        assertThat(payout.body().path("requestId").asText()).isEqualTo(ChainValues.bytes32(request));
        assertThat(payout.body().path("bankReferenceHash").asText()).isEqualTo(ChainValues.simulatedPayoutReference(request));
        com.riseai.dto.response.CampaignChainResponse.Disbursement unconfirmed = chainView(campaign, request);
        assertThat(unconfirmed.payoutTxHash()).isEqualTo("0xabc");
        assertThat(unconfirmed.receiptConfirmedAt()).isNull();

        receiptConfirmedAt = 1_700_000_500L;
        assertThat(chainView(campaign, request).receiptConfirmedAt()).isEqualTo(java.time.Instant.ofEpochSecond(1_700_000_500L));
    }

    @Test
    void reservesShowTheGuardiansAttestationInVnd() {
        com.riseai.dto.response.ReservesResponse none = transparencyService.reserves();
        assertThat(none.onChain()).isTrue();
        assertThat(none.attested()).isFalse();
        assertThat(none.currentHeld()).isEqualByComparingTo("5000000");

        reservesAttested = true;
        com.riseai.dto.response.ReservesResponse attested = transparencyService.reserves();
        assertThat(attested.attested()).isTrue();
        assertThat(attested.period()).isEqualTo(3L);
        assertThat(attested.bankBalance()).isEqualByComparingTo("4900000");
        assertThat(attested.heldAtAttestation()).isEqualByComparingTo("5000000");
        assertThat(attested.gap()).isEqualByComparingTo("100000");
        assertThat(attested.attestedAt()).isEqualTo(java.time.Instant.ofEpochSecond(1_700_000_000L));

        down = true;
        assertThat(transparencyService.reserves().available()).isFalse();
    }

    private static String indexed(String hash, String from, String selector, boolean success, String transfers) {
        return "{\"nativeTransaction\":{\"chainId\":\"43113\",\"blockNumber\":\"60816125\",\"txHash\":\"" + hash + "\",\"txStatus\":\""
                + (success ? "1" : "0") + "\",\"blockTimestamp\":1794284747,\"from\":{\"address\":\"" + from + "\"},\"to\":{\"address\":\"0xE5\"},"
                + "\"method\":{\"callType\":\"CONTRACT_CALL\",\"methodHash\":\"" + selector + "\"},\"value\":\"0\"},\"erc20Transfers\":[" + transfers + "]}";
    }

    @Test
    void theDataApiActivityIsCheckedAgainstThePlatformsRecords() {
        funded(3_000_000);
        drain();
        String toBeneficiary = "{\"from\":{\"address\":\"0xE5\"},\"to\":{\"address\":\"" + BENEFICIARY.toUpperCase().replace("0X", "0x")
                + "\"},\"value\":\"1000000000\",\"erc20Token\":{\"symbol\":\"rVND\",\"decimals\":2}}";
        dataApiBody = "{\"transactions\":["
                + indexed("0xABC", OPERATOR, "0x7c466f55", true, "") + ","
                + indexed("0x" + "11".repeat(32), GUARDIAN, "0x069a31ba", true, "") + ","
                + indexed("0x" + "22".repeat(32), OPERATOR, "0x2197d12c", false, "") + ","
                + indexed("0x" + "33".repeat(32), APPROVER, "0x2197d12c", true, toBeneficiary)
                + "],\"nextPageToken\":\"next-1\"}";

        com.riseai.dto.response.ChainActivityResponse activity = chainActivityService.activity(null);
        assertThat(activity.available()).isTrue();
        assertThat(activity.source()).isEqualTo("AVALANCHE_DATA_API");
        assertThat(activity.escrowContract()).isEqualTo("0xe5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5");
        assertThat(activity.nextPageToken()).isEqualTo("next-1");
        assertThat(activity.items()).extracting(com.riseai.dto.response.ChainActivityResponse.Item::check)
                .containsExactly("RECORDED", "INDEPENDENT", "REVERTED", "UNRECORDED");
        assertThat(activity.items()).extracting(com.riseai.dto.response.ChainActivityResponse.Item::method)
                .containsExactly("recordContribution", "placeHold", "executeRelease", "executeRelease");
        assertThat(activity.items()).extracting(com.riseai.dto.response.ChainActivityResponse.Item::signer)
                .containsExactly("PLATFORM", "GUARDIAN", "PLATFORM", "PLATFORM");
        assertThat(activity.unrecordedCount()).isEqualTo(1);
        assertThat(activity.items().get(3).rvndToBeneficiary()).isEqualByComparingTo("10000000");
        assertThat(activity.items().get(0).explorerUrl()).isEqualTo("https://testnet.snowtrace.io/tx/0xabc");
        assertThat(DATA_API_PATHS).anySatisfy(path -> assertThat(path).startsWith("/v1/chains/43113/addresses/0xe5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5/transactions?pageSize=25"));

        assertThat(chainActivityService.activity("broken").available()).as("a Data API outage is reported, never guessed").isFalse();
    }

    @Test
    void theChainConfigNamesTheNetworkContractsAndExplorer() {
        com.riseai.dto.response.ChainConfigResponse config = transparencyService.chainConfig();
        assertThat(config.onChain()).isTrue();
        assertThat(config.available()).isTrue();
        assertThat(config.network()).isEqualTo("AVALANCHE_FUJI");
        assertThat(config.chainId()).isEqualTo(43113L);
        assertThat(config.escrowContract()).isEqualTo("0xe5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5");
        assertThat(config.anchorContract()).isEqualTo("0xa1");
        assertThat(config.beneficiaryAddress()).isEqualTo(BENEFICIARY);
        assertThat(config.explorerAddressUrl()).isEqualTo("https://testnet.snowtrace.io/address/");
        assertThat(config.guardianAddress()).isEqualTo(GUARDIAN);
        assertThat(config.guardianHoldMaxSeconds()).isEqualTo(14L * 24 * 3600);
    }

    @Test
    void theCampaignsCommandsWaitForTheirHead() {
        ActiveCampaign campaign = funded(3_000_000);
        relay.relayOnce();
        assertThat(actions()).containsExactly("open");
        assertThat(Set.copyOf(SENT.stream().map(Sent::campaign).toList())).containsExactly(ChainValues.bytes32(campaign.id()));
    }
}
