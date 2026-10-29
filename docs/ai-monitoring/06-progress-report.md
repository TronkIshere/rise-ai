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

## 10. Remaining blockchain-independent work (2026-10-29)

Scope: the unfinished MVP-adjacent items that need no Avalanche integration. No commits were made.

**Backend**
- `GET /api/v1/admin/monitoring/findings/precision` (ADMIN): per `ruleId` counts of OPEN, ACKNOWLEDGED, CONCERN_CONFIRMED and DISMISSED findings, and `precision = confirmed / (confirmed + dismissed)`, or null when nothing is decided. Read-only; findings stay advisory. Files: `RulePrecisionResponse`, `MonitoringFindingRepository`, `FindingService(Impl)`, `FindingController`; OpenAPI snapshot regenerated (67 additions, 0 deletions); asserted in `OutboxRelayIntegrationTest`.

**Monitor**
- Service-key rotation: optional `monitoring.security.previous-service-key` (`MONITORING_PREVIOUS_SERVICE_KEY`); both keys are accepted until the old one is removed.
- `/api/v1/monitoring/rules` now also lists the eight Rise rules (category `CHARITY`, effect `FLAG`). The existing rules-endpoint test expectation changed from 6 to 14.
- New tests: `ServiceKeyRotationAndLoggingIntegrationTest` (overlap and rejection; no key, attribute or payload content in logs at any level, captured at DEBUG) and `RiseProjectionRetentionIntegrationTest` (purge boundary, rules listing).

**Operations**
- `rise-ai-backend/Dockerfile`, `rise-ai-monitoring/Dockerfile`, root `docker-compose.yml` (services `mysql`, `rise-ai-monitoring`, `rise-ai-backend`; secrets required from the environment; seed and simulation off by default; monitor not published on the host), `.github/workflows/ci.yml`, `07-operations-runbook.md`.
- Defect found while verifying Compose: the `riseai` profile binds the monitor to 127.0.0.1, so the backend could not reach it. Compose sets `SERVER_ADDRESS=0.0.0.0`.

