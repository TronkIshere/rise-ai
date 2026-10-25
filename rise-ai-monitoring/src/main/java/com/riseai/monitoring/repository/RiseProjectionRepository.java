package com.riseai.monitoring.repository;

import com.riseai.monitoring.rise.RiseEnvelope;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RiseProjectionRepository {
    JdbcClient jdbcClient;
    Clock clock;

    public boolean markProcessed(RiseEnvelope envelope) {
        return insert("insert into rise_processed_events (event_id, event_type, occurred_at, processed_at) values (?, ?, ?, ?)",
                envelope.eventId(), envelope.eventType(), timestamp(envelope.occurredAt()), timestamp(clock.instant()));
    }

    public void recordEvidenceHash(RiseEnvelope envelope, String evidenceId, String fileHash, String milestoneId, String campaignId) {
        insert("insert into rise_evidence_hashes (event_id, evidence_id, file_hash, milestone_id, campaign_id, occurred_at) values (?, ?, ?, ?, ?, ?)",
                envelope.eventId(), evidenceId, fileHash, milestoneId, campaignId, timestamp(envelope.occurredAt()));
    }

    public List<EvidenceHashRow> evidenceWithHash(String fileHash, String excludeEventId) {
        return jdbcClient.sql("""
                        select event_id, evidence_id, milestone_id, campaign_id, occurred_at from rise_evidence_hashes
                        where file_hash = ? and event_id <> ? order by occurred_at, event_id
                        """)
                .params(fileHash, excludeEventId)
                .query((rs, row) -> new EvidenceHashRow(rs.getString("event_id"), rs.getString("evidence_id"), rs.getString("milestone_id"),
                        rs.getString("campaign_id"), rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }

    public void recordDisbursementEvent(RiseEnvelope envelope, String requestId, String milestoneId, String campaignId, BigDecimal amount) {
        insert("insert into rise_disbursement_events (event_id, event_type, request_id, milestone_id, campaign_id, amount, occurred_at) values (?, ?, ?, ?, ?, ?, ?)",
                envelope.eventId(), envelope.eventType(), requestId, milestoneId, campaignId, amount, timestamp(envelope.occurredAt()));
    }

    public List<DisbursementEventRow> disbursementEvents(String milestoneId, String eventType, String excludeEventId) {
        return jdbcClient.sql("""
                        select event_id, request_id, amount, occurred_at from rise_disbursement_events
                        where milestone_id = ? and event_type = ? and event_id <> ? order by occurred_at, event_id
                        """)
                .params(milestoneId, eventType, excludeEventId)
                .query((rs, row) -> new DisbursementEventRow(rs.getString("event_id"), rs.getString("request_id"), rs.getBigDecimal("amount"),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }

    public int purgeOlderThan(Instant before) {
        Timestamp cutoff = timestamp(before);
        return jdbcClient.sql("delete from rise_evidence_hashes where occurred_at < ?").param(cutoff).update()
                + jdbcClient.sql("delete from rise_disbursement_events where occurred_at < ?").param(cutoff).update()
                + jdbcClient.sql("delete from rise_processed_events where processed_at < ?").param(cutoff).update();
    }

    private boolean insert(String sql, Object... params) {
        try {
            return jdbcClient.sql(sql).params(params).update() == 1;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    public record EvidenceHashRow(String eventId, String evidenceId, String milestoneId, String campaignId, Instant occurredAt) {
    }

    public record DisbursementEventRow(String eventId, String requestId, BigDecimal amount, Instant occurredAt) {
    }
}
