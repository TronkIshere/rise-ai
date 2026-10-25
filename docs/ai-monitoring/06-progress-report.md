# 06 — Progress Report: Rise AI Backend + AI Monitoring Integration

**Date:** 2026-10-25. **Branch:** `master` (local), fast-forwarded from `feat/phase-1-business-hardening` (`59a8f29` → `aaf374e`) with the owner's approval. Nothing is committed or pushed; all changes are in the working tree.

## 1. Starting point (verified)
- Working tree: the planning documents in `docs/ai-monitoring/` plus edits to three docs (uncommitted, preserved).
- Backend `mvn clean test` (JDK 21): 110 tests, **109 passed, 1 failed** (`RequestIdempotencyIntegrationTest.reusingAKeyOnSomeoneElsesResourceDoesNotRevealIt`). This reproduces the Phase 1 record.
- Monitor `./mvnw clean test`: **133/133 passed**. This was the first real Spring run of this module.
- Simulator against the running monitor (2,000 scenarios, seed 42, AI scenarios excluded): **2,000/2,000**.
- Environment note: `JAVA_HOME` defaults to JDK 20 on this machine; every build was run with `JAVA_HOME` set to `C:\Program Files\Java\jdk-21`.

## 2. What was implemented

| Area | Roadmap | Result |
|---|---|---|
| Phase 1 remaining items | M0 | Failing test corrected (the expectation was wrong; no production change). Smoke boot of the packaged backend with the real configuration done. OpenAPI snapshot found to be already committed |
| Module rename | M1 | `local-ai-event-monitor` → `rise-ai-monitoring`; `com.aicore.eventmonitor` → `com.riseai.monitoring`; main class `RiseAiMonitoringApplication`; Maven `com.riseai:rise-ai-monitoring`; simulator `com.riseai:rise-ai-monitoring-simulator`; port 8081; metric prefix `riseai.monitoring.*`. API paths, configuration keys and environment variables unchanged |
| Event contract | M2 | Envelope v1 finalized with `actorRole` and `references`; 20 event types; minimization allowlists; `finding.v1`. Documented in `rise-ai-backend/docs/event-contracts.md` |
| Transactional outbox + relay | M3 | `V8__outbox_events.sql`; `outbox` package; publishes at 20 call sites inside the business transactions; relay with lease, backoff, dead-lettering, response classification; disabled by default |
| Monitor ingestion + store | M4 | Service-key filter (Bearer, constant time, error 1006); `riseai` profile (127.0.0.1 binding, key required, file H2, 30-day event age, allow-list, AI off); Rise envelope validation; minimized projection with Flyway; retention purge |
| Charity rules + findings | M5 | Eight deterministic rules (EV-01, EV-02, DB-01, DB-02, DB-03, LG-01, AO-01, DP-01); deterministic `findingId`; additive `findings[]` in the response; findings also raise rule flags, so risk and review use the existing logic; metric `riseai.monitoring.findings` |
| Findings store + admin API | M6 | `V9__monitoring_findings.sql`; idempotent upsert per (finding, source event); severity only escalates; admin list, detail and history endpoints; OpenAPI snapshot extended (additive) |
| Human review + audit | M7 | Review state machine with a mandatory note; conflict-of-interest guard (campaign creator, source-event actors); escalation reopens dismissed findings; all transitions audited |
| Operations (MVP subset) | M9★ | Outbox stats endpoint (backlog, oldest age, failed); admin requeue of FAILED rows (audited); `ALERT`-prefixed log lines on permanent failure; `FindingBoundaryTest` |

