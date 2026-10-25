# Rise AI Monitoring — Planning Set

Planning documents for renaming the AI Monitoring module (`local-ai-event-monitor/`) to **`rise-ai-monitoring`** and adapting it to the Rise AI charity domain. Written 2026-10-25 from repository inspection at commit `aaf374e` (branch `feat/phase-1-business-hardening`, clean working tree).

**Status (2026-10-25): MVP IMPLEMENTED.** Phases M0–M7, the M10 MVP end-to-end run (manual) and most M9 MVP items (not the log-capture test) are implemented and verified. M8 (AI-assisted analysis) and the production items are pending. Evidence and next steps: [06-progress-report.md](06-progress-report.md). The module now lives at `rise-ai-monitoring/` (renamed from `local-ai-event-monitor/`); paths below that use the old name describe the pre-rename state.

## How these phases relate to the backend phases

The backend already numbers its delivery phases 0–4 (`rise-ai-backend/docs/implementation-plan.md`). AI monitoring is backend **Phase 2**. The phases in this folder are numbered **M0–M10** and are sub-phases of backend Phase 2. They do not replace or renumber the backend phases.

## Reading order for a future implementation session

1. This file: status labels, decision register, current-state summary.
2. [04-roadmap.md](04-roadmap.md): find the phase you are implementing and its prerequisites.
3. The document that phase points to:
   - [01-module-rename-plan.md](01-module-rename-plan.md) for M1.
   - [02-architecture-and-integration.md](02-architecture-and-integration.md) for contracts, data flow, security (M2–M4, M6, M9).
   - [03-charity-monitoring-requirements.md](03-charity-monitoring-requirements.md) for rules, AI capabilities, review (M5, M7, M8).
4. [05-acceptance-checklist.md](05-acceptance-checklist.md): tick items only with evidence (command + result).
5. [06-progress-report.md](06-progress-report.md): what was actually built, verification results and open gaps.
5. Then read the source files you will change. **Code is the source of truth**; if a document disagrees with code, trust the code and fix the document.

## Status labels

| Label | Meaning |
|---|---|
| **VERIFIED** | Read in source code or configuration at commit `aaf374e`. File path given. |
| **DOC-ONLY** | Stated in existing documentation, not confirmed in code (for example test results that were not re-run). |
| **PROPOSED** | Architecture or contract recommended by this plan. Not implemented. |
| **PLANNED** | Future functionality assigned to a phase. Not implemented. |
| **DECISION** | Needs human (architect or product owner) approval before the dependent phase starts. |

## State before implementation (VERIFIED at `aaf374e`, historical)

- Backend (`rise-ai-backend/`, `com.riseai:rise-ai-backend`, Spring Boot 3.3.5, MySQL, Flyway V1–V7) publishes **no events**: no outbox, publisher, listener, broker or outbound HTTP client exists. Business actions are recorded only by `AuditService` into `audit_logs` / `status_history`.
- Monitor (`local-ai-event-monitor/`, `com.aicore:local-ai-event-monitor`, Spring Boot 3.5.15, package `com.aicore.eventmonitor`) is a **domain-agnostic, stateless, unauthenticated** HTTP service: `POST /api/v1/events/monitor` → normalize → in-memory dedup → deterministic rules → optional local Ollama → result returned synchronously and logged.
- The two modules do not reference each other anywhere (`git grep` for each module's identifiers in the other returns nothing).
- The monitor has **never been built with Maven** and its Spring integration tests have never run (`local-ai-event-monitor/docs/TESTING.md`, "Latest verified results").
- The backend's last recorded full test run was 110 tests, 109 passed, 1 failed (`rise-ai-backend/docs/phase-1-business-hardening.md`, session checkpoint).

## Decision register

All were **DECISION** items. On 2026-10-25 the owner asked for the integration to be implemented, so the recommendations were applied as defaults (Status column). Formal sign-off is still recommended; record it by editing the Status column with date and approver.

| ID | Decision | Recommendation | Blocks | Status |
|---|---|---|---|---|
| D1 | Canonical module name and Java package | Directory and artifactId `rise-ai-monitoring`; groupId `com.riseai`; package `com.riseai.monitoring` (alternative `com.riseai.aimonitoring` if the backend may ever want a `com.riseai.monitoring` package) | M1 | Applied as recommended (owner chose "Rename now") |
| D2 | Integration mechanism | Transactional outbox in the backend + backend-owned relay pushing over HTTP to the monitor's existing endpoint. No broker for MVP. See architecture §4 | M2, M3 | Applied as recommended |
| D3 | Findings return path | MVP: findings returned in the monitor's synchronous response and stored by the backend relay. Post-MVP: authenticated push from monitor to a backend ingestion API for time-based findings | M2, M6 | Applied as recommended (synchronous return path only) |
| D4 | Monitor persistence | Give the monitor its own database schema (`rise_ai_monitoring`, separate DB user, no grants on `rise_ai`) for durable dedup and a minimized event projection. This reverses the monitor's own decision D2 ("no database") | M4, M5 | **Applied with deviation:** embedded H2 instead of a MySQL schema; durable deduplication not built (see progress report §2) |
| D5 | Data allowed into the LLM prompt | Allowlisted scalar attributes only: event type, amounts, currency, counts, enum values, rule findings. No free text, names, emails, phone numbers, URLs or internal IDs | M2, M8 | Applied: payloads allowlisted; IDs, codes and hashes excluded from monitor attributes; AI prompt itself not yet adapted (M8) |
| D6 | Role of AI in MVP | AI stays optional and explanation-only. Shipped disabled by default for Rise events until the M8 evaluation gate passes. Deterministic rules carry the MVP | M8 | Applied: AI off by default in the `riseai` profile; findings deterministic |
| D7 | Finding review lifecycle | Statuses `OPEN → ACKNOWLEDGED → CONCERN_CONFIRMED / DISMISSED`; a dismissed finding reopens only if its severity escalates | M6, M7 | Applied as recommended |
| D8 | Service-to-service authentication | Static per-environment API key in a request header, compared in constant time, plus binding the monitor to localhost/private network. mTLS deferred to production readiness | M4 | Applied (Bearer key, constant time, loopback binding in `riseai`) |
| D9 | Retention | Monitor projection and processed-event records: 400 days (covers a campaign lifecycle plus a margin; product to confirm). Findings and reviews in the backend: same as audit logs (no deletion today) | M4, M6 | Partly applied: monitor projection 400 days; published outbox rows 30 days; findings not deleted |
| D10 | Envelope v1 finalization | Since nothing is emitted yet, finalize v1 now with an optional top-level `references` object (campaign, milestone, disbursement, donation IDs) instead of adding it later | M2 | Applied (`actorRole`, `references`) |
| D11 | Monitor default port | `8081` (backend and monitor both default to `8080` today) | M1 | Applied (8081) |
| D12 | Monitoring rule thresholds | Product owner sets numeric thresholds (for example "large share of milestone" percentage); plan ships configurable placeholders, not validated values | M5 | Open: placeholders in configuration |
| D13 | External model providers | Prohibited. Local Ollama only. Any hosted provider requires explicit written approval and a new privacy review | M8 | Applied (unchanged: local Ollama only) |
| D14 | Shared contract fixtures location | Root-level `contracts/` folder with JSON Schemas and example payloads, read by both modules' contract tests | M2 | Not applied: fixtures live in each module's tests |
