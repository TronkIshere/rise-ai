package com.riseai.monitoring.deduplication;

import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.dto.response.monitoring.MonitoringResultResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "monitoring.deduplication", name = "store", havingValue = "memory")
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
            if (tracked.response == null) {
                return DeduplicationOutcome.of(DeduplicationStatus.IN_PROGRESS);
            }
            return DeduplicationOutcome.builder()
                    .status(DeduplicationStatus.DUPLICATE)
                    .previousResponse(tracked.response)
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
    public synchronized void complete(MonitoringEvent event, MonitoringResultResponse response) {
        TrackedEvent tracked = trackedEvents.get(key(event));
        if (tracked != null && tracked.fingerprint.equals(event.getFingerprint()) && tracked.response == null) {
            tracked.response = response;
        }
    }

    @Override
    public synchronized void release(MonitoringEvent event) {
        TrackedEvent tracked = trackedEvents.get(key(event));
        if (tracked != null && tracked.fingerprint.equals(event.getFingerprint()) && tracked.response == null) {
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

    @Override
    public String getScope() {
        return "source+eventId, single application instance, lost on restart";
    }

    private boolean evictOldestCompleted() {
        Iterator<TrackedEvent> iterator = trackedEvents.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().response != null) {
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
        private MonitoringResultResponse response;

        private TrackedEvent(String fingerprint, Instant registeredAt) {
            this.fingerprint = fingerprint;
            this.registeredAt = registeredAt;
        }
    }
}
