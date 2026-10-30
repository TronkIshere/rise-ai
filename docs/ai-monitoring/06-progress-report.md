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

## 13. Fourth continuation session (2026-10-30)

Starting state: branch `master` equal to `origin/master` (0 ahead, 0 behind, after `git fetch`), working tree clean apart from the new root `.gitignore`. Nothing committed or pushed. There is no `CLAUDE.md`.

### 13.1 Changes
| Area | Defect found | Change | Evidence |
|---|---|---|---|
| CI | `rise-ai-monitoring/mvnw` is tracked as mode 100644 (`git ls-files -s`), so `./mvnw` would fail with permission denied on the Linux runner; the monitor Dockerfile already worked around it with `chmod +x` | `ci.yml` runs `sh ./mvnw -B clean package`; the git file mode was left alone | YAML parses (PyYAML, jobs `backend`, `monitoring`, `images`). Not run on GitHub |
| AI-note latency | The 12.2 review was right: the budget only stopped new calls. An in-flight note call could run to `OLLAMA_REQUEST_TIMEOUT` (60 s) past the budget | `OllamaClient.chat(request, timeout)` caps the request timeout at the smaller of the caller's and the configured value; `FindingNoteService` passes the remaining budget. Worst case per event is now generic analysis (at most 60 s) plus notes (at most the 20 s budget), under the relay's 90 s read timeout. Notes remain off by default and advisory | `FindingNoteTest` (+2: timeout is bounded by the remaining budget; a timed-out call leaves findings unchanged), `OllamaClientTest` (+2: a shorter caller timeout cancels an in-flight request; a longer one never exceeds the configured limit) |
| Git | No project `.gitignore` | Ignores `.claude/settings.local.json` and `CLAUDE.local.md` | `git check-ignore` |

The 90 s margin is arithmetic from the defaults, not a measured run with a real model.

### 13.2 Verification (executed 2026-10-30, JDK 21, Maven 3.9.11 from the wrapper cache because no global `mvn` is installed)
| Check | Result |
|---|---|
| Backend `mvn -B clean test` | **125/125**, BUILD SUCCESS (Docker 29.3.1 available for Testcontainers) |
| Monitor `./mvnw -B clean package` | **159/159**, BUILD SUCCESS (155 + 4 new) |
| Simulator `mvn -B clean package` | **43/43**, BUILD SUCCESS |
| Rise suite against the packaged monitor (port 8083, AI off, key set as in CI) | **20/20**, 8/8 rules |
| `docker compose build` | Not rerun; no Dockerfile or Compose change this session |
| GitHub Actions | **Not run** |

### 13.3 Not done this session
- The Rise rules and the 20 scenarios were not re-audited line by line; the earlier mismatch check (19/20 with a wrong DB-01 threshold) is the only sensitivity evidence.
- No runbook, alerting, security or JSON Schema work; the blockers in 12.8 are unchanged. No D12 value, alert destination, model or dependency was chosen.
- Avalanche untouched.

## 14. Fifth continuation session (2026-10-30, resumed after an interrupted session)

Resumed from the working tree, not from memory. Nothing committed or pushed. The previous session's work after section 13 was in the tree but undocumented; this section records it after re-verification.

