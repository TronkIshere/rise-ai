# ADR-002 — Database, Keys, Roles and Ledger

## Context
Financial records must be traceable, non-negative, reconcilable and tamper-resistant; schema must be versioned.

## Decision
- MySQL 8 + Flyway; Hibernate `ddl-auto: none`.
- UUID primary keys stored as `CHAR(36)` (readable, non-enumerable, consistent with uploaded code).
- Roles as enum in `user_roles` (no `roles` table).
- Money: `DECIMAL(19,2)`/`BigDecimal`, single currency VND in Phase 0.
- Append-only `escrow_ledger_entries` as source of truth; `escrow_accounts` as transactional aggregates with CHECK constraints including the balance identity.
- Reservation on request creation; compensation via `RESERVATION_CANCELLED`.
- Triggers block UPDATE/DELETE on ledger, audit and status history.
- Generic `status_history` table for all entities.

## Alternatives
BIGINT auto-increment ids (simpler, but enumerable); relational roles table (redundant for 3 fixed roles); balance computed only from ledger (slower reads, harder locking); event sourcing (too heavy).

## Rationale
Defense in depth: service checks + DB constraints + idempotency keys + triggers.

## Consequences
CHAR(36) keys use more space than BINARY(16). Triggers require `log_bin_trust_function_creators=1` on servers with binary logging. Schema mismatches are detected at query time until `validate` is enabled (Phase 1).
