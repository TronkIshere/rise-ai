# Phase 1 — Business Hardening (IMPLEMENTED and VERIFIED — see Progress and Closure)

> **Note (2026-10-25):** package paths in this document refer to the capability-based layout used before the layered refactor (ADR-008). For current locations see `architecture.md` › Package layout.

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

## Progress (2026-10-25)
Status values: VERIFIED = implemented, with automated tests passing in a local `mvn clean test` run (Testcontainers MySQL 8.4); PENDING = not done.

| # | Objective | Status | Implementation | Verification |
|---|---|---|---|---|
| 1 | `ddl-auto: validate`, fix mapping drift | VERIFIED | `application.yml`. Drift found and fixed: six `currency` columns plus `refresh_tokens.token_hash` and `evidence_records.file_hash` are `CHAR(n)` and are now mapped with `@JdbcTypeCode(SqlTypes.CHAR)` | Every integration test boots the context under schema validation |
| 2 | Scheduled reconciliation, alert logging, report history | VERIFIED | `V4`, `escrow/ReconciliationService`, `ReconciliationRun*`, `config/ScheduledJobs`, `EscrowController` run endpoints | `ReconciliationJobIntegrationTest`; `DemoDataSeederIntegrationTest.scheduledReconciliationJobFindsNoInconsistenciesOnSeededData` |
| 3 | Settlement policy, new ledger type, dual control | VERIFIED | ADR-006, `V5`, `settlement/*`, `EscrowEngine.recordSettlementRefund` | `SettlementIntegrationTest`, `ProRataAllocationPropertiesTest`, `LedgerInvariantPropertiesTest` |
| 4 | Refund approval reserves funds (`REFUND_RESERVED`) | VERIFIED | `V3`, `EscrowEngine.reserveRefund` / `releaseRefundReservation`, `RefundService` | `RefundReservationIntegrationTest`, `ConcurrencyIntegrationTest.refundApprovalAndDisbursementRequestCannotBothClaimTheSameFunds`, existing `DisputeAndRefundIntegrationTest` |
| 5 | `ADJUSTMENT` entries with two distinct admins | VERIFIED | `V5`, `adjustment/*`, `EscrowEngine.recordAdjustment`, DB CHECK `decided_by <> requested_by` | `AdjustmentIntegrationTest`, `LedgerInvariantPropertiesTest` |
| 6 | Property tests for ledger invariants; organization, pagination and cancellation tests | VERIFIED | jqwik 1.9.2 (test scope) | `LedgerInvariantPropertiesTest` (300 random operation sequences; mutation-checked: dropping `SETTLEMENT_REFUND_RECORDED` from the formula makes it fail), `OrganizationApiIntegrationTest`, `PaginationContractIntegrationTest`, `CampaignCancellationIntegrationTest` |
| 7 | Optional `Idempotency-Key` on disbursement and refund creation | VERIFIED in the 109/109 run; final ownership-order fix not yet re-verified (see Session checkpoint) | `V6`, `DisbursementService` / `RefundService` / controllers | `RequestIdempotencyIntegrationTest`, `ConcurrencyIntegrationTest.concurrentReplaysWithTheSameIdempotencyKeyCreateOneDisbursement` |
| 8 | Campaign end-date handling | VERIFIED | `V7`, `campaign/FundraisingCloseService`, `ScheduledJobs`, confirmation and approval checks, `acceptingDonations` field | `FundraisingCloseIntegrationTest` |
| 9 | Projections for list endpoints, N+1 review | VERIFIED | `UserRepository.findDisplayNames` (donor names, no User entity or role collection loaded), `OrganizationRepository.findSummaries` (campaign organization summary). Review found no per-row queries in campaign or donation lists | `ListQueryCountIntegrationTest` (statement count does not grow with page size or number of donors) |
| 10 | Frozen API contract (versioned OpenAPI snapshot) | VERIFIED in the working tree; git commit PENDING (needs the owner's go-ahead) | `docs/api/openapi-v1.json` (OpenAPI info version 1.0.0) | `OpenApiContractTest`, which compares the live `/v3/api-docs` with the snapshot |

Acceptance criteria:
- New rules documented in `business-rules.md`: done (rules 44–57; rules 14, 39 and 42 updated).
- Reconciliation job finds zero inconsistencies on seeded data: verified by `DemoDataSeederIntegrationTest`.
- Invariant property tests pass: verified.
- API snapshot committed: the file exists and is verified; the git commit itself waits for the repository owner.

Decisions and deviations:
- **Settlement policy:** pro-rata refund only; reallocation is not implemented (ADR-006).
- **Fundraising close:** the job does not change campaign status, because completion needs milestone work to finish first.
- **Default page size:** it is 10 (`@PageableDefault`). The `default-page-size: 20` in `application.yml` has no effect on annotated endpoints. It was documented rather than changed, to keep the contract stable.
- **Build fixes found while starting Phase 1:**
  - `nimbus-jose-jwt` had no version; it is now pinned to 9.37.3.
  - Testcontainers is pinned to 1.21.4, because 1.19.8 cannot talk to Docker Engine 29.
  - `DisbursementService.create` checked milestone status before the open-request rule, so a second request returned `INVALID_STATE_TRANSITION` instead of `OPEN_REQUEST_EXISTS` (business rule 26). The checks are now in the right order.
- **Order of work:** tests for objectives 2–5 and 7–9 were written right after each feature rather than strictly test-first. The property tests (objective 6) cover all ledger operations, including the new ones.

## Session checkpoint (2026-10-25)
**Phase / task:** Phase 1. All 10 objectives are implemented (see Progress). The remaining work is final verification and handover.

**Commands executed and results** (Maven 3.9.12 with JDK 21; Docker Desktop with Engine 29):
- `mvn test-compile`: passes.
- `mvn test` before Phase 1 changes, with only the build fixes and `ddl-auto: validate`: 70/70 passed.
- `mvn test -Dtest=LedgerInvariantPropertiesTest,ProRataAllocationPropertiesTest`: 3/3 passed. A deliberate formula mutation made the ledger property fail, as intended; the source was restored and checked.
- `mvn test -Dtest=OpenApiContractTest -Dopenapi.snapshot.update=true` generated the snapshot. A separate rerun without the flag passed.
- `mvn test` (full suite): **109/109 passed, BUILD SUCCESS.**
- After that run, two changes were made: ownership is now checked before the idempotency-key lookup in `RefundService.request` and `DisbursementService.create`, and `RequestIdempotencyIntegrationTest.reusingAKeyOnSomeoneElsesResourceDoesNotRevealIt` was added. Then `mvn clean test` ran: **110 tests, 109 passed, 1 failed (BUILD FAILURE).** The failing test is the new `reusingAKeyOnSomeoneElsesResourceDoesNotRevealIt`: expected `RESOURCE_NOT_FOUND`, actual `ACCESS_DENIED`. Probable cause (not yet confirmed): the disbursement assertion is wrong. For a published campaign, `CampaignService.requireOwner` returns `ACCESS_DENIED` to a non-owner (existing Phase 0 behavior, business rule 4 applies only to hidden resources), and that answer reveals nothing about the idempotency key.

**Open items:**
1. Fix the failing test above. The likely fix is to expect `ACCESS_DENIED` in the disbursement assertion, after confirming which assertion failed. Then rerun `mvn clean test` and expect 110/110.
2. The smoke boot of the packaged app with the real `application.yml` (jobs and seeding on) has not been run. Container `rise-phase1-smoke` (MySQL 8.4 on host port 3317; throwaway, not the docker-compose volume) is still running. Remove it with `docker rm -f rise-phase1-smoke` when done.
3. Objective 10: `docs/api/openapi-v1.json` is not committed to git yet; this needs the owner's go-ahead. Nothing from this session is committed.
4. Not Phase 1 scope, left open: the root `README.md` is still empty (Phase 0 acceptance item 19); evidence-review edge-case tests and load tests (Phase 4).

**Constraints the next session must know:**
- **Environment:** Maven is not on PATH. Use the cached distribution `~/.m2/wrapper/dists/apache-maven-3.9.12-bin/.../bin/mvn` with `JAVA_HOME` set to JDK 21; the default `JAVA_HOME` is JDK 20.
- **Maven cache repair:** 31 corrupted (HTML) POMs in `~/.m2` were moved, not deleted, to the session scratchpad backup so Maven could download them again. This is outside the repo and needs no repo action.
- **API changes:** the OpenAPI snapshot freezes the contract. Any API change fails `OpenApiContractTest` until the snapshot is reviewed and regenerated.
- **Migrations:** V3–V7 are new; never edit them once applied anywhere. The escrow balance-identity CHECK now includes refund-reserved and adjustment aggregates.
- **Do not skip ahead:** do not start Phase 2 or 3 without an explicit request.

**Next recommended action:** find which assertion in `RequestIdempotencyIntegrationTest.reusingAKeyOnSomeoneElsesResourceDoesNotRevealIt` failed, correct it, and rerun `mvn clean test` to reach 110/110.

## Closure (2026-10-25, Phase 2 session)
All open items above are resolved:
1. **Failing test fixed.** The failing assertion was the disbursement one (line 87): for an ACTIVE (visible) campaign, `CampaignService.requireOwner` returns `ACCESS_DENIED`, which is correct under business rule 4 (404 only for hidden resources) and reveals nothing about the idempotency key. The test now expects `ACCESS_DENIED`; no production code changed. Baseline `mvn clean test` reproduced 110 run / 1 failure before the fix; after it, the suite passes (see `testing-strategy.md` for the latest full count).
2. **Smoke boot done.** The packaged jar started with the real `application.yml` (jobs and seeding on) against a fresh MySQL 8.4 container: Flyway applied V1–V9, the seeder created the demo data through the services, `/actuator/health` was UP. The old `rise-phase1-smoke` container was not touched; its removal is left to the owner.
3. **Objective 10.** `docs/api/openapi-v1.json` was already committed (it is tracked in `c541f9e`); the "git commit pending" note above is stale. Phase 2 added admin monitoring endpoints to the snapshot (additive only).
4. Root `README.md` now contains setup instructions (Phase 0 acceptance item 19).
