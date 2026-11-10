package com.riseai.service.impl;

import com.riseai.util.MerkleRoot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Canonical rows of the three append-only histories that are anchored on-chain. The ledger and audit log are protected by
 * triggers, and finding occurrences record every time monitoring raised a concern, so none of them should ever change. A
 * row's canonical form is fixed here; changing it would make every earlier anchor fail verification.
 */
@Component
@RequiredArgsConstructor
public class AnchorRecords {
    public static final List<String> KINDS = List.of("LEDGER", "AUDIT", "FINDINGS");
    private static final String TIME = "'%Y-%m-%dT%H:%i:%s.%f'";

    private record Source(String table, String columns) {
    }

    private static final Map<String, Source> SOURCES = Map.of(
            "LEDGER", new Source("escrow_ledger_entries", "id, transaction_code, campaign_id, ifnull(donation_id, ''), ifnull(milestone_id, ''), "
                    + "ifnull(disbursement_id, ''), transaction_type, amount, currency, direction, status, idempotency_key, reference_type, reference_id, "
                    + "date_format(created_at, " + TIME + ")"),
            "AUDIT", new Source("audit_logs", "id, ifnull(actor_user_id, ''), action, entity_type, ifnull(entity_id, ''), ifnull(previous_state, ''), "
                    + "ifnull(new_state, ''), ifnull(correlation_id, ''), ifnull(ip_address, ''), ifnull(cast(metadata as char), ''), "
                    + "date_format(created_at, " + TIME + ")"),
            "FINDINGS", new Source("monitoring_finding_occurrences", "id, finding_id, source_event_id, ifnull(actor_id, ''), severity, "
                    + "date_format(observed_at, " + TIME + "), date_format(created_at, " + TIME + ")"));

    private final JdbcTemplate jdbc;

    /** Rows old enough to have committed in order; younger rows wait for the next anchor. */
    public long countOlderThan(String kind, Duration lag) {
        Long count = jdbc.queryForObject("select count(*) from " + source(kind).table() + " where created_at < timestampadd(MICROSECOND, ?, utc_timestamp(6))",
                Long.class, -lag.toNanos() / 1000);
        return count == null ? 0 : count;
    }

    public long countAll(String kind) {
        Long count = jdbc.queryForObject("select count(*) from " + source(kind).table(), Long.class);
        return count == null ? 0 : count;
    }

    /** Root over the first {@code count} rows in stored order, or null when fewer rows exist than were anchored. */
    public String root(String kind, long count) {
        Source source = source(kind);
        List<String> rows = jdbc.queryForList("select concat_ws('|', " + source.columns() + ") from " + source.table() + " order by created_at, id limit ?",
                String.class, count);
        return rows.size() < count ? null : MerkleRoot.of(rows);
    }

    private static Source source(String kind) {
        Source source = SOURCES.get(kind);
        if (source == null) {
            throw new IllegalArgumentException("Unknown anchor kind " + kind);
        }
        return source;
    }
}