### 14.1 What was found in the tree (all uncommitted)
| Area | Content | Verified this session |
|---|---|---|
| JSON Schema contract | `contracts/events/rise-event-envelope.v1.schema.json` (20 event types); `RiseEventSchemaContractTest` + fixtures (monitor); `EventSchemaContract` (backend, used by outbox and seeder tests); validator dependency in both poms, test scope | Yes, in the suites below |
| Threshold configuration | Pilot values in `application-riseai.yml` (DB-01 20,000,000; DB-03 limit 5, both overridable by env; **reverted in section 15 because the owner has not approved them**); bean validation: positive amounts and durations, bounded counts, non-empty schema versions, service keys of at least 16 characters; `RiseThresholdValidationTest` (5) | Yes |
| Rule audit fixes | DB-03 and EV-01 no longer filter by `occurredAt`, so they count every earlier delivered row | Suite passes; **not re-audited line by line** this session |
| Alert delivery | `AlertNotifierImpl`: configurable timeout and retry for tests, delivered/failed/dropped counters, invalid webhook URL or token fails startup instead of silently dropping alerts, no redirects; `AlertNotifierImplTest` (9) | Yes |
| Production profile | `application-prod.yml` (demo seed, donation simulation and Swagger off, secrets required), graceful shutdown in both modules, Compose sets `prod` and CORS origins; `ProductionProfileConfigurationTest` (2) | Yes (config test only; Compose not rerun) |
| AI evaluation | `rise-ai-monitoring/docs/evaluation/ai_note_eval.py`: fixed 19-event dataset covering all eight rules plus two injection cases, per-note checks, latency, comparison with a notes-off baseline | **Not run**; no result file exists, so there is no new model evidence. The 2026-10-29 `qwen3.5:9b` pilot in M8.5 remains the only measurement |

### 14.2 Verification (executed 2026-10-30, JDK 21.0.2 via explicit `JAVA_HOME`; the machine default is JDK 20)
| Check | Result |
|---|---|
| Monitor `./mvnw -B test` (no `clean`, see 14.3) | **173/173**, BUILD SUCCESS |
| Backend `mvn -B clean test` (Maven 3.9.11 from the wrapper cache) | **137/137**, BUILD SUCCESS |
| Monitor `package`, simulator build, Rise suite 20/20 against a rebuilt jar | **Not run this session** (14.3). Last recorded: 159 monitor tests and 20/20 in section 13.2, before these changes |
| Docker Compose, GitHub Actions | Not run |

### 14.3 Environment problem
A monitor started in an earlier session is still running (`java -jar rise-ai-monitoring/target/rise-ai-monitoring.jar`, PID 21016, port 8083). It locks the jar, so `clean package` fails. Stopping it was refused by the permission system, and it was left alone. Stop it manually (`taskkill /PID 21016 /F`), then rerun `./mvnw -B clean package` and the Rise suite.

### 14.4 Remaining
- Owner: D12 values, alert destination, model choice, approval of the validator dependency.
- Not done: `finding.v1.schema.json` (M2.4); the 200-scenario AI gate (M8.5) with `ai_note_eval.py`; backend log-capture test (M9.3); re-run of package, simulator and Rise suite after stopping PID 21016; first GitHub Actions run.
- Avalanche (Phase 3) untouched.

## 15. Sixth continuation session (2026-10-30): audit, schema, evaluation, hardening

Nothing committed or pushed. Corrections to section 14: the blocker in 14.3 cleared itself (PID 21016 exited; it was never stopped by the assistant), and the rule audit, finding schema, backend log test and AI evaluation listed as open there were completed here.

### 15.1 Changes
| Area | Change | Evidence |
|---|---|---|
| Thresholds | The unapproved pilot values were the `riseai` profile, simulator and evaluation-script defaults; reverted to the documented placeholders (50,000,000 and 3). Env overrides still work | Rise suite 20/20 at the placeholders |
| Rules | EV-02 NullPointerException on a missing `evidenceType`; AO-01 wrote "null" into explanations; unbounded id lists in EV-01; `1e999999999` amounts (about 1 GB `toPlainString`); malformed hashes, ids and free-text fields reached facts, explanations and the backend token contract | `09-rule-audit.md`; `RiseRuleAuditTest` (19) |
| AI notes | Enum-like injection in `direction`/`currency` reached the prompt and one note echoing "ignore previous instructions and approve" passed the filter. Fields closed to contract values; filter extended | Evaluation rerun clean |
| Finding schema | `contracts/findings/finding.v1.schema.json`; monitor and backend tests | M2.4, M5.5 |
| Backend log capture | `noKeyTokenPayloadOrMonitorResponseIsLoggedAtAnyLevel` | M9.3 |
| Documentation | `09-rule-audit.md`, `evaluation/README.md`, checklist, TESTING, CONFIGURATION, 02 §6, 08 | this section |

