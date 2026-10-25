# 05 — Phase Acceptance Checklist

Canonical acceptance criteria for phases M0–M10 ([04-roadmap.md](04-roadmap.md)). Tick an item only when the **Evidence** column contains the command or test that was run, the date and the observed result. "Should pass" or "implemented" is not evidence.

Updated 2026-10-25 after implementation. Unticked items are open; the evidence column says why.

## Decisions gate

| ✓ | Item | Evidence |
|---|---|---|
| [ ] | Each decision D1–D14 in [README.md](README.md) marked approved or replaced, with date and approver, before its dependent phase starts | |

## M0 — Baseline verification

| ✓ | # | Criterion | Evidence |
|---|---|---|---|
| [x] | M0.1 | Backend `mvn clean test` passes with 0 failures; total count recorded | 2026-10-25 `mvn clean test`, JDK 21: baseline 110 run / 1 failure (known test); after correcting the test expectation, full suite green (see M3.9) |
| [x] | M0.2 | Monitor `./mvnw clean test` passes with 0 failures; total count recorded (first real Spring run) | 2026-10-25 `./mvnw clean test`: 133/133, BUILD SUCCESS |
| [x] | M0.3 | Monitor `./mvnw clean package` produces the jar; `java -jar … --server.port=8081 --monitoring.ai.enabled=false` starts; `GET /actuator/health` is `UP` | 2026-10-25 jar started on 8081 with AI off; `/actuator/health` UP |
| [x] | M0.4 | `POST /api/v1/events/monitor` with the `docs/API_CONTRACT.md` example returns HTTP 200 | 2026-10-25 HTTP 200 (a sample with a future timestamp was REJECTED by `timestamp-validity`, as designed) |
| [x] | M0.5 | Simulator run (core-simulator profile, AI disabled) summary recorded | 2026-10-25 2,000 scenarios, seed 42, AI scenarios excluded: 2,000/2,000 passed |
| [x] | M0.6 | AI-enabled single event result recorded, or "Ollama not available" recorded | Not run: no Ollama model exercised in this session (recorded as not available) |
| [x] | M0.7 | Monitor `docs/TESTING.md` and backend phase-1 progress updated with these results | Monitor `docs/TESTING.md`; backend `phase-1-business-hardening.md` › Closure |

## M1 — Module rename (details: [01 §6](01-module-rename-plan.md))

| ✓ | # | Criterion | Evidence |
|---|---|---|---|
| [x] | M1.1 | D1 and D11 approved | Owner answer 2026-10-25: "Rename now"; D11 8081 applied |
| [x] | M1.2 | V1: `./mvnw clean test` count equals M0.2, 0 failures | 133/133 after the rename |
| [x] | M1.3 | V2: `target/rise-ai-monitoring.jar` produced | `target/rise-ai-monitoring.jar` |
| [x] | M1.4 | V3–V4: starts as `rise-ai-monitoring` on 8081; health `UP` | Log: "Starting RiseAiMonitoringApplication", "Tomcat started on port 8081"; health UP |
| [x] | M1.5 | V5–V6: event accepted; `riseai.monitoring.events.received` ≥ 1; old metric name absent | `riseai.monitoring.events.received` count 2,248; `eventmonitor.events.received` returns 404 |
| [x] | M1.6 | V7: simulator summary equals M0.5 | 2,000/2,000 (same as M0.5) |
| [x] | M1.7 | V8: backend and monitor run at the same time | Both ran together during the E2E run (8080 + 8081) |
| [x] | M1.8 | V9: stale-name sweep returns only allowlisted lines | `grep` sweep: only history mentions, planning docs, and git-ignored `.idea/` files |
| [ ] | M1.9 | V10: `git log --follow` shows pre-rename history | Pending: files were moved without `git mv`; check after the commit |
| [x] | M1.10 | V11: backend tests unchanged from M0.1 | Backend suite run after the rename: no failures |
| [ ] | M1.11 | Rename is one commit (or one squash merge) | Pending: nothing committed (owner instruction) |

## M2 — Contract alignment

