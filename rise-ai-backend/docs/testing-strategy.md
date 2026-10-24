# Testing Strategy

> Test files are **IMPLEMENTED** but were **not executed** by the generating agent. Run them locally.

## Layout
| File | Type | Covers |
|---|---|---|
| `escrow/SimulatedEscrowEngineTest` | unit (Mockito) | balance math, reservation/release/refund, insufficient balance, idempotency, frozen account, invariant check, entry direction/references |
| `security/JwtServiceTest` | unit | signing, tampering, wrong secret, expiry, short secret |
| `security/AuthSecurityIntegrationTest` | MockMvc + MySQL | ADMIN not registrable, injected roles ignored, login/profile, generic login errors, 401/403 shapes, refresh rotation and reuse detection, logout, suspended user, self role change, cross-creator edits, hidden drafts, donation IDOR, donor cannot confirm, no internals in errors |
| `campaign/CampaignLifecycleIntegrationTest` | MySQL | DRAFT start, milestone budget rules, verified org + admin approval, invalid transitions, edit lock, donation eligibility, self-donation, evidence ownership, sequential milestones, status history, completion with reserved funds |
| `escrow/EscrowLifecycleIntegrationTest` | MySQL | full donation→release flow with reconciliation and audit, pending donations, double confirmation, minimum donation, idempotency key, evidence required, over-budget, over-balance, one open request, accepted evidence for approval, dual-role self-review, duplicate release, rejection returns reservation, milestone completion |
| `dispute/DisputeAndRefundIntegrationTest` | MySQL | dispute hold/dismiss/uphold, eligibility, opener cannot review, duplicate disputes, withdrawal, refund limits, refund recording and duplicates, refunds cannot use reserved funds, self-approval |
| `escrow/ConcurrencyIntegrationTest` | MySQL, multithreaded | concurrent release, concurrent requests, concurrent confirmations (no lost update), double confirmation race |
| `config/DemoDataSeederIntegrationTest` | MySQL | seed completeness, idempotent re-run, all accounts reconcile; Phase 1: the scheduled reconciliation job (wired with `rise.jobs.enabled=true`) finds zero inconsistencies on seeded data |
| `escrow/LedgerInvariantPropertiesTest` (Phase 1) | jqwik property, unit | 300 random sequences of every engine operation (incl. replays and status changes): no negative aggregate, balance identity, ledger-recomputed balances equal stored aggregates, rejected operations change nothing, unique idempotency keys |
| `settlement/ProRataAllocationPropertiesTest` (Phase 1) | jqwik property, unit | allocation sums to the amount, never exceeds a weight, within one cent of exact proportion; full amount refunds every weight |
| `refund/RefundReservationIntegrationTest` (Phase 1) | MySQL | approval reserves, disbursement cannot take reserved refund funds, reject releases, record converts, paused campaign |
| `settlement/SettlementIntegrationTest` (Phase 1) | MySQL | pro-rata execution with two admins, cancelled campaign full refund, preconditions, stale proposal, cancel/reject |
| `adjustment/AdjustmentIntegrationTest` (Phase 1) | MySQL | dual control, credit/debit effects, over-debit, reject/cancel, conflicts of interest |
| `escrow/ReconciliationJobIntegrationTest` (Phase 1) | MySQL + MockMvc | drift detection and findings, run history, append-only runs, admin-only endpoint |
| `campaign/FundraisingCloseIntegrationTest` (Phase 1) | MySQL | end-date close, pending donations failed, idempotency, late approval refused |
| `campaign/CampaignCancellationIntegrationTest` (Phase 1) | MySQL | owner/admin cancellation paths, escrow SETTLEMENT, blocked with open requests or after completion |
| `disbursement/RequestIdempotencyIntegrationTest` (Phase 1) | MySQL | Idempotency-Key replay and conflict for disbursements and refunds |
| `organization/OrganizationApiIntegrationTest` (Phase 1) | MockMvc + MySQL | organization CRUD, verification workflow, material-change reset, delete protection, validation |
| `common/PaginationContractIntegrationTest` (Phase 1) | MockMvc + MySQL | page envelope, sorting, size cap/default, bad sort, admin filters |
| `common/ListQueryCountIntegrationTest` (Phase 1) | MySQL + Hibernate statistics | statement count of campaign and donation lists does not grow with page size or donor count |
| `config/OpenApiContractTest` (Phase 1) | MockMvc | live OpenAPI equals `docs/api/openapi-v1.json` |
| `escrow/ConcurrencyIntegrationTest` (Phase 1 additions) | MySQL, multithreaded | concurrent idempotent replays create one request; refund approval vs disbursement request cannot both claim the same funds |

Integration tests use **Testcontainers MySQL 8.4** (no H2) via `support/AbstractMySqlIntegrationTest`, a singleton container started with `--log-bin-trust-function-creators=1`. They are tagged `integration`.

## Commands
```bash
mvn test                                  # everything (Docker required)
mvn test -DexcludedGroups=integration     # unit tests only, no Docker
mvn test -Dtest=ConcurrencyIntegrationTest
mvn test -Dtest=OpenApiContractTest -Dopenapi.snapshot.update=true   # regenerate the API snapshot after a reviewed change
```
`application-test.yml` turns on Hibernate statistics, which `ListQueryCountIntegrationTest` needs, and turns off scheduled jobs.

## Conventions
- Test data uses random emails/titles; tests do not depend on order or on seed data (seed disabled via `application-test.yml`, except the seeder test).
- Assert error codes (`ErrorCode`), not messages.
- Every money-moving test also asserts `reconcile(...).consistent()`.

## Gaps
Covered in Phase 1: organization API, pagination/sorting contract, campaign cancellation paths, property-based invariants.
Still open: evidence-review edge cases and load tests for lock contention. Neither is a Phase 1 objective; they are left for Phase 4.
