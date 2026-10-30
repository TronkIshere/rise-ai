# Testing Strategy

> All tests below are executed by `mvn clean test` (latest results at the end of this file). Since the layered refactor (ADR-008, 2026-10-25): scenario integration tests live in `src/test/java/com/riseai/integration/`, unit tests sit in the package of the class they test, the shared base class is `support/AbstractMySqlIntegrationTest`, and layer rules are checked by `architecture/LayeredArchitectureTest`.

## Layout
| File | Type | Covers |
|---|---|---|
| `service/impl/SimulatedEscrowEngineTest` | unit (Mockito) | balance math, reservation/release/refund, insufficient balance, idempotency, frozen account, invariant check, entry direction/references |
| `security/JwtServiceTest` | unit | signing, tampering, wrong secret, expiry, short secret |
| `integration/AuthSecurityIntegrationTest` | MockMvc + MySQL | ADMIN not registrable, injected roles ignored, login/profile, generic login errors, 401/403 shapes, refresh rotation and reuse detection, logout, suspended user, self role change, cross-creator edits, hidden drafts, donation IDOR, donor cannot confirm, no internals in errors |
| `integration/CampaignLifecycleIntegrationTest` | MySQL | DRAFT start, milestone budget rules, verified org + admin approval, invalid transitions, edit lock, donation eligibility, self-donation, evidence ownership, sequential milestones, status history, completion with reserved funds |
| `integration/EscrowLifecycleIntegrationTest` | MySQL | full donation→release flow with reconciliation and audit, pending donations, double confirmation, minimum donation, idempotency key, evidence required, over-budget, over-balance, one open request, accepted evidence for approval, dual-role self-review, duplicate release, rejection returns reservation, milestone completion |
| `integration/DisputeAndRefundIntegrationTest` | MySQL | dispute hold/dismiss/uphold, eligibility, opener cannot review, duplicate disputes, withdrawal, refund limits, refund recording and duplicates, refunds cannot use reserved funds, self-approval |
| `integration/ConcurrencyIntegrationTest` | MySQL, multithreaded | concurrent release, concurrent requests, concurrent confirmations (no lost update), double confirmation race |
| `integration/DemoDataSeederIntegrationTest` | MySQL | seed completeness, idempotent re-run, all accounts reconcile; Phase 1: the scheduled reconciliation job (wired with `rise.jobs.enabled=true`) finds zero inconsistencies on seeded data |
| `service/impl/LedgerInvariantPropertiesTest` (Phase 1) | jqwik property, unit | 300 random sequences of every engine operation (incl. replays and status changes): no negative aggregate, balance identity, ledger-recomputed balances equal stored aggregates, rejected operations change nothing, unique idempotency keys |
| `util/ProRataAllocationPropertiesTest` (Phase 1) | jqwik property, unit | allocation sums to the amount, never exceeds a weight, within one cent of exact proportion; full amount refunds every weight |
| `integration/RefundReservationIntegrationTest` (Phase 1) | MySQL | approval reserves, disbursement cannot take reserved refund funds, reject releases, record converts, paused campaign |
| `integration/SettlementIntegrationTest` (Phase 1) | MySQL | pro-rata execution with two admins, cancelled campaign full refund, preconditions, stale proposal, cancel/reject |
| `integration/AdjustmentIntegrationTest` (Phase 1) | MySQL | dual control, credit/debit effects, over-debit, reject/cancel, conflicts of interest |
| `integration/ReconciliationJobIntegrationTest` (Phase 1) | MySQL + MockMvc | drift detection and findings, run history, append-only runs, admin-only endpoint |
| `integration/FundraisingCloseIntegrationTest` (Phase 1) | MySQL | end-date close, pending donations failed, idempotency, late approval refused |
| `integration/CampaignCancellationIntegrationTest` (Phase 1) | MySQL | owner/admin cancellation paths, escrow SETTLEMENT, blocked with open requests or after completion |
| `integration/RequestIdempotencyIntegrationTest` (Phase 1) | MySQL | Idempotency-Key replay and conflict for disbursements and refunds |
| `integration/OrganizationApiIntegrationTest` (Phase 1) | MockMvc + MySQL | organization CRUD, verification workflow, material-change reset, delete protection, validation |
| `integration/PaginationContractIntegrationTest` (Phase 1) | MockMvc + MySQL | page envelope, sorting, size cap/default, bad sort, admin filters |
| `integration/ListQueryCountIntegrationTest` (Phase 1) | MySQL + Hibernate statistics | statement count of campaign and donation lists does not grow with page size or donor count |
| `integration/OpenApiContractTest` (Phase 1) | MockMvc | live OpenAPI equals `docs/api/openapi-v1.json` |
| `integration/ConcurrencyIntegrationTest` (Phase 1 additions) | MySQL, multithreaded | concurrent idempotent replays create one request; refund approval vs disbursement request cannot both claim the same funds |

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

## Phase 2 — AI monitoring tests (2026-10-25)
| Test | Kind | Covers |
|---|---|---|
| `integration/DomainEventOutboxIntegrationTest` | Testcontainers MySQL | One minimized event per committed action across a full milestone lifecycle; no event on rollback; forbidden fields absent from every payload; reconciliation event published inside its transaction |
| `integration/OutboxRelayIntegrationTest` | Testcontainers MySQL + JDK `HttpServer` stub monitor + MockMvc | Request mapping and service key; PUBLISHED / retry with backoff / FAILED; requeue; idempotent finding storage; conflict of interest; review transitions; escalation reopens a dismissed finding; invalid finding contract dead-letters; admin-only endpoints |
| `architecture/LayeredArchitectureTest` | Plain JUnit (source scan) | Controllers import no repositories, entities or `service.impl`; repositories and entities import no services or controllers; business service implementations never reference monitoring findings; event delivery code never calls business services or the escrow engine |

Commands: `mvn clean test` (requires Docker). The default `JAVA_HOME` on the development machine is JDK 20; set it to JDK 21 first, or the build fails with "release version 21 not supported".

Latest full run (2026-10-25, after the layered refactor): **122 tests, 0 failures** (110 Phase 0/1 tests, including the corrected `RequestIdempotencyIntegrationTest`, 8 Phase 2 integration tests and 4 `LayeredArchitectureTest` rules). Before the refactor the same suite ran 120 tests (with a 2-test predecessor of the architecture test). End-to-end verification against the real monitoring service is recorded in `../../docs/ai-monitoring/06-progress-report.md`.

## Test count (2026-10-30)
Full backend run: 177 tests, 0 failures (`mvn -B clean test`, JDK 21, Testcontainers MySQL 8.4). New in this run: hardening, alert administration, secrets validation, AI note persistence, production pool timings.
