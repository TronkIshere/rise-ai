package com.riseai.monitoring.deduplication;

import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.core.result.MonitoringResult;
import com.riseai.monitoring.core.result.MonitoringStatus;
import com.riseai.monitoring.testsupport.MutableClock;
import com.riseai.monitoring.testsupport.TestEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventDeduplicatorTest {

    private MonitoringProperties properties;
    private MutableClock clock;
    private InMemoryEventDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        properties = TestEvents.properties();
        clock = new MutableClock(TestEvents.NOW);
        deduplicator = new InMemoryEventDeduplicator(properties, clock);
    }

    @Test
    void replaysTheStoredResultForAnIdenticalEvent() {
        MonitoringEvent event = TestEvents.event().build();
        MonitoringResult result = MonitoringResult.builder().eventId("evt-1").monitoringStatus(MonitoringStatus.COMPLETED).build();

        assertThat(deduplicator.register(event).getStatus()).isEqualTo(DeduplicationStatus.NEW);
        assertThat(deduplicator.register(event).getStatus()).isEqualTo(DeduplicationStatus.IN_PROGRESS);
        deduplicator.complete(event, result);

        DeduplicationOutcome outcome = deduplicator.register(event);
        assertThat(outcome.getStatus()).isEqualTo(DeduplicationStatus.DUPLICATE);
        assertThat(outcome.getPreviousResult()).isSameAs(result);
    }

    @Test
    void reportsAConflictWhenTheSameIdentifierCarriesDifferentContent() {
        deduplicator.register(TestEvents.event().build());

        DeduplicationOutcome outcome = deduplicator.register(TestEvents.event().fingerprint("other").build());

        assertThat(outcome.getStatus()).isEqualTo(DeduplicationStatus.CONFLICT);
    }

    @Test
    void scopesIdentifiersBySource() {
        deduplicator.register(TestEvents.event().build());

        assertThat(deduplicator.register(TestEvents.event().source("another-source").build()).getStatus())
                .isEqualTo(DeduplicationStatus.NEW);
    }

    @Test
    void releasedEventsCanBeProcessedAgain() {
        MonitoringEvent event = TestEvents.event().build();
        deduplicator.register(event);

        deduplicator.release(event);

        assertThat(deduplicator.register(event).getStatus()).isEqualTo(DeduplicationStatus.NEW);
    }

    @Test
    void forgetsEventsAfterTheRetentionWindow() {
        MonitoringEvent event = TestEvents.event().build();
        deduplicator.register(event);
        deduplicator.complete(event, MonitoringResult.builder().build());

        clock.advance(properties.getDeduplication().getRetention().plus(Duration.ofSeconds(1)));

        assertThat(deduplicator.getTrackedEventCount()).isZero();
        assertThat(deduplicator.register(event).getStatus()).isEqualTo(DeduplicationStatus.NEW);
    }

    @Test
    void evictsTheOldestCompletedEntryWhenCapacityIsReached() {
        properties.getDeduplication().setMaxTrackedEvents(2);
        MonitoringEvent first = TestEvents.event().eventId("a").build();
        MonitoringEvent inFlight = TestEvents.event().eventId("b").build();
        deduplicator.register(first);
        deduplicator.complete(first, MonitoringResult.builder().build());
        deduplicator.register(inFlight);

        assertThat(deduplicator.register(TestEvents.event().eventId("c").build()).getStatus()).isEqualTo(DeduplicationStatus.NEW);
        assertThat(deduplicator.getTrackedEventCount()).isEqualTo(2);
        assertThat(deduplicator.register(inFlight).getStatus()).isEqualTo(DeduplicationStatus.IN_PROGRESS);
        assertThat(deduplicator.register(first).getStatus()).isEqualTo(DeduplicationStatus.CAPACITY_EXHAUSTED);
    }

    @Test
    void neverEvictsInProgressEntriesToMakeRoom() {
        properties.getDeduplication().setMaxTrackedEvents(1);
        MonitoringEvent inFlight = TestEvents.event().eventId("a").build();
        deduplicator.register(inFlight);

        assertThat(deduplicator.register(TestEvents.event().eventId("b").build()).getStatus())
                .isEqualTo(DeduplicationStatus.CAPACITY_EXHAUSTED);
        assertThat(deduplicator.register(inFlight).getStatus()).isEqualTo(DeduplicationStatus.IN_PROGRESS);
    }

    @Test
    void concurrentRegistrationsAdmitExactlyOneProcessor() throws Exception {
        int threads = 16;
        MonitoringEvent event = TestEvents.event().build();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<DeduplicationStatus>> outcomes = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            outcomes.add(pool.submit(() -> {
                start.await();
                return deduplicator.register(event).getStatus();
            }));
        }
        start.countDown();
        List<DeduplicationStatus> statuses = new ArrayList<>();
        for (Future<DeduplicationStatus> outcome : outcomes) {
            statuses.add(outcome.get(10, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(statuses.stream().filter(status -> status == DeduplicationStatus.NEW).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == DeduplicationStatus.IN_PROGRESS).count()).isEqualTo(threads - 1);
    }

    @Test
    void reportsItsStoreType() {
        assertThat(deduplicator.getStoreType()).isEqualTo("IN_MEMORY");
    }

    @Test
    void treatsEveryEventAsNewWhenDisabled() {
        properties.getDeduplication().setEnabled(false);
        MonitoringEvent event = TestEvents.event().build();

        deduplicator.register(event);

        assertThat(deduplicator.register(event).getStatus()).isEqualTo(DeduplicationStatus.NEW);
    }
}