**Verification (executed 2026-10-29, JDK 21; the shell's `JAVA_HOME` pointed to JDK 20 and was overridden)**

| Check | Result |
|---|---|
| Backend `mvn clean test` | **122/122**, BUILD SUCCESS |
| Monitor `./mvnw clean package` | **148/148** (144 + 4 new), BUILD SUCCESS |
| `docker compose build` | Both images built |
| Compose stack with seed and simulation temporarily on | Backend `/actuator/health` UP; 65 outbox events PUBLISHED, none failed; stack removed with `down -v` |

**Not done, and why**
- M8 (AI-assisted analysis): needs decisions D5, D6, D13 and a local model evaluation; AI stays off for Rise events.
- M3.5 / M4.5 concurrency tests, M5.8 simulator Rise scenarios, M2.2 / M2.4 JSON Schema fixtures (no schema-validator dependency approved), M10.5 full business-table checksum, M9.3 backend-side log test.
- M10.7: the CI workflow has not run on a CI runner. M9.5: the runbook has not been drilled.
- D12 thresholds remain placeholders.
- Avalanche (backend Phase 3): untouched, see `rise-ai-backend/docs/phase-3-avalanche.md` and ADR-005.

**Next eligible work:** decide D5/D6/D13 and start M8; run CI once; drill the runbook; then Phase 3 (Avalanche) when an environment and contracts exist.

## 11. Decisions D5, D6, D13, D12 and the second continuation session (2026-10-29)

### 11.1 Decisions (from the `README.md` register; none is formally signed off)
| ID | What it decides | Options | Status and implementation | Needs your input |
|---|---|---|---|---|
| D5 | Data allowed into an LLM prompt | Allowlisted scalars only (recommended); or also free text or IDs | Applied as recommended. `FindingNotePrompt` sends only rule id, category, severity, recommended action and facts whose value is a number, boolean or enum-like token; IDs, hashes and text are dropped | Confirm the allowlist, or approve a wider one after a privacy review |
| D6 | Role of AI in the MVP | Explanation-only and disabled by default until the evaluation gate passes (recommended); or enabled | Applied. `aiNote` is advisory text; severity, facts and findings are never changed by it | Decide whether notes are ever enabled in production, after the gate |
| D13 | External model providers | Local Ollama only (recommended); or a hosted provider after written approval and privacy review | Applied; only `OllamaClient` is used | Confirm. Also name the candidate local model (`qwen3.5:9b` is the configured default) |
| D12 | Numeric rule thresholds | Owner sets values; placeholders ship | Open. Configurable under `monitoring.rise.*` (50,000,000 VND minimal-evidence approval, 24 h resubmission window, 3 requests per milestone, 10,000,000 VND adjustment review) | Provide the real values. None were invented |

M8 is implemented only as far as these applied defaults allow. The evaluation gate and the enable decision are blocked on your input.

### 11.2 Completed this session
- **Relay concurrency (M3.5):** two concurrent relays deliver each event exactly once.
- **Monitor ingestion concurrency (M4.5):** eight concurrent identical requests give one fresh analysis and one projection row.
- **Business-table checksum (M10.5):** 13 business tables are unchanged by relay, findings and reviews.
- **Alert delivery:** `AlertNotifier` (`service/impl/AlertNotifierImpl`) writes `ALERT` log lines and, when `RISE_ALERT_WEBHOOK_URL` is set, sends a short JSON POST (kind and ids only, no payload, 3 s timeouts, failures swallowed). Kinds: `OUTBOX_EVENT_FAILED`, `CRITICAL_FINDING` (new or escalated only). Sent after commit. Tested against a local stub endpoint. **Not tested against a real alerting system; the alert target is still your decision.**
- **M8 partial:** `analysis/FindingNotePrompt`, `FindingNoteFilter`, `FindingNoteService`; `Finding.aiNote` and `FindingResponse.aiNote`; `monitoring.rise.ai-notes-enabled` (default false) and `ai-note-max-length`. The backend ignores `aiNote` and does not store it.
- **Compose:** `RISE_BACKEND_MONITORING_KEY` lets the backend key differ from the monitor key during rotation.

### 11.3 Real-model pilot (not the gate)
Packaged monitor with AI and notes on, `qwen3.5:9b` on local Ollama, 3 rules (duplicate hash, financial without hash, reconciliation inconsistent), 10 findings. All 10 notes passed the filter and read as neutral. Median request time 71.8 s, max 78.1 s; this includes the generic AI analysis the monitor also runs for flagged events. With the backend relay read timeout at 90 s, enabling notes on this model is risky. Before enabling: a faster model, a separate asynchronous note path, or a larger timeout. This needs your decision.

### 11.4 Runbook drill (executed 2026-10-29 on the Compose stack, seed on)
| Procedure | Result |
|---|---|
| 1 monitor down | Monitor stopped; 3 reconciliation runs returned HTTP 201; outbox `pending` rose 0 to 3; after restart `pending` 0, `published` 65 to 68, `failed` 0 |
| 5 key rotation | New key plus previous on the monitor: backend (old key) still delivered (published 69); both keys answered 200 on the monitor; backend switched to the new key (published 70); previous cleared: new key 200, old key **401**, delivery continued (published 71) |
| 2, 3, 4, 6, 7 | **Not drilled** |

M9.5 therefore stays open.

### 11.5 Not done, with reasons
- **Simulator Rise scenarios (M5.8):** the simulator's `ExpectedOutcome` has no `findings[]` assertions and its configuration targets the generic API. Supporting the Rise profile (service key, envelope payloads, finding assertions) is separate work. All eight rules are covered in process by `RiseMonitoringIntegrationTest`.
- **JSON Schema fixtures (M2.2, M2.4):** a schema validator would be a new dependency. An equivalent contract test over generated fixtures was not built.
- **CI (M10.7):** `.github/workflows/ci.yml` cannot run here (no GitHub runner). The same commands ran locally (backend `mvn clean test`, monitor `./mvnw clean package`, `docker compose build`) and passed. Simulator tests were not rerun this session.
- **Evaluation gate (M8.5), D12 values, real alert target, other drill procedures:** see above.
- **Avalanche (Phase 3):** untouched.

### 11.6 Verification (executed 2026-10-29, JDK 21)
| Check | Result |
|---|---|
| Backend `mvn clean test` | **125/125** (122 + 3 new: relay concurrency, critical-finding alert, business-table checksum), BUILD SUCCESS |
| Monitor `./mvnw clean package` | **154/154** (148 + 5 `FindingNoteTest` + 1 concurrency test), BUILD SUCCESS |
| Compose build and drill | As in 11.4 |

**Next eligible work:** your answers on D5/D6/D13/D12 and the alert target; then the 200-scenario gate with a chosen model, backend storage of `aiNote`, simulator Rise support, the remaining runbook drills, a first CI run.

## 12. Third continuation session (2026-10-29)

Provisional decisions applied, recorded as recommendations pending sign-off: D5 strict allowlist kept; D6 notes explanation-only and off by default; D13 local Ollama only, `qwen3.5:9b` an evaluation candidate and not approved (observed median 71.8 s is too close to the backend's 90 s read timeout); D12 proposal only (`08-d12-threshold-proposal.md`); alerts stay log plus optional webhook with no destination selected.

### 12.1 Completed
- **Simulator finding assertions and Rise suite.** `ExpectedOutcome.requiredFindings` and `forbiddenFindings`, `StepResult.findingSeverities`, bearer service key in `CoreClient`, `--simulator.suite=rise`, `RiseScenarioSuite` (20 scenarios, all eight rules, boundary cases) and `RiseSuiteRun` (preflight requires eight active CHARITY rules). New category `RISE_RULES` has weight 0 and never appears in generic runs. Simulator unit tests: 43 (was 38).
- **Rise suite runs against the real monitor:** 20/20 with AI off; 20/20 with AI and notes on and Ollama unreachable; 19/20 (RISE-010 fails as intended) when the Core's DB-01 threshold differs from the suite's; 20/20 with the D12 candidate values on both sides.
- **Alert delivery review and changes** (see 12.3).
- **AI-note timing control:** `monitoring.rise.ai-note-budget` (default 20 s; a safety default, not a validated value). No new note call starts once the budget is spent; findings are then returned without a note. Test: `noNewModelCallIsStartedOnceTheNoteBudgetIsSpent`. Numeric-looking strings such as amounts are now allowed in prompts (digits only, at most 18 integer digits and 4 decimals).
- **Runbook drills** (12.4).
- **D12 proposal** with an executed dry run.

### 12.2 AI-note latency and persistence review
- A note is requested per finding, sequentially, inside the monitor's request, after the generic AI analysis for flagged events. The worst case for one event is generic analysis plus one note per finding, each bounded only by `OLLAMA_REQUEST_TIMEOUT` (60 s default). That can exceed the backend relay's 90 s read timeout, which would make the relay retry the event.
- With `qwen3.5:9b` the pilot median was 71.8 s. It is not suitable as configured.
- **Recommendation (not implemented):** if notes are ever enabled, produce them asynchronously after the finding is delivered, not in the ingestion request; until then keep them off. Notes are currently **transient**: returned in the monitor response, ignored by the backend, not persisted. No requirement for persistence is documented, so the backend does not store them. If a requirement appears, it needs a migration and a decision on retention and on whether notes are regenerated.
- Until a faster model or an asynchronous path exists, the safe setting is `monitoring.rise.ai-notes-enabled=false` (the default).

### 12.3 Alert delivery review
| Aspect | Finding | Action |
|---|---|---|
| Post-commit semantics | Alerts are registered with the transaction and sent after commit; a rolled-back failure marks nothing and sends nothing | Kept, tested (`OutboxRelayIntegrationTest`) |
| Blocking | The first version sent on the relay thread; a down webhook would stall the relay by up to 6 s per alert | Changed: single worker thread, queue of 100, overflow is logged |
| Retry | None | Two attempts, 1 s apart; after that the alert exists only as a log line. No persistent alert queue; alerts are lost on restart or when the webhook is down for longer |
| SSRF and scheme | URL comes only from operator configuration | Only absolute http or https URLs accepted, redirects not followed, plain http logged as a warning |
| Authentication | None | Optional `RISE_ALERT_WEBHOOK_TOKEN` sent as a bearer token. No request signing |
| Content | Kind and identifiers only (event id and type, error text, finding id, rule id, category); no payloads, names or amounts | Kept; the error text is a relay error string |
| Destination | None selected | Not configured and no production claim made |
- **Gaps:** reliable alert delivery would need a persistent alert outbox; webhook TLS pinning and signing are not implemented.

### 12.4 Runbook drill evidence (Compose stack, seed on, 2026-10-29)
| Section | What was executed | Observed |
|---|---|---|
| 2 Ollama down | Packaged monitor with AI and notes on, `OLLAMA_BASE_URL` unreachable; Rise suite | `/status` showed Ollama `UNREACHABLE`, failure mode `RULES_ONLY`; suite 20/20 |
| 3 FAILED rows | Created events while the backend used a wrong key; listed `FAILED`; fixed the key; requeued each row | 2 `FAILED` with `HTTP 401 code 1006`; after requeue `published` 65 to 67, `failed` 0; 2 `OUTBOX_EVENT_REQUEUED` audit rows |
| 4 Monitor DB lost | Stopped the monitor, removed its data volume, started it, ran a reconciliation | Healthy; `published` 67 to 68, `failed` 0 |
| 6 Compromise, no overlap | Monitor on a new key, backend on the old key; then the backend switched | Events went straight to `FAILED` (not `PENDING`); runbook corrected |
| 7 CRITICAL finding | Added 1 to contributions and balance of one escrow account in the database, ran reconciliation via the API, restored the data, ran it again | A `CRITICAL` `rise.ledger.reconciliation-inconsistent` finding appeared (`CHECK_RECONCILIATION`); review without a note: 400; review with a note by the same ADMIN: 403 `CONFLICT_OF_INTEREST`; rerun after restore: `CONSISTENT` |
Earlier the same day: section 1 (monitor down) and section 5 (key rotation), see 11.4. The single demo ADMIN cannot complete the review step on a finding about its own action, so a successful review was not re-run in this drill.

### 12.5 Dependency decision needed: JSON Schema validation
Neither module has a JSON Schema validator (swagger-core is present in the backend but is not one). Options: (a) add a test-scope validator such as `com.networknt:json-schema-validator`, then add `contracts/` schemas and one example per event type, validated in both modules; (b) keep the current approach, field-level checks in `FindingIngestionService` and `RiseEnvelopeReader` plus the simulator suite that exercises the envelope shape. Recommendation: (a), test scope only. Not added.

### 12.6 CI (local equivalents; GitHub not run)
| Command | Result |
|---|---|
| Backend `mvn -B clean test` | **125/125** |
| Monitor `./mvnw -B clean package` | **155/155** |
| Simulator `mvn -B clean package` | **43/43** |
| Rise suite against the packaged monitor | 20/20, 8/8 rules |
| `docker compose build` | Built (earlier today and again this session) |
Workflow changes: simulator now builds a jar, and a step starts the monitor and runs the Rise suite. The workflow has not run on GitHub Actions and was not parsed by a YAML tool; do not treat CI as verified.

### 12.7 Automated guarantees kept
Business-table checksum (`monitoringAndReviewActivityNeverChangeBusinessTables`), two-relay concurrency (`twoConcurrentRelaysNeverDeliverTheSameEventTwice`) and monitor ingestion concurrency (`concurrentIdenticalRequestsProduceOneAnalysisAndNoDuplicateProjectionRows`) all pass in the runs above.

### 12.8 Blockers and next work
- Owner: sign-off on D5/D6/D13; D12 values (proposal in `08-d12-threshold-proposal.md`); alert destination; JSON Schema dependency; model choice and whether notes are produced asynchronously.
- Then: the 200-scenario note evaluation gate; relative thresholds (backend payload change); alert outbox if reliability matters; first CI run on GitHub; a second ADMIN account in the demo data for the review drill.
- Avalanche (Phase 3) untouched.
