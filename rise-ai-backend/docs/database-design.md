# Database Design — IMPLEMENTED

MySQL 8, InnoDB, `utf8mb4_unicode_ci`. Schema owned by Flyway (`src/main/resources/db/migration`); Hibernate `ddl-auto: validate` since Phase 1, so mapping drift fails at startup. `CHAR(n)` columns are mapped with `@JdbcTypeCode(SqlTypes.CHAR)`.

## Conventions
- Primary keys: UUID stored as `CHAR(36)` (`hibernate.type.preferred_uuid_jdbc_type=CHAR`).
- Timestamps: `DATETIME(6)` in UTC.
- Money: `DECIMAL(19,2)` mapped to `BigDecimal`; never float/double. Phase 0 currency is `VND` only.
- Enums: `VARCHAR`, `EnumType.STRING`.
- Mutable tables carry `version BIGINT` for optimistic locking.
- JSON columns: `gallery_image_urls`, `budget_breakdown` (campaigns), `supporting_references` (disputes), `metadata` (audit_logs).

## Tables
| Table | Purpose | Key constraints / indexes |
|---|---|---|
| users | Accounts | `uk_users_email` |
| user_roles | Enum roles per user (ADR-002) | PK(user_id, role) |
| refresh_tokens | SHA-256 hashes of refresh tokens | `uk_refresh_tokens_hash`, idx user |
| organizations | Organizations + review status | `uk_organizations_slug`, idx status, creator |
| campaigns | Campaign detail page data | unique code/slug; CHECK amounts > 0, min ≤ target, end > start; idx status, creator, org, category |
| campaign_milestones | Ordered milestones with planned/reserved/released amounts | UNIQUE(campaign_id, sequence_number); CHECK reserved+released ≤ planned |
| donations | Simulated donations | unique code; UNIQUE(donor_id, idempotency_key); CHECK refunded ≤ amount |
| escrow_accounts | Aggregate balances per campaign and currency | UNIQUE(campaign_id, currency); CHECK non-negative; CHECK available = confirmed − reserved − disbursed − refunded |
| escrow_ledger_entries | Append-only ledger (source of truth) | unique transaction_code, unique idempotency_key; CHECK amount > 0; FKs to campaign, account, donation, milestone, disbursement; append-only trigger |
| disbursement_requests | Requests against milestones | unique code; idx (milestone_id, status), (campaign_id, status) |
| refund_requests | Refund workflow | unique code; idx (donation_id, status) |
| evidence_records | Evidence metadata and simulated file refs | idx (milestone_id, status), disbursement |
| disputes | Disputes with assignment/resolution | unique code; idx (campaign_id, status), (milestone_id, status) |
| dispute_messages | Dispute responses | idx (dispute_id, created_at) |
| status_history | Every state transition | idx (entity_type, entity_id, created_at); append-only trigger |
| audit_logs | Audit trail | idx entity, actor, action, created_at, correlation_id; append-only trigger |
| reconciliation_runs (V4) | Reconciliation report history | unique run_code; CHECK counts; idx started_at, (run_status, started_at); append-only trigger |
| settlement_requests (V5) | Dual-controlled leftover settlements | unique code; CHECK amount > 0; CHECK decided_by ≠ proposed_by; idx (campaign_id, status) |
| escrow_adjustments (V5) | Dual-controlled ledger adjustments | unique code; CHECK amount > 0, direction ∈ {CREDIT, DEBIT}, decided_by ≠ requested_by; idx (campaign_id, status) |
| outbox_events (V8) | Transactional outbox of domain events for AI monitoring | UNIQUE event_id; CHECK status ∈ {PENDING, IN_FLIGHT, PUBLISHED, FAILED}, attempts ≥ 0; idx (status, next_attempt_at), (aggregate_type, aggregate_id, occurred_at); `envelope` MEDIUMTEXT holds the frozen JSON envelope; optimistic `version`. Mutable (relay status); published rows purged after 30 days |
| monitoring_findings (V9) | Advisory findings from rise-ai-monitoring, with review state | UNIQUE finding_key (64-hex `findingId`); CHECK severity, review_status, counts ≥ 0; FK reviewed_by → users; idx (review_status, severity, last_seen_at), (campaign_id, last_seen_at); `facts` JSON; optimistic `version` |
| monitoring_finding_occurrences (V9) | One row per (finding, source event) | UNIQUE (finding_id, source_event_id); FK finding_id; idx (finding_id, observed_at); append-only triggers |

Phase 1 columns:
- `escrow_accounts.total_refund_reserved`, `total_adjustment_credits`, `total_adjustment_debits` (V3). V3 also redefines the non-negative CHECK and the balance identity CHECK.
- `refund_requests.funds_reserved` (V3).
- `disbursement_requests.idempotency_key` and `refund_requests.idempotency_key`, each UNIQUE together with `requested_by` (V6).
- `campaigns.fundraising_closed_at` and index `(campaign_status, end_at)` (V7).

## Additional tables (justification)
`refresh_tokens` (revocable sessions), `refund_requests` (spec 8.8 requires requested/approved/recorded distinction), `dispute_messages` (spec "submit responses"), `status_history` (milestone/disbursement/dispute history endpoints). Phase 2 adds `outbox_events` (reliable event delivery without dual writes, ADR-004) and the two monitoring tables (advisory findings and their per-event occurrences, ADR-007). No business table references the monitoring tables.

## Budget strategy
- `campaigns.budget_breakdown` JSON array of `{item, amount, note}`; when non-empty its sum must equal `target_amount`.
- Milestone `planned_amount` values must sum to `target_amount` before submission and may never exceed it.

## Migrations
- `V1__init_schema.sql` — all tables.
- `V2__append_only_guards.sql` — triggers. Requires `log_bin_trust_function_creators=1` (set in `docker-compose.yml`) when binary logging is on and the user lacks SUPER.
- `V3__refund_reservation_and_adjustment_balances.sql` — new escrow aggregates, redefined CHECKs, `refund_requests.funds_reserved`.
- `V4__reconciliation_runs.sql` — run history table and its append-only triggers.
- `V5__settlements_and_adjustments.sql` — settlement and adjustment request tables.
- `V6__request_idempotency_keys.sql` — idempotency keys for disbursement and refund requests.
- `V7__campaign_fundraising_close.sql` — `fundraising_closed_at` and the end-date index.
- `V8__outbox_events.sql` — transactional outbox (Phase 2).
- `V9__monitoring_findings.sql` — findings, occurrences and the occurrence append-only triggers (Phase 2).

V1–V9 were applied to an empty MySQL 8.4 database by the packaged application on 2026-10-25 (Flyway warns that MySQL 8.4 is newer than its tested range; migration succeeded).

Never edit applied migrations; add `V3__…`.
