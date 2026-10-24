package com.aicore.eventmonitor.deduplication;

import com.aicore.eventmonitor.configuration.MonitoringProperties;
import com.aicore.eventmonitor.core.event.MonitoringEvent;
import com.aicore.eventmonitor.core.result.MonitoringResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InMemoryEventDeduplicator implements EventDeduplicator {

    public static final String STORE_TYPE = "IN_MEMORY";

    MonitoringProperties properties;
    Clock clock;
    Map<String, TrackedEvent> trackedEvents = new LinkedHashMap<>();

    @Override
    public synchronized DeduplicationOutcome register(MonitoringEvent event) {
        if (!properties.getDeduplication().isEnabled()) {
            return DeduplicationOutcome.of(DeduplicationStatus.NEW);
        }
        Instant now = clock.instant();
        purgeExpired(now);

        TrackedEvent tracked = trackedEvents.get(key(event));
        if (tracked != null) {
            if (!tracked.fingerprint.equals(event.getFingerprint())) {
                return DeduplicationOutcome.of(DeduplicationStatus.CONFLICT);
            }
            if (tracked.result == null) {
                return DeduplicationOutcome.of(DeduplicationStatus.IN_PROGRESS);
            }
            return DeduplicationOutcome.builder()
                    .status(DeduplicationStatus.DUPLICATE)
                    .previousResult(tracked.result)
                    .build();
        }

        int capacity = properties.getDeduplication().getMaxTrackedEvents();
        while (trackedEvents.size() >= capacity) {
            if (!evictOldestCompleted()) {
                return DeduplicationOutcome.of(DeduplicationStatus.CAPACITY_EXHAUSTED);
            }
        }
        trackedEvents.put(key(event), new TrackedEvent(event.getFingerprint(), now));
        return DeduplicationOutcome.of(DeduplicationStatus.NEW);
    }

    @Override
    public synchronized void complete(MonitoringEvent event, MonitoringResult result) {
        TrackedEvent tracked = trackedEvents.get(key(event));
        if (tracked != null && tracked.fingerprint.equals(event.getFingerprint()) && tracked.result == null) {
            tracked.result = result;
        }
    }

    @Override
    public synchronized void release(MonitoringEvent event) {
        TrackedEvent tracked = trackedEvents.get(key(event));
        if (tracked != null && tracked.fingerprint.equals(event.getFingerprint()) && tracked.result == null) {
            trackedEvents.remove(key(event));
        }
    }

    @Override
    public synchronized int getTrackedEventCount() {
        purgeExpired(clock.instant());
        return trackedEvents.size();
    }

    @Override
    public String getStoreType() {
        return STORE_TYPE;
    }

    private boolean evictOldestCompleted() {
        Iterator<TrackedEvent> iterator = trackedEvents.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().result != null) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private void purgeExpired(Instant now) {
        Iterator<TrackedEvent> iterator = trackedEvents.values().iterator();
        while (iterator.hasNext()) {
            TrackedEvent tracked = iterator.next();
            if (tracked.registeredAt.plus(properties.getDeduplication().getRetention()).isAfter(now)) {
                return;
            }
            iterator.remove();
        }
    }

    private String key(MonitoringEvent event) {
        return event.getSource() + '\u0000' + event.getEventId();
    }

    private static final class TrackedEvent {
        private final String fingerprint;
        private final Instant registeredAt;
        private MonitoringResult result;

        private TrackedEvent(String fingerprint, Instant registeredAt) {
            this.fingerprint = fingerprint;
            this.registeredAt = registeredAt;
        }
    }
}