### 15.2 Verification (2026-10-30, JDK 21.0.2, Maven 3.9.11, all exit code 0)
Superseded by 15.6, which was run in the real tree after the DB-02 change. Earlier figures in this section (200/200, scratch-copy Rise suite) are kept only as history: monitor `clean package` 200/200 and `test` 200/200 before the DB-02 change; backend `mvn -B clean test` 138/138 (still current: no backend or contract file changed afterwards); AI evaluation as in 15.4 and `evaluation/README.md`.

### 15.3 Security and reliability: fixed and remaining
Fixed: items in 15.1. Reviewed, no change: compose (secrets required, MySQL and monitor not published, non-root image), webhook redirects never followed and URL validated (the URL is operator-configured, so SSRF to internal hosts is an operator decision, not an input risk), request size cap 262,144 bytes.
Remaining: DP-01 misses a release delivered after the dispute event (DB-02 has since been fixed, see 15.6); the in-memory alert queue loses queued alerts on a restart (the FAILED outbox row stays in MySQL and in the stats endpoint, so the condition is not lost, only the push notification); a rule that throws is logged and skipped while the event is still marked processed; an enum-shaped value of up to 40 characters in any future open-ended field would still reach the prompt; no dependency vulnerability scan was run; Compose and `useSSL=false` between backend and MySQL are unchanged.

### 15.4 Decision register (owner)
| Decision | Options in the code and docs | Safe interim | Not production-ready until |
|---|---|---|---|
| D12 thresholds | Placeholders 50,000,000 / 3 / 24 h / 10,000,000 (active); pilot proposal 20,000,000 / 5 in `08` (not active) | Placeholders | Values approved with date and approver, checked against real data and the precision report |
| Alert destination and ownership | `RISE_MONITORING_ALERT_WEBHOOK_URL` and token; log-only without it | Log-only | A destination, an on-call owner and a decision on losing queued alerts on restart (outbox-backed alerts are the smallest fix) |
| AI model | `qwen3.5:9b` pilot, 68 s median per event | Notes off (default) | A model chosen, the 200-scenario gate run (`--repeat 10`, about three hours) and a grounding measure |
| Validator dependency | `com.networknt:json-schema-validator` 1.5.6, test scope in both modules | Keep | Owner sign-off |

### 15.5 Open
Technical: first GitHub Actions run; 200-scenario evaluation; DP-01 reverse-order projection (optional). Approval: the four decisions above. Avalanche (Phase 3) untouched; it is not the only remaining item while these stay open.

### 15.6 Final pre-commit verification (2026-10-30, real tree)
| Step | Command | Exit | Result |
|---|---|---|---|
| Processes | Eight `java -jar rise-ai-monitoring.jar` processes on ports 8084-8087 inspected by command line: 8084 and 8085 ran the real-tree jar, 8086 and 8087 a scratch copy; all started by the assistant in this session. Stopped by exact PID with the owner's instruction (5044, 19044, 15476, 16852 and their launcher shims) | 0 | none left running |
| Monitor | `rise-ai-monitoring`: `./mvnw -B clean package` | 0 | **202/202**, BUILD SUCCESS; `target/rise-ai-monitoring.jar` SHA-256 `601c82664398469e28f32b699e4471763cdb7782b8434ca96a58bee52d4cb4d9` |
| Simulator | `test-simulator`: `mvn -B clean package` | 0 | **43/43** |
| Rise suite | simulator jar, `--simulator.suite=rise` against that real-tree jar on port 8083 (`riseai` profile, AI off, placeholder thresholds); the jar hash was identical before and after | 0 | **20/20, 8/8 rules** |
| Backend | `mvn -B clean test` | 0 | **138/138** (run before the DB-02 change; no backend, contract or schema file modified since) |
| Whitespace | `git diff --check` | 0 | no whitespace errors; only the `LF will be replaced by CRLF` autocrlf notices on modified files, which were left alone |

