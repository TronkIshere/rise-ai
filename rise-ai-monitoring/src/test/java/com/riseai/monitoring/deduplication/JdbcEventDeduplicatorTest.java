package com.riseai.monitoring.deduplication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.event.Severity;
import com.riseai.monitoring.core.finding.FindingCategory;
import com.riseai.monitoring.core.finding.RecommendedAction;
import com.riseai.monitoring.core.result.MonitoringStatus;
import com.riseai.monitoring.dto.response.monitoring.FindingResponse;
import com.riseai.monitoring.dto.response.monitoring.MonitoringResultResponse;
import com.riseai.monitoring.testsupport.MutableClock;
import com.riseai.monitoring.testsupport.TestEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "monitoring.ai.enabled=false")
@ActiveProfiles("test")
class JdbcEventDeduplicatorTest {
    @Autowired
    JdbcClient jdbcClient;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MonitoringProperties properties;

    private MutableClock clock;
    private JdbcEventDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.now());
        deduplicator = newInstance();
    }

    private JdbcEventDeduplicator newInstance() {
        return new JdbcEventDeduplicator(jdbcClient, objectMapper, properties, clock, "jdbc:h2:file:./data/rise-ai-monitoring");
    }

    private static MonitoringEvent event() {
        return TestEvents.event().eventId(UUID.randomUUID().toString()).fingerprint("fingerprint-a").build();
    }

    private static MonitoringResultResponse response(MonitoringEvent event) {
        FindingResponse finding = FindingResponse.builder()
                .findingId("a".repeat(64))
                .contractVersion("finding.v1")
                .detector(new FindingResponse.Detector("RULE", "rise.test", "1"))
                .category(FindingCategory.EVIDENCE_INTEGRITY)
                .severity(Severity.MEDIUM)
                .subject(new FindingResponse.Subject("EVIDENCE", "evidence-1"))
                .references(Map.of("campaignId", UUID.randomUUID().toString()))
                .sourceEventIds(List.of(event.getEventId()))
                .facts(List.of(new FindingResponse.Fact("count", 3, event.getEventId()), new FindingResponse.Fact("ids", List.of("x", "y"), event.getEventId())))
                .explanation("explanation")
                .recommendedAction(RecommendedAction.REVIEW_EVIDENCE)
                .observedAt(Instant.parse("2026-10-23T11:00:00Z"))
                .build();
        return MonitoringResultResponse.builder()
                .eventId(event.getEventId())
                .source(event.getSource())
                .monitoringStatus(MonitoringStatus.COMPLETED)
                .findings(List.of(finding))
                .createdAt(Instant.parse("2026-10-23T11:00:01Z"))
                .build();
    }

    @Test
    void aCompletedResultIsReplayedByAnotherInstanceSharingTheDatabase() {
        MonitoringEvent event = event();
        assertThat(deduplicator.register(event).getStatus()).isEqualTo(DeduplicationStatus.NEW);
        assertThat(deduplicator.register(event).getStatus()).isEqualTo(DeduplicationStatus.IN_PROGRESS);
        deduplicator.complete(event, response(event));

        DeduplicationOutcome outcome = newInstance().register(event);

        assertThat(outcome.getStatus()).isEqualTo(DeduplicationStatus.DUPLICATE);
        MonitoringResultResponse replayed = outcome.getPreviousResponse();
        assertThat(replayed.getEventId()).isEqualTo(event.getEventId());
        assertThat(replayed.getMonitoringStatus()).isEqualTo(MonitoringStatus.COMPLETED);
        assertThat(replayed.getFindings()).singleElement().satisfies(finding -> {
            assertThat(finding.getFindingId()).isEqualTo("a".repeat(64));
            assertThat(finding.getSeverity()).isEqualTo(Severity.MEDIUM);
            assertThat(finding.getSubject().id()).isEqualTo("evidence-1");
            assertThat(finding.getFacts()).extracting(FindingResponse.Fact::name).containsExactly("count", "ids");
            assertThat(finding.getObservedAt()).isEqualTo(Instant.parse("2026-10-23T11:00:00Z"));
        });
    }

    @Test
    void reportsAConflictWhenTheSameIdentifierCarriesDifferentContent() {
        MonitoringEvent event = event();
        deduplicator.register(event);

        DeduplicationOutcome outcome = deduplicator.register(event.toBuilder().fingerprint("fingerprint-b").build());

        assertThat(outcome.getStatus()).isEqualTo(DeduplicationStatus.CONFLICT);
    }

    @Test
    void releasedEventsCanBeProcessedAgainButCompletedOnesCannotBeReleased() {
        MonitoringEvent released = event();
        deduplicator.register(released);
        deduplicator.release(released);
        assertThat(deduplicator.register(released).getStatus()).isEqualTo(DeduplicationStatus.NEW);

        MonitoringEvent completed = event();
        deduplicator.register(completed);
        deduplicator.complete(completed, response(completed));
        deduplicator.release(completed);
        assertThat(deduplicator.register(completed).getStatus()).isEqualTo(DeduplicationStatus.DUPLICATE);
    }

    @Test
    void anInterruptedEventIsTakenOverOnceItsInProgressTimeoutHasPassed() {
        MonitoringEvent event = event();
        deduplicator.register(event);
        assertThat(newInstance().register(event).getStatus()).isEqualTo(DeduplicationStatus.IN_PROGRESS);

        clock.advance(properties.getDeduplication().getInProgressTimeout().plusSeconds(1));

        assertThat(newInstance().register(event).getStatus()).isEqualTo(DeduplicationStatus.NEW);
        assertThat(deduplicator.register(event).getStatus()).isEqualTo(DeduplicationStatus.IN_PROGRESS);
    }

    @Test
    void forgetsEventsAfterTheRetentionWindow() {
        MonitoringEvent event = event();
        deduplicator.register(event);
        deduplicator.complete(event, response(event));

        clock.advance(properties.getDeduplication().getRetention().plus(Duration.ofSeconds(1)));

        assertThat(deduplicator.register(event).getStatus()).isEqualTo(DeduplicationStatus.NEW);
    }

    @Test
    void purgeDeletesExpiredRowsOnly() {
        MonitoringEvent expired = event();
        deduplicator.register(expired);
        clock.advance(properties.getDeduplication().getRetention().plusSeconds(1));
        MonitoringEvent fresh = event();
        deduplicator.register(fresh);

        deduplicator.purgeExpired();

        assertThat(jdbcClient.sql("select count(*) from monitoring_event_ledger where event_id = ?").param(expired.getEventId()).query(Integer.class).single()).isZero();
        assertThat(jdbcClient.sql("select count(*) from monitoring_event_ledger where event_id = ?").param(fresh.getEventId()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void refusesNewEventsWhileTheInFlightLimitIsReached() {
        int original = properties.getDeduplication().getMaxTrackedEvents();
        try {
            int inFlight = jdbcClient.sql("select count(*) from monitoring_event_ledger where completed_at is null and registered_at >= ?")
                    .param(Timestamp.from(clock.instant().minus(properties.getDeduplication().getInProgressTimeout())))
                    .query(Integer.class)
                    .single();
            properties.getDeduplication().setMaxTrackedEvents(inFlight + 1);
            assertThat(deduplicator.register(event()).getStatus()).isEqualTo(DeduplicationStatus.NEW);
            assertThat(deduplicator.register(event()).getStatus()).isEqualTo(DeduplicationStatus.CAPACITY_EXHAUSTED);
        } finally {
            properties.getDeduplication().setMaxTrackedEvents(original);
        }
    }

    @Test
    void concurrentRegistrationsAdmitExactlyOneProcessor() throws Exception {
        int threads = 16;
        MonitoringEvent event = event();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<DeduplicationStatus>> outcomes = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            outcomes.add(pool.submit(() -> {
                start.await();
                return newInstance().register(event).getStatus();
            }));
        }
        start.countDown();
        List<DeduplicationStatus> statuses = new ArrayList<>();
        for (Future<DeduplicationStatus> outcome : outcomes) {
            statuses.add(outcome.get(20, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(statuses.stream().filter(status -> status == DeduplicationStatus.NEW).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == DeduplicationStatus.IN_PROGRESS).count()).isEqualTo(threads - 1);
    }

    @Test
    void aTakeoverOfAnAbandonedEventAdmitsExactlyOneProcessor() throws Exception {
        MonitoringEvent event = event();
        deduplicator.register(event);
        clock.advance(properties.getDeduplication().getInProgressTimeout().plusSeconds(1));
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<DeduplicationStatus>> outcomes = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            outcomes.add(pool.submit(() -> {
                start.await();
                return newInstance().register(event).getStatus();
            }));
        }
        start.countDown();
        long admitted = 0;
        for (Future<DeduplicationStatus> outcome : outcomes) {
            admitted += outcome.get(20, TimeUnit.SECONDS) == DeduplicationStatus.NEW ? 1 : 0;
        }
        pool.shutdownNow();

        assertThat(admitted).isEqualTo(1);
    }

    @Test
    void reportsItsStoreTypeAndAClaimOfRestartDurabilityOnlyForADurableDatabase() {
        assertThat(deduplicator.getStoreType()).isEqualTo("JDBC");
        assertThat(deduplicator.getScope()).contains("survives restarts");

        JdbcEventDeduplicator inMemory = new JdbcEventDeduplicator(jdbcClient, objectMapper, properties, clock, "jdbc:h2:mem:rise-ai-monitoring;DB_CLOSE_DELAY=-1");
        assertThat(inMemory.getScope()).contains("in-memory database").doesNotContain("survives restarts");
    }
}
