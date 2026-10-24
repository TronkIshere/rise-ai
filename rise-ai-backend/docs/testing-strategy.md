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
| `config/DemoDataSeederIntegrationTest` | MySQL | seed completeness, idempotent re-run, all accounts reconcile |

Integration tests use **Testcontainers MySQL 8.4** (no H2) via `support/AbstractMySqlIntegrationTest`, a singleton container started with `--log-bin-trust-function-creators=1`. They are tagged `integration`.

## Commands
```bash
mvn test                                  # everything (Docker required)
mvn test -DexcludedGroups=integration     # unit tests only, no Docker
mvn test -Dtest=ConcurrencyIntegrationTest
```

## Conventions
- Test data uses random emails/titles; tests do not depend on order or on seed data (seed disabled via `application-test.yml`, except the seeder test).
- Assert error codes (`ErrorCode`), not messages.
- Every money-moving test also asserts `reconcile(...).consistent()`.

## Gaps (Phase 1)
Organization API tests, pagination/sorting contract tests, evidence-review edge cases, campaign cancellation paths, property-based invariant tests, load tests for lock contention.