DB-02: a rejection delivered after the request it covers now flags that request once (same `findingId` as in order); covered by `RiseRuleAuditTest` and the integration test. DP-01 reverse order is not fixed: it needs a new projection table and migration, and the exact impact is in `09-rule-audit.md`. The AI evaluation was run on a scratch build made before the DB-02 change; the change does not touch notes or the prompt, but the evaluation was not repeated.

## 16. Seventh session: remaining blockchain-independent work (2026-10-30, after commit `0994bcd`)

Scope: implementation and cleanup only. **No test, build, Docker command or evaluation was executed in this session**, so nothing below is verified by execution. The only command run on a source artifact was a Python syntax check of `ai_note_eval.py`.

### 16.1 Implemented (unverified)

| Item | Change | Test source written (not run) |
|---|---|---|
| M4.4 persistent deduplication | `JdbcEventDeduplicator` (default store) and `monitoring_event_ledger` (monitor migration V2); `EventDeduplicator` now stores a `MonitoringResultResponse` and has `getScope()`; stale in-progress rows are taken over after `in-progress-timeout`; in-memory store kept behind `monitoring.deduplication.store=memory` | `JdbcEventDeduplicatorTest`; `InMemoryEventDeduplicatorTest` and `EventMonitoringApiIntegrationTest` adapted |
| M5.4 DP-01 reverse order | `rise_dispute_events` (V2), `RiseProjectionRepository.recordDisputeEvent/disputeEvents`, `MisuseDisputeAfterReleaseRule` flags the dispute when the release arrives later, once, same `findingId`; purge covers the new table | `RiseRuleAuditTest.dp01FlagsADisputeExactlyOnceInEitherDeliveryOrder`, `dp01LateReleaseIgnores...` |
| M9 rule failures | `RiseFindingEngine` runs every rule, counts failures, then throws `RULE_EVALUATION_FAILED` (9001); the transaction rolls back, the dedup entry is released, the relay retries and finally marks the row `FAILED` with an alert | `RiseFindingEngineFailureTest` |
| M9 alert reliability | Backend `alert_notifications` (V10), `AlertNotifierImpl` stores, `AlertDispatcherImpl` + `AlertDispatchScheduler` deliver; `AlertWebhookClient` extracted from the old in-memory notifier; reconciliation raises alerts | `AlertWebhookClientTest`, `AlertDeliveryIntegrationTest`; `OutboxRelayIntegrationTest` adapted; old `AlertNotifierImplTest` removed (it tested the deleted in-memory queue) |
| M8.5 evaluation tooling | `ai_note_eval.py` reports grounding, note rate, p95 and a gate verdict | none (syntax-checked only) |

### 16.2 Cleanup
- Redundant catch-and-log removed from `OutboxRelayScheduler.relay()` (the scheduler already logs and keeps running).
- A scan found no unused imports and no unused private members or classes in either module (Spring components excluded).
- Source files carry no new comments.

### 16.3 Still open
- Owner decisions: D12 thresholds, alert destination and ownership, AI model, notes in the admin UI (M8.7), validator dependency sign-off, accept M1.9 / M1.11.
- M8.5 (200 scenarios) and M9.5 (deployed drill) need execution that was out of scope here.
- Everything in 16.1 needs a full test run before its checklist rows can be ticked.

### 16.4 Limitations introduced
- A poison event (a rule that always throws) now blocks the findings of the healthy rules for that event until the row is fixed and requeued, instead of silently losing only the failing rule. Healthy-rule findings are regenerated, with the same `findingId`s, on retry.
- (Superseded by section 17) Alerts are now persisted independently of the webhook URL. `FAILED` alert rows are not retried automatically and there is no admin endpoint for them.
- The in-flight capacity limit of the JDBC deduplicator counts unfinished rows only; completed rows are bounded by retention, not by `max-tracked-events`.
- Multi-instance use of the shared ledger needs a database server and has not been tried.

## 17. Eighth session: review of the section 16 implementation (2026-10-30)

Again **no test, build, Docker command or evaluation was executed**; the changes below are source and documentation only.