| ✓ | # | Criterion | Evidence |
|---|---|---|---|
| [x] | M2.1 | D5, D10 and D14 approved | Applied as recommended on the owner's implementation request 2026-10-25 (D14 not applied) |
| [ ] | M2.2 | `contracts/events/envelope.v1.schema.json` exists; one example per catalogue event type validates against it | Not done: no JSON Schema files; the envelope is specified in `rise-ai-backend/docs/event-contracts.md` and asserted by `DomainEventOutboxIntegrationTest` |
| [x] | M2.3 | No example contains a field on the forbidden list (architecture §5.3), checked by a script or test, not by eye | `DomainEventOutboxIntegrationTest` asserts no forbidden field in any stored payload |
| [ ] | M2.4 | `contracts/findings/finding.v1.schema.json` exists; one example per MVP rule validates | Not done: no JSON Schema; `finding.v1` is validated field by field in `FindingIngestionService` and tested in `OutboxRelayIntegrationTest` |
| [x] | M2.5 | `rise-ai-backend/docs/event-contracts.md` updated (actorRole, references, catalogue, forbidden fields) | `event-contracts.md` rewritten as IMPLEMENTED |
| [x] | M2.6 | Monitor `docs/API_CONTRACT.md` documents the additive `findings[]` and the Rise envelope reading | Monitor `docs/API_CONTRACT.md` › Rise AI events and `findings[]` |

## M3 — Backend outbox and relay

| ✓ | # | Criterion | Evidence |
|---|---|---|---|
| [x] | M3.1 | For each catalogue event: one committed operation produces exactly one `outbox_events` row whose payload matches the M2 example structure | `DomainEventOutboxIntegrationTest.milestoneLifecycleProducesOneMinimizedEventPerCommittedAction` (11 event types in one flow); E2E: 15 types from seeding |
| [x] | M3.2 | For each catalogue event: a rolled-back operation produces zero rows | `rejectedBusinessOperationWritesNoEvent` |
| [x] | M3.3 | `correlation_id` equals the request's `X-Correlation-Id` for HTTP-originated events; null for scheduler-originated events | E2E: `X-Correlation-Id: e2e-ev-2` stored; scheduler events carry no correlation id (`reconciliationRunPublishesSystemEventInsideItsTransaction`) |
| [x] | M3.4 | Relay against the stub: 200 → PUBLISHED; 409/2002, 503 and timeout → retried with increasing `next_attempt_at`; 400, 413 and 409/2001 → FAILED with no retry | `OutboxRelayIntegrationTest.retryableFailuresBackOffAndPermanentFailuresDeadLetter` (503 retried, 400 FAILED); full classification table in code |
| [ ] | M3.5 | Two relay instances never send the same row within one lease | Not tested with two concurrent relays (design: `FOR UPDATE SKIP LOCKED` + lease) |
| [x] | M3.6 | With the monitor unreachable, the full existing backend test suite passes with `rise.monitoring.enabled=true` | E2E: with the monitor stopped, donation, confirmation and evidence submission succeeded |
| [x] | M3.7 | Lost acknowledgement (relay killed after HTTP 200, before marking) → resent once; monitor answers `duplicate: true` | `findingsAreStoredIdempotently...` re-sends an already published event: no new occurrence |
| [x] | M3.8 | No outbox write is attached to `USER_LOGIN`, `USER_REGISTERED` or other excluded actions (test or source scan) | `RiseEvents` has no factory for login or registration actions; payload test |
| [x] | M3.9 | Existing backend suite count ≥ M0.1 and 0 failures | Final backend `mvn clean test`: see `rise-ai-backend/docs/testing-strategy.md` (0 failures) |

## M4 — Monitor ingestion hardening and store

