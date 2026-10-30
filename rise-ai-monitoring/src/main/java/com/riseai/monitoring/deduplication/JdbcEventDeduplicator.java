package com.riseai.monitoring.deduplication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riseai.monitoring.configuration.MonitoringProperties;
import com.riseai.monitoring.core.event.MonitoringEvent;
import com.riseai.monitoring.dto.response.monitoring.MonitoringResultResponse;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "monitoring.deduplication", name = "store", havingValue = "jdbc", matchIfMissing = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JdbcEventDeduplicator implements EventDeduplicator {

    public static final String STORE_TYPE = "JDBC";
    private static final String IN_MEMORY_DATABASE = "jdbc:h2:mem:";
    private static final int REGISTRATION_ATTEMPTS = 3;

    JdbcClient jdbcClient;
    ObjectMapper objectMapper;
    MonitoringProperties properties;
    Clock clock;
    boolean durable;

    public JdbcEventDeduplicator(JdbcClient jdbcClient, ObjectMapper objectMapper, MonitoringProperties properties, Clock clock,
                                 @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.durable = !datasourceUrl.startsWith(IN_MEMORY_DATABASE);
        if (!durable) {
            log.warn("Event deduplication is stored in an in-memory database and is lost when this process stops; set MONITORING_DB_URL to a file or server database for durability");
        }
    }

    @Override
    public DeduplicationOutcome register(MonitoringEvent event) {
        if (!properties.getDeduplication().isEnabled()) {
            return DeduplicationOutcome.of(DeduplicationStatus.NEW);
        }
        Instant now = clock.instant();
        deleteExpired(event, now);
        if (inFlightCount(now) >= properties.getDeduplication().getMaxTrackedEvents()) {
            return DeduplicationOutcome.of(DeduplicationStatus.CAPACITY_EXHAUSTED);
        }
        for (int attempt = 0; attempt < REGISTRATION_ATTEMPTS; attempt++) {
            try {
                jdbcClient.sql("insert into monitoring_event_ledger (source, event_id, fingerprint, registered_at) values (?, ?, ?, ?)")
                        .params(event.getSource(), event.getEventId(), event.getFingerprint(), Timestamp.from(now))
                        .update();
                return DeduplicationOutcome.of(DeduplicationStatus.NEW);
            } catch (DuplicateKeyException ex) {
                Optional<DeduplicationOutcome> outcome = outcomeForExisting(event, now);
                if (outcome.isPresent()) {
                    return outcome.get();
                }
            }
        }
        return DeduplicationOutcome.of(DeduplicationStatus.IN_PROGRESS);
    }

    @Override
    public void complete(MonitoringEvent event, MonitoringResultResponse response) {
        jdbcClient.sql("update monitoring_event_ledger set completed_at = ?, result_json = ? where source = ? and event_id = ? and fingerprint = ? and completed_at is null")
                .params(Timestamp.from(clock.instant()), serialize(response), event.getSource(), event.getEventId(), event.getFingerprint())
                .update();
    }

    @Override
    public void release(MonitoringEvent event) {
        jdbcClient.sql("delete from monitoring_event_ledger where source = ? and event_id = ? and fingerprint = ? and completed_at is null")
                .params(event.getSource(), event.getEventId(), event.getFingerprint())
                .update();
    }

    @Override
    public int getTrackedEventCount() {
        return jdbcClient.sql("select count(*) from monitoring_event_ledger where registered_at >= ?")
                .param(Timestamp.from(clock.instant().minus(properties.getDeduplication().getRetention())))
                .query(Integer.class)
                .single();
    }

    @Override
    public String getStoreType() {
        return STORE_TYPE;
    }

    @Override
    public String getScope() {
        return durable
                ? "source+eventId, shared by every instance using the monitoring database, survives restarts"
                : "source+eventId, in-memory database, lost when the process stops";
    }

    @Scheduled(cron = "${monitoring.deduplication.purge-cron:0 45 4 * * *}")
    public void purgeExpired() {
        int deleted = jdbcClient.sql("delete from monitoring_event_ledger where registered_at < ?")
                .param(Timestamp.from(clock.instant().minus(properties.getDeduplication().getRetention())))
                .update();
        if (deleted > 0) {
            log.info("Purged {} event ledger rows past retention", deleted);
        }
    }

    private Optional<DeduplicationOutcome> outcomeForExisting(MonitoringEvent event, Instant now) {
        Optional<LedgerRow> existing = jdbcClient.sql("select fingerprint, registered_at, completed_at, result_json from monitoring_event_ledger where source = ? and event_id = ?")
                .params(event.getSource(), event.getEventId())
                .query((rs, row) -> new LedgerRow(rs.getString("fingerprint"), rs.getTimestamp("registered_at").toInstant(),
                        rs.getTimestamp("completed_at") != null, rs.getString("result_json")))
                .optional();
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        LedgerRow row = existing.get();
        if (!row.fingerprint().equals(event.getFingerprint())) {
            return Optional.of(DeduplicationOutcome.of(DeduplicationStatus.CONFLICT));
        }
        if (row.completed()) {
            return Optional.of(DeduplicationOutcome.builder().status(DeduplicationStatus.DUPLICATE).previousResponse(deserialize(row.resultJson())).build());
        }
        return Optional.of(abandoned(row, now) && takeOver(event, now)
                ? DeduplicationOutcome.of(DeduplicationStatus.NEW)
                : DeduplicationOutcome.of(DeduplicationStatus.IN_PROGRESS));
    }

    private boolean abandoned(LedgerRow row, Instant now) {
        return row.registeredAt().plus(properties.getDeduplication().getInProgressTimeout()).isBefore(now);
    }

    private boolean takeOver(MonitoringEvent event, Instant now) {
        Instant staleBefore = now.minus(properties.getDeduplication().getInProgressTimeout());
        return jdbcClient.sql("update monitoring_event_ledger set registered_at = ? where source = ? and event_id = ? and fingerprint = ? and completed_at is null and registered_at < ?")
                .params(Timestamp.from(now), event.getSource(), event.getEventId(), event.getFingerprint(), Timestamp.from(staleBefore))
                .update() == 1;
    }

    private void deleteExpired(MonitoringEvent event, Instant now) {
        jdbcClient.sql("delete from monitoring_event_ledger where source = ? and event_id = ? and registered_at < ?")
                .params(event.getSource(), event.getEventId(), Timestamp.from(now.minus(properties.getDeduplication().getRetention())))
                .update();
    }

    private int inFlightCount(Instant now) {
        return jdbcClient.sql("select count(*) from monitoring_event_ledger where completed_at is null and registered_at >= ?")
                .param(Timestamp.from(now.minus(properties.getDeduplication().getInProgressTimeout())))
                .query(Integer.class)
                .single();
    }

    private String serialize(MonitoringResultResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Monitoring result could not be stored for deduplication", ex);
        }
    }

    private MonitoringResultResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, MonitoringResultResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored monitoring result could not be read", ex);
        }
    }

    private record LedgerRow(String fingerprint, Instant registeredAt, boolean completed, String resultJson) {
    }
}