**Architectural decisions taken during implementation** (the plan's recommendations applied; see `README.md` › Decision register):
- **D4 changed:** the projection store is **embedded H2** (in-memory by default, file in the `riseai` profile), not a MySQL schema. It needs no extra infrastructure and is still separate from the business database. Persistent deduplication was **not** built: after a restart a redelivered event is re-evaluated, which is harmless because rules and finding IDs are deterministic and projection writes are idempotent. Both behaviours are tested and were seen end to end.
- **Retry policy:** a maximum attempt count (12) with exponential backoff, without a separate time horizon, so that requeued old events are not failed immediately.
- **Metrics:** the backend exposes outbox health through an ADMIN endpoint rather than actuator metrics (its actuator exposure is `health,info`; unchanged).
- **D14 not applied:** no shared root `contracts/` folder. Each module's integration tests carry its own fixtures.

## 3. Changed files
**Backend (`rise-ai-backend/`)**
- New:
  - `src/main/java/com/riseai/outbox/` (10 classes);
  - `src/main/java/com/riseai/finding/` (10 classes);
  - `db/migration/V8__outbox_events.sql`, `V9__monitoring_findings.sql`;
  - tests `outbox/DomainEventOutboxIntegrationTest`, `outbox/OutboxRelayIntegrationTest`, `finding/FindingBoundaryTest`;
  - `docs/decisions/ADR-007-monitoring-integration.md`.
- Modified:
  - services `CampaignService`, `DonationService`, `MilestoneService`, `EvidenceService` (and `EvidenceRepository`), `DisbursementService`, `DisputeService`, `RefundService`, `AdjustmentService`, `SettlementService`, `ReconciliationService` (publish calls only);
  - `application.yml` (`rise.monitoring.*`);
  - `RequestIdempotencyIntegrationTest` (corrected expectation);
  - `docs/api/openapi-v1.json` (additive).

**Monitor (`rise-ai-monitoring/`, moved from `local-ai-event-monitor/`)**
- Every file moved, and package/import statements rewritten.
- New:
  - `core/finding/*`, `rise/*`, `rise/rule/*`;
  - `filter/ServiceKeyAuthenticationFilter`, `configuration/SchedulingConfig`;
  - `dto/response/monitoring/FindingResponse`;
  - `db/migration/V1__rise_projection.sql`, `application-riseai.yml`;
  - `controller/RiseMonitoringIntegrationTest`.
- Modified:
  - `pom.xml` (coordinates; `spring-boot-starter-jdbc`, `flyway-core`, `h2`);
  - `MonitoringProperties` (`security`, `rise`), `ErrorCode` (1006), `PlatformConstants`, `MonitoringMetrics`;
  - `RuleEngine` (`withAdditionalFindings`), `MonitoringResult`, `MonitoringResultAssembler`, `MonitoringResultMapper`, `MonitoringResultResponse`;
  - `EventMonitoringServiceImpl`, `MonitoringStatusServiceImpl` (+`riseProjection`), `MonitoringStatusResponse`;
  - `application.yml`, `application-local.yml`, test `application-test.yml`;
  - `.env.example`, `.gitignore` (`data/`);
  - tests `EventMonitoringServiceImplTest`, `EventMonitoringApiIntegrationTest`;
  - simulator POM, default URL and logging configuration.

**Documentation:** listed in §6.

## 4. Verification results (all executed on 2026-10-25)
| Check | Result |
|---|---|
| Backend `mvn clean test` (final) | **120/120**, BUILD SUCCESS (110 Phase 0/1 + 10 Phase 2) |
| Backend Phase 2 tests | `DomainEventOutboxIntegrationTest` 3/3, `OutboxRelayIntegrationTest` 5/5, `FindingBoundaryTest` 2/2 |
| `OpenApiContractTest` with regenerated snapshot | Pass; diff 603 additions, 0 deletions |
| Monitor `./mvnw clean package` (final) | **144/144** (133 existing + 11 new) |
| Simulator unit tests / scenario run (final jar) | **38/38**; **2,000/2,000** scenarios, 0 request failures |
| Rename runtime checks | Starts as `RiseAiMonitoringApplication` on 8081; `riseai.monitoring.events.received` present, old metric name 404 |
| `riseai` profile without key | Startup refused with the configuration message |
| **E2E:** fresh MySQL 8.4 container (`rise-e2e-mysql`, port 3318), packaged backend (relay on, seeding and jobs on), packaged monitor (`riseai`) | Flyway V1–V9 applied; seeding emitted 65 events over 15 event types; all **PUBLISHED, 0 FAILED, none REJECTED** within ~6 s |
| E2E: same receipt hash submitted to two campaigns via the REST API | EV-01 **HIGH** finding visible at `/api/v1/admin/monitoring/findings`, `advisoryOnly: true` |
| E2E: access and review | Creator → 403; review without note → 400; ADMIN review → `ACKNOWLEDGED`, audit row with action, states and correlation ID `e2e-review-1`; evidence records unchanged |
| E2E: monitor stopped | Donation, confirmation and evidence submission all succeeded; 2 events PENDING with `ConnectException` and backoff |
| E2E: monitor restarted on the same H2 file | Backlog drained (69 published, 0 failed); a third use of the hash produced a new HIGH finding from pre-restart history; occurrences unique (2 rows, 2 distinct pairs) |
| E2E: injected ledger mismatch, reconciliation run via API | Run `INCONSISTENT` → LG-01 **CRITICAL** finding with `CHECK_RECONCILIATION`; data restored, rerun `CONSISTENT` |
| Real Ollama model | **Not run** |

## 5. Known gaps, mocks and external dependencies
- **Simulated money:** payments, escrow, releases and refunds are simulated (unchanged). No payment provider or credentials.
- **AI:**
  - AI analysis is disabled for Rise events by default and has not been run against a real model.
  - The prompt is not adapted to charity data (M8).
  - Ollama is an external local process with no credentials.
- **Not implemented:**
  - time-based rules (overdue milestones, missing reports) and a monitor→backend push path;
  - persistent deduplication;
  - service-key rotation and mTLS;
  - alert delivery (logs only);
  - backend actuator metrics;
  - Dockerfiles, Compose services for the two applications, CI;
  - expense/invoice-level monitoring (the backend has no expense model);
  - document content analysis (no document storage).
- **Thresholds** (DB-01 50,000,000 VND, DB-02 24 h, DB-03 3 requests, AO-01 10,000,000 VND) are placeholders awaiting product decision D12.
- **Monitor rule listing:** `/api/v1/monitoring/rules` lists only the six generic rules, not the eight Rise rules.
- **Environment leftovers (not deleted):**
  - `rise-phase1-smoke` (port 3317), from the earlier Phase 1 session; its removal is the owner's decision;
  - `rise-e2e-mysql` (port 3318), the throwaway database for this session's E2E run.

## 6. Documentation updated
- **Root:** `README.md`, `docs/INDEX.md`, this folder.
- **Backend:**
  - `README.md`;
  - `docs/README.md`, `project-overview.md`, `architecture.md`, `event-contracts.md` (rewritten as implemented), `api-specification.md`, `database-design.md`;
  - `security.md`, `audit-and-traceability.md`, `business-rules.md` (58–66), `state-machines.md`, `roles-and-permissions.md`, `testing-strategy.md`;
  - `implementation-plan.md`, `phase-1-business-hardening.md` (closure), `phase-2-ai-monitoring.md`;
  - `decisions/README.md`, ADR-004 (status note), ADR-007 (new).
- **Monitor:** `README.md`, every file in `docs/`, `test-simulator/README.md` (rename, port, Rise integration, verification status).

## 7. Phase 1 acceptance status
All 10 Phase 1 objectives are implemented and the suite passes. "API snapshot committed" was already true. The remaining Phase 0 item 19 (setup instructions in the root README) is now satisfied.

## 8. Next steps before any deployment
1. Review this working tree and commit it (suggested split: Phase 1 test fix, module rename, backend outbox and findings, monitor Rise rules, docs). Then decide whether `master` should be pushed and how `feat/phase-1-business-hardening` should be closed.
2. Record formal approval of decisions D1–D13 and the D4 deviation (H2) in `README.md`.
3. Set production secrets: `RISE_JWT_SECRET`, `RISE_MONITORING_SERVICE_KEY` / `MONITORING_SERVICE_KEY`, database credentials; disable seeding and simulated confirmation.
4. Product decision D12 on rule thresholds; then decide whether AI notes (M8) are wanted and run the evaluation gate on the chosen local model.
5. Backend Phase 4 / roadmap M10: Dockerfiles, Compose services, CI running both suites, an automated cross-service E2E test, alert delivery for `FAILED` outbox rows and `CRITICAL` findings, and key rotation.
6. Decide retention for findings and the monitor projection (D9) and confirm the 30-day published-outbox purge.
7. Remove the leftover containers (`docker rm -f rise-phase1-smoke rise-e2e-mysql`) when no longer needed.

## 9. Layered package refactor (2026-10-25, later the same day)

Requested by the owner: organize both Spring Boot modules by technical layer. Decision record: `rise-ai-backend/docs/decisions/ADR-008-layered-package-structure.md`. File paths in §3 above refer to the layout **before** this refactor.

**Backend.** All 185 source and test files were reorganized with a parser-based script, run after a backup and checked by the compiler:
- 19 capability packages became `controller` (16), `service` (22 interfaces), `service/impl` (22), `repository` (21), `entity` (24), `enums` (31), `dto/request` (23), `dto/response` (33), `dto/event` (1), `mapper` (1), `client` (1), `configuration` (8), `security` (4), `exception` (4), `util` (6).
- The 20 `*Dtos` holders were flattened into 50 records and 1 enum, with unchanged simple names.
- Application services are now interface + `Impl`; all callers use interfaces.
- Tests: `integration/` (19), `architecture/LayeredArchitectureTest` (new, 4 rules), unit tests next to their class's package.
- Manual follow-ups: four holder constants moved onto their response records; `FundingSummary.from` made public; `AuditService.meta` became a static interface method.

**Monitor.** Already layered (`controller`, `service/impl`, `dto`, `configuration`, `exception`, `mapper`). Only the JDBC repository moved: `rise/RiseProjectionRepository` → `repository/RiseProjectionRepository`. The pipeline packages stay, as documented in its `ARCHITECTURE.md`.

**Unchanged by design:** endpoints, request/response schemas (OpenAPI snapshot not regenerated; `OpenApiContractTest` passes), entities and column mappings, repository queries (only class names inside two JPQL strings were updated), migrations, transactions, security rules, event contracts.

**Verification (executed):**

| Check | Result |
|---|---|
| Backend `mvn clean test` | **122/122** (the 2 old boundary tests replaced by 4 layer rules) |
| `OpenApiContractTest` against the unchanged snapshot | Pass |
| Monitor `./mvnw clean package` / simulator | **144/144** / **38/38** |
| Packaged jars on MySQL 8.4 (existing E2E data) with the relay on | Both healthy; stored findings readable; public endpoint 200, admin endpoint 401/403 for anonymous/creator; a new evidence submission produced a new EV-01 finding through the relay (72 published, 0 failed) |
| Stale reference sweep | No old `com.riseai.<capability>` packages in code or tests |
