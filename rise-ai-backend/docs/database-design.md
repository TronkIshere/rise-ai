# Database Design — IMPLEMENTED

MySQL 8, InnoDB, `utf8mb4_unicode_ci`. Schema owned by Flyway (`src/main/resources/db/migration`); Hibernate `ddl-auto: none`.

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

## Additional tables (justification)
`refresh_tokens` (revocable sessions), `refund_requests` (spec 8.8 requires requested/approved/recorded distinction), `dispute_messages` (spec "submit responses"), `status_history` (milestone/disbursement/dispute history endpoints). No other tables exist.

## Budget strategy
- `campaigns.budget_breakdown` JSON array of `{item, amount, note}`; when non-empty its sum must equal `target_amount`.
- Milestone `planned_amount` values must sum to `target_amount` before submission and may never exceed it.

## Migrations
- `V1__init_schema.sql` — all tables.
- `V2__append_only_guards.sql` — triggers. Requires `log_bin_trust_function_creators=1` (set in `docker-compose.yml`) when binary logging is on and the user lacks SUPER.

Never edit applied migrations; add `V3__…`.
