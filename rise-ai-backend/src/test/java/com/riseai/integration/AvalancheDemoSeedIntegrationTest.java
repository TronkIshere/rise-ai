package com.riseai.integration;

import com.riseai.configuration.DemoDataSeeder;
import com.riseai.dto.response.CampaignChainResponse;
import com.riseai.dto.response.ReconciliationReport;
import com.riseai.enums.DisbursementStatus;
import com.riseai.service.TransparencyService;
import com.riseai.service.impl.ChainCommandRelay;
import com.riseai.support.AbstractMySqlIntegrationTest;
import com.riseai.util.Slugs;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The demo seed with the avalanche engine, in its own database so the shared one keeps its simulated seed. Every chain call succeeds. */
@SpringBootTest(properties = {"rise.seed.enabled=true", "rise.escrow.engine=avalanche", "rise.avalanche.internal-api-key=seed-test-key",
        "rise.avalanche.beneficiary-address=0x00000000000000000000000000000000000000b0", "rise.avalanche.release-delay=PT0S",
        "rise.avalanche.retry-interval=PT0S", "rise.avalanche.relay-interval=PT1H", "rise.avalanche.relay-initial-delay=PT1H",
        "rise.avalanche.anchor-initial-delay=PT1H"})
class AvalancheDemoSeedIntegrationTest extends AbstractMySqlIntegrationTest {
    private static final String DATABASE = "rise_seed_chain";
    private static final long RELEASE_AT = 1_900_000_000L;
    private static final HttpServer GATEWAY;

    static {
        try {
            GATEWAY = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        GATEWAY.createContext("/api/v1/avalanche/", exchange -> {
            String[] path = exchange.getRequestURI().getPath().split("/");
            if ("campaigns".equals(path[4]) && "GET".equals(exchange.getRequestMethod())) {
                respond(exchange, "{\"status\":\"QUEUED\",\"releaseAt\":" + RELEASE_AT + "}");
                return;
            }
            exchange.getRequestBody().readAllBytes();
            respond(exchange, "{\"state\":\"CONFIRMED\",\"txHash\":\"0x" + "c".repeat(64) + "\"}");
        });
        GATEWAY.start();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @DynamicPropertySource
    static void separateDatabase(DynamicPropertyRegistry registry) {
        // Hikari's own settings are bound after spring.datasource.*, so they win over the shared database the base class registers.
        registry.add("spring.datasource.hikari.jdbc-url", () -> MYSQL.getJdbcUrl().replaceFirst("/" + MYSQL.getDatabaseName() + "(?=\\?|$)", "/" + DATABASE)
                + (MYSQL.getJdbcUrl().contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true");
        registry.add("spring.datasource.hikari.username", () -> "root");
        registry.add("spring.datasource.hikari.password", MYSQL::getPassword);
        registry.add("rise.avalanche.gateway-url", () -> "http://127.0.0.1:" + GATEWAY.getAddress().getPort());
    }

    @Autowired
    ChainCommandRelay relay;
    @Autowired
    TransparencyService transparencyService;

    @Test
    void theSeedRunsOnTheChainEngineAndEachReleaseWaitsForTheChain() {
        assertThat(jdbcTemplate.queryForObject("select database()", String.class)).isEqualTo(DATABASE);
        UUID education = campaign(0);
        UUID library = campaign(1);
        CampaignChainResponse.Disbursement before = transparencyService.campaignChain(null, education).disbursements().get(0);
        assertThat(before.requestStatus()).as("the seeded release waits for the chain").isEqualTo(DisbursementStatus.APPROVED);
        assertThat(before.chainStatus()).isEqualTo("QUEUING");

        for (int i = 0; i < 200 && relay.relayOnce() > 0; i++) {
            // drain the outbox
        }

        CampaignChainResponse educationChain = transparencyService.campaignChain(null, education);
        assertThat(educationChain.openStatus()).isEqualTo("CONFIRMED");
        CampaignChainResponse.Disbursement released = educationChain.disbursements().get(0);
        assertThat(released.requestStatus()).isEqualTo(DisbursementStatus.RELEASED);
        assertThat(released.chainStatus()).isEqualTo("RELEASED_ON_CHAIN");
        assertThat(released.releaseTxHash()).startsWith("0x");
        assertThat(educationChain.transactions()).extracting(CampaignChainResponse.Transaction::action)
                .contains("open", "contribution", "anchor-evidence", "decide-evidence", "reserve", "queue-release", "execute-release");

        CampaignChainResponse.Disbursement held = transparencyService.campaignChain(null, library).disbursements().get(0);
        assertThat(held.requestStatus()).isEqualTo(DisbursementStatus.DISPUTED);
        assertThat(held.chainStatus()).isEqualTo("BLOCKED_BY_DISPUTE");
        assertThat(held.releaseAt()).isEqualTo(Instant.ofEpochSecond(RELEASE_AT));
        assertThat(held.releaseTxHash()).isNull();

        assertThat(escrowQueryService.reconcileAll()).allSatisfy(report -> assertThat(report.consistent()).isTrue())
                .extracting(ReconciliationReport::ledgerEntryCount).isNotEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from chain_commands where status = 'FAILED'", Integer.class)).isZero();
    }

    private UUID campaign(int index) {
        return UUID.fromString(jdbcTemplate.queryForObject("select id from campaigns where slug = ?", String.class,
                Slugs.slugify(DemoDataSeeder.CAMPAIGN_TITLES.get(index))));
    }
}
