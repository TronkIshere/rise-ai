package com.riseai.configuration;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.riseai.client.AvalancheGatewayClient;
import com.riseai.repository.ChainAnchorRepository;
import com.riseai.repository.ChainCommandRepository;
import com.riseai.repository.DisbursementRepository;
import com.riseai.service.AlertNotifier;
import com.riseai.service.DisbursementService;
import com.riseai.service.impl.AnchorRecords;
import com.riseai.service.impl.ChainAnchorRelay;
import com.riseai.service.impl.ChainCommandRelay;
import com.riseai.service.impl.ChainCommandWriter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FujiReadOnlyTest {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    private AvalancheEscrowProperties properties(String url) {
        return new AvalancheEscrowProperties(URI.create(url), "unit-test-internal", null, null,
                null, null, null, null, null, null, null, null);
    }

    @Test
    void directRelaysDoNotReadOrWriteSqlOrCallTheGateway() {
        var commands = mock(ChainCommandRepository.class);
        var disbursements = mock(DisbursementRepository.class);
        var gateway = mock(AvalancheGatewayClient.class);
        var writer = mock(ChainCommandWriter.class);
        var business = mock(DisbursementService.class);
        var alerts = mock(AlertNotifier.class);
        var tx = mock(PlatformTransactionManager.class);
        var anchors = mock(ChainAnchorRepository.class);
        var records = mock(AnchorRecords.class);
        var properties = properties("http://127.0.0.1:9");
        var relay = new ChainCommandRelay(commands, disbursements, gateway, writer, business, alerts, properties, tx);
        var anchor = new ChainAnchorRelay(anchors, records, gateway, alerts, properties, tx);
        ReflectionTestUtils.setField(relay, "readOnly", true);
        ReflectionTestUtils.setField(anchor, "readOnly", true);
        assertThat(relay.relayOnce()).isZero();
        anchor.anchorOnce();
        verifyNoInteractions(commands, disbursements, gateway, writer, business, alerts, tx, anchors, records);
    }

    @Test
    void gatewayClientNeverSendsPostButStillAllowsGetInReadOnlyMode() throws Exception {
        var posts = new AtomicInteger();
        var gets = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestMethod().equals("POST")) posts.incrementAndGet();
            else gets.incrementAndGet();
            byte[] response = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new AvalancheGatewayClient(properties("http://127.0.0.1:" + server.getAddress().getPort()), mapper);
            ReflectionTestUtils.setField(gateway, "readOnly", true);
            var execute = gateway.execute("campaign", "reserve", "{}", "operation");
            var anchor = gateway.anchor("LEDGER", "root", "previous", 1, "anchor-operation");
            assertThat(execute.outcome()).isEqualTo(AvalancheGatewayClient.Outcome.REJECTED);
            assertThat(anchor.outcome()).isEqualTo(AvalancheGatewayClient.Outcome.REJECTED);
            assertThat(execute.error()).contains("read-only");
            assertThat(posts).hasValue(0);
            gateway.transaction("operation");
            assertThat(gets).hasValue(1);
            ReflectionTestUtils.setField(gateway, "readOnly", false);
            gateway.execute("campaign", "reserve", "{}", "normal-operation");
            assertThat(posts).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void everyHttpMutationIncludingLoginIsDeniedBeforeTheApplication() throws Exception {
        var filter = new FujiReadOnlyFilter(mapper);
        for (String method : new String[]{"POST", "PUT", "PATCH", "DELETE", "TRACE"}) {
            var reached = new AtomicInteger();
            var response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest(method, "/api/v1/auth/login"), response,
                    (request, result) -> reached.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("ACCESS_DENIED", "read-only");
            assertThat(reached).hasValue(0);
        }
        for (String method : new String[]{"GET", "HEAD", "OPTIONS"}) {
            var reached = new AtomicInteger();
            filter.doFilter(new MockHttpServletRequest(method, "/api/v1/transparency/chain"),
                    new MockHttpServletResponse(), (request, result) -> reached.incrementAndGet());
            assertThat(reached).hasValue(1);
        }
    }

    @Test
    void readOnlySuppressesSchedulersAndSeederEvenWhenTheirOwnFlagsAreEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(ChainCommandScheduler.class, ScheduledJobs.class, OutboxRelayScheduler.class,
                        AlertDispatchScheduler.class, DemoDataSeeder.class)
                .withPropertyValues("rise.avalanche.read-only=true", "rise.escrow.engine=avalanche",
                        "rise.jobs.enabled=true", "rise.seed.enabled=true", "rise.monitoring.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChainCommandScheduler.class)
                            .doesNotHaveBean(ScheduledJobs.class).doesNotHaveBean(OutboxRelayScheduler.class)
                            .doesNotHaveBean(AlertDispatchScheduler.class).doesNotHaveBean(DemoDataSeeder.class);
                });
    }

    @Test
    void readOnlyHttpFilterDoesNotExistByDefault() {
        new ApplicationContextRunner().withUserConfiguration(FujiReadOnlyFilter.class)
                .run(context -> assertThat(context).doesNotHaveBean(FujiReadOnlyFilter.class));
    }

    @Test
    void regularAvalancheModeRetainsItsSchedulerByDefault() {
        var relay = mock(ChainCommandRelay.class);
        var anchors = mock(ChainAnchorRelay.class);
        new ApplicationContextRunner().withUserConfiguration(ChainCommandScheduler.class)
                .withBean(ChainCommandRelay.class, () -> relay).withBean(ChainAnchorRelay.class, () -> anchors)
                .withPropertyValues("rise.escrow.engine=avalanche")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChainCommandScheduler.class);
                    context.getBean(ChainCommandScheduler.class).relay();
                    context.getBean(ChainCommandScheduler.class).anchor();
                    verify(relay).relayOnce();
                    verify(anchors).anchorOnce();
                });
    }
}