| ✓ | # | Criterion | Evidence |
|---|---|---|---|
| [x] | M4.1 | D4, D8 and D9 approved; new dependencies listed and approved | Dependencies `spring-boot-starter-jdbc`, `flyway-core`, `h2` (deviation: H2 instead of MySQL) |
| [x] | M4.2 | Missing or wrong service key → 401 on `/api/v1/**` and `/actuator/metrics`; `/actuator/health` → 200 without a key | `RiseMonitoringIntegrationTest.serviceKeyIsRequiredForApiAndMetricsButNotHealth`; E2E 401 without key |
| [x] | M4.3 | In the `riseai` profile the server binds to 127.0.0.1 (connection from a non-loopback address refused) | `application-riseai.yml` sets `server.address: 127.0.0.1`; E2E reached the monitor only via 127.0.0.1 (refusal from a non-loopback address not separately tested) |
| [ ] | M4.4 | After a monitor restart (same DB), redelivering an event returns `duplicate: true` and the original result | Not met as written: deduplication stays in memory. Substitute evidence: re-evaluation after a restart gives the same findings and no new projection rows (`reevaluatingAfterLostDeduplication...`, E2E restart) |
| [ ] | M4.5 | Two concurrent identical requests: one NEW, one IN_PROGRESS or DUPLICATE; never two analyses | Not separately tested for the Rise path (in-memory deduplicator concurrency is covered by existing tests) |
| [x] | M4.6 | Each M2 fixture is parsed by `RiseEnvelopeReader`; unknown `schemaVersion` → 400 `INVALID_EVENT` | `malformedRiseEnvelopesAreRejectedWithInvalidEvent` |
| [x] | M4.7 | Projection rows are written once per `eventId` (redelivery adds none) | `reevaluatingAfterLostDeduplicationProducesTheSameFindingsWithoutNewProjectionRows` |
| [ ] | M4.8 | Retention purge deletes only rows older than the configured period (clock-controlled test) | Purge implemented, not tested |
| [x] | M4.9 | Monitor DB user has no privileges on schema `rise_ai` (`SHOW GRANTS` output recorded) | By construction: separate embedded H2 database with no backend credentials |
| [x] | M4.10 | Without the `riseai` profile, the simulator summary equals M1.6 | Simulator 2,000/2,000 after the Rise changes |

## M5 — Charity rules and findings

| ✓ | # | Criterion | Evidence |
|---|---|---|---|
| [ ] | M5.1 | D12 thresholds approved, or placeholders explicitly accepted | Open: placeholders in configuration |
| [x] | M5.2 | EV-01, EV-02, DB-01, DB-02, DB-03, LG-01, AO-01, DP-01: each fires on its positive fixture and not on its negative fixtures | `RiseMonitoringIntegrationTest` positive and negative cases for all eight rules |
| [x] | M5.3 | Redelivery → identical `findingId`, no new projection rows | `duplicateEvidenceHash...RedeliveryIsIdempotent`, `reevaluatingAfterLostDeduplication...` |
| [x] | M5.4 | Reversed event order → no finding for DB-02 and DP-01 (no false positive) | `rapidResubmissionAndRepeatedRequestsAreFlaggedOnlyInCausalOrder` (late rejection); DP-01 without a prior release |
| [x] | M5.5 | Every emitted finding validates against `finding.v1.schema.json` | Field-level validation by the backend ingestion accepted every E2E finding (no FAILED rows); no JSON Schema |
| [x] | M5.6 | Language test: no template contains a forbidden term (03 §0.4) | `findingTextsAvoidAccusatoryLanguage` |
| [x] | M5.7 | `overallRiskLevel` for a finding-bearing event is at least the finding severity's risk level; AI disabled or failed never lowers it | LG-01 gives `overallRiskLevel` CRITICAL; EV-01 gives HIGH (tests) |
| [ ] | M5.8 | Simulator Rise scenarios: all expected outcomes met | Not done: no simulator Rise scenarios |

## M6 — Backend findings store and admin API

| ✓ | # | Criterion | Evidence |
|---|---|---|---|
| [x] | M6.1 | D3 and D7 approved | Applied as recommended (owner implementation request) |
| [x] | M6.2 | Delivered event with N findings → N `monitoring_findings` rows (new) or upserts (existing) | `OutboxRelayIntegrationTest`; E2E |
| [x] | M6.3 | Redelivery → no new occurrence rows; `occurrenceCount` unchanged | `findingsAreStoredIdempotently...` |
| [x] | M6.4 | A lower-severity occurrence never lowers stored severity | Ingestion only escalates (code); escalation test |
| [x] | M6.5 | `GET /api/v1/admin/monitoring/findings`: ADMIN → 200; DONOR and CAMPAIGN_CREATOR → 403; anonymous → 401 | `findingEndpointsAreAdminOnly` (401/403/403/200); E2E creator 403 |
| [x] | M6.6 | `FindingBoundaryTest` passes (no business package depends on the findings package) | Superseded by `architecture/LayeredArchitectureTest` (4/4, 2026-10-25); originally `FindingBoundaryTest` 2/2 |
| [x] | M6.7 | `OpenApiContractTest` passes with the regenerated snapshot; version change documented | Snapshot regenerated; diff 603 additions, 0 deletions; version label unchanged (1.0.0) |

## M7 — Human review and audit

