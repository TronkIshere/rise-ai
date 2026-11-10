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
    private static final List<Sent> SENT = new CopyOnWriteArrayList<>();
    private static final Map<String, String> REVERT_ACTIONS = new ConcurrentHashMap<>();
    private static final Map<String, String> KEY_TO_ACTION = new ConcurrentHashMap<>();
    private static volatile boolean down;

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
    }

    @Autowired
    ChainCommandRelay relay;
    @Autowired
    EscrowLedgerRepository ledgerRepository;

    AuthUser creator;
    AuthUser admin;
    AuthUser donor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from chain_commands");
        SENT.clear();
        REVERT_ACTIONS.clear();
        KEY_TO_ACTION.clear();
        down = false;
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

    @Test
    void theCampaignsCommandsWaitForTheirHead() {
        ActiveCampaign campaign = funded(3_000_000);
        relay.relayOnce();
        assertThat(actions()).containsExactly("open");
        assertThat(Set.copyOf(SENT.stream().map(Sent::campaign).toList())).containsExactly(ChainValues.bytes32(campaign.id()));
    }
}
