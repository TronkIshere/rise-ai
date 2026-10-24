# Phase 1 — Business Hardening (PLANNED)

## Inputs & prerequisites
Phase 0 builds and all tests pass locally; acceptance checklist verified.

## Read first
`README.md`, `architecture.md`, `business-rules.md`, `state-machines.md`, `ledger-and-balance-rules.md`, `testing-strategy.md`.

## Objectives (not duplicating Phase 0)
1. Switch Hibernate to `ddl-auto: validate`; fix any mapping drift.
2. Scheduled reconciliation job over all escrow accounts with alert logging and an admin report history table.
3. Settlement policy for leftover available balance after COMPLETED/CANCELLED (pro-rata refund batch or admin-controlled reallocation), implemented as new ledger types with dual control.
4. Refund approval reserves funds (new `REFUND_RESERVED` type) to remove the approve/record race.
5. Controlled `ADJUSTMENT` ledger entries requiring two distinct admins.
6. Property-based tests for ledger invariants; organization, pagination and cancellation test coverage.
7. Optional client `Idempotency-Key` on disbursement and refund creation.
8. Campaign end-date handling (auto-close donations, scheduled transitions).
9. Query optimization: projections for list endpoints, review N+1 in campaign/donation lists.
10. Freeze API contracts for the frontend (versioned OpenAPI snapshot in repo).

## Allowed changes
New migrations (`V3+`), new services/tests, additive API fields.

## Must not change
Existing migrations; ledger append-only semantics; `EscrowEngine` method contracts (extend only); conflict-of-interest rules.

## Sequence
1 → 6 (tests first) → 2 → 4 → 3 → 5 → 7–10.

## Acceptance criteria
All new rules documented in `business-rules.md`; reconciliation job finds zero inconsistencies on seeded data; invariant property tests pass; API snapshot committed.