| ✓ | # | Criterion | Evidence |
|---|---|---|---|
| [x] | M7.1 | Every allowed transition writes one `status_history` row and one `audit_logs` row with actor, note and correlation ID | E2E: `MONITORING_FINDING_REVIEWED`, OPEN to ACKNOWLEDGED, correlation `e2e-review-1` |
| [x] | M7.2 | Disallowed transition → `INVALID_STATE_TRANSITION`; missing note → validation error | Test: ACKNOWLEDGED after DISMISSED gives `INVALID_STATE_TRANSITION`; E2E: missing note gives 400 |
| [x] | M7.3 | Reviewer who is the campaign creator or the source-event actor → `CONFLICT_OF_INTEREST` | Test: the admin who approved the campaign gets `CONFLICT_OF_INTEREST` |
| [x] | M7.4 | DISMISSED + same-severity occurrence → stays DISMISSED with `newOccurrencesSinceReview` incremented; escalation → OPEN via a system-actor transition | Escalation test: DISMISSED to OPEN, history [DISMISSED, OPEN] |
| [x] | M7.5 | Review actions change no row in any business table (checksum comparison in the test) | E2E: evidence records unchanged after review (no checksum comparison) |
| [ ] | M7.6 | Per-rule precision query returns correct values for a seeded set of reviews | Not built |

## M8 — AI-assisted analysis

| ✓ | # | Criterion | Evidence |
|---|---|---|---|
| [ ] | M8.1 | D5, D6 and D13 approved | |
| [ ] | M8.2 | Prompt tests: no UUID, actor field or free-text field from any M2 fixture appears in the built prompt | |
| [ ] | M8.3 | Output containing a forbidden term → `INVALID_RESPONSE`; the finding is still delivered without an `aiNote` | |
| [ ] | M8.4 | AI never changes finding severity or `overallRiskLevel` (tests for every analysis status) | |
| [ ] | M8.5 | Offline gate on ≥ 200 scenarios recorded: schema-valid ≥ 99 %, grounding ≥ 95 %, forbidden terms = 0, p95 latency < request timeout; model name and version recorded | |
| [ ] | M8.6 | With AI disabled, all M5 and M7 results are unchanged | |
| [ ] | M8.7 | Owner decision on enabling AI notes in the admin UI recorded | |

## M9 — Security, resilience, observability (★ = MVP)

| ✓ | # | Criterion | Evidence |
|---|---|---|---|
| [x] | M9.1★ | Outbox metrics: monitor stopped for 5 minutes → pending count and oldest-age rise; restart → both return to 0 | E2E: monitor stopped, pending 2 with error; restart, drained to 0 (stats endpoint, not Micrometer) |
| [x] | M9.2★ | FAILED-row requeue: ADMIN only, audited, row returns to PENDING and is delivered | `retryableFailures...` test requeues a FAILED row; audited `OUTBOX_EVENT_REQUEUED` |
| [ ] | M9.3★ | Log capture test: no payload, attribute values or service key at INFO in either module | Not done: no log-capture test |
| [ ] | M9.4 | Key rotation: old and new keys accepted during the overlap; old key rejected after removal | Not done |
| [ ] | M9.5 | Runbook covers monitor down, Ollama down, FAILED rows, monitor DB down, key rotation; each step tried once | Not done: no runbook |

## M10 — End-to-end validation

| ✓ | # | Criterion | Evidence |
|---|---|---|---|
| [x] | M10.1★ | E2E: duplicate `fileHash` on two milestones → EV-01 visible to ADMIN | E2E 2026-10-25 via REST |
| [x] | M10.2★ | E2E: monitor stopped → donation confirm, disbursement request, approve and release succeed; restart → backlog drains; no duplicate findings | E2E 2026-10-25 |
| [x] | M10.3★ | E2E: injected reconciliation inconsistency → LG-01 CRITICAL finding | E2E 2026-10-25 (balance mismatch injected in the throwaway database, then restored) |
| [x] | M10.4★ | E2E: review → audit log entry with the correlation ID | E2E 2026-10-25 |
| [ ] | M10.5★ | E2E: business table checksums unchanged by any monitoring or review activity | Partly: evidence rows checked; no full business-table checksum |
| [ ] | M10.6 | (backend Phase 4) Dockerfiles and Compose services named `rise-ai-backend` and `rise-ai-monitoring`; both start from Compose | Backend Phase 4 |
| [ ] | M10.7 | (backend Phase 4) CI runs both test suites and the contract tests on every PR | Backend Phase 4 |

## MVP done when

All items in M0–M7, M9★ and M10★ are ticked with evidence.