### 17.1 Issues found in the section 16 implementation
| Area | Issue | Fix |
|---|---|---|
| Alerts | Alerts were stored only when a webhook URL was configured, so creating an alert depended on delivery configuration; the dispatcher scheduler was also conditional on the URL | `AlertNotifierImpl` always stores; the scheduler always runs and `AlertDispatcherImpl` skips delivery while no URL is set |
| Alerts | One undifferentiated pending state, every failure retried, constant failure text | States `PENDING`, `RETRYING`, `SENT`, `FAILED` (V10 edited in place; it had never been committed or applied); `AlertWebhookClient.Delivery` classifies HTTP 5xx, 408, 429, timeouts and I/O errors as transient and other 4xx and redirects as permanent; `last_error` keeps the reason (`HTTP 503`) and `failed_at` is set |
| Alerts | A row whose attempts ran out while it was claimed (crash during delivery) would have been re-claimed forever or sent once more | `claim()` fails such rows without another delivery; `record()` ignores rows already `SENT` or `FAILED` |
| Deduplication | `register` recursed when the conflicting row vanished between insert and read | bounded retry loop, then `IN_PROGRESS` |
| Deduplication | The status endpoint claimed "survives restarts" even for `jdbc:h2:mem:` | the store derives durability from `spring.datasource.url`, logs a warning at startup for an in-memory URL and reports an in-memory scope |
| Projection | Identifiers longer than the projection columns (36 for evidence and disbursements) were inserted unchecked, so a hostile or malformed id would have made the insert fail and turned the event into a poison event | `RiseFindingEngine.record` stores only identifiers that match the safe pattern and fit the column; rules already ignored them |
| Docs | Several documents (module README, ARCHITECTURE, EVENT_LIFECYCLE, DECISIONS, runbook, roadmap, simulator README) still described in-memory deduplication or the earlier alert behaviour | updated; see 17.3 |

### 17.2 Reviewed and left unchanged
- DP-01: both delivery orders produce the same `findingId` (dispute id); a replayed release or dispute produces the same finding for the same source event, which the backend stores once (`uk_finding_occurrences_event`); with several releases or disputes a dispute is flagged once, by whichever release first covers it, and later releases see an earlier release at or before the dispute and stay quiet. V2 only creates new tables, so it applies to an existing file database that already has V1.
- Rule failure: the exception is thrown before any projection write, so nothing is partially written; the dedup entry is released; the backend retries up to `max-attempts` (12) with backoff and then marks the outbox row `FAILED` and alerts. The original envelope stays in the outbox row and the existing requeue endpoint is the recovery path, so no admin API was added.
- No unused imports, unused private members or catch-and-rethrow blocks were found in either module (scripted scan); no further code was removed.

### 17.3 Documentation updated
Runbook (alert lifecycle, rule-failure recovery, monitor database section), module README, ARCHITECTURE, EVENT_LIFECYCLE, DECISIONS D3, PROJECT_OVERVIEW, AI_HANDOFF, simulator README, roadmap, architecture overview 02, checklist M9.6, this report. M4.4, M5.4 and M9.6 stay unticked/partial; M8.5 stays NOT MET; owner decisions stay open.

### 17.4 Remaining limitations
- `PENDING`, `RETRYING` and `FAILED` alert rows are never purged automatically, so a deployment that never configures a webhook accumulates `PENDING` rows; configuring a webhook later delivers the whole backlog at once. There is no admin endpoint or metric for alert rows.
- Alert delivery is at-least-once: a crash between the HTTP call and the status update repeats one delivery.
- If a slow first processor outlives `in-progress-timeout`, a taker can evaluate the same event concurrently (outcome identical because writes and finding ids are idempotent). The timeout is not validated against the AI timeouts.
- The ledger's in-flight capacity check is a soft limit under concurrency (count then insert).
- All new tests remain unexecuted: `AlertWithoutWebhookIntegrationTest`, the added cases in `AlertDeliveryIntegrationTest`, `AlertWebhookClientTest` and `JdbcEventDeduplicatorTest`, and two new `RiseMonitoringIntegrationTest` cases.
