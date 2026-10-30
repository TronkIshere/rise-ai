# Architectural Decisions

Each entry uses: **Decision**, **Status**, **Context**, **Consequences**.

Status values:

- **Implemented**: present in code;
- **Accepted, not implemented**;
- **Proposed**;
- **Deprecated / superseded**.

These records reflect the code and project documentation as of 2026-10-24. There is no Git history to cite, so dates of earlier decisions are not recorded.

---

### D1. Event ingestion over synchronous REST

- **Decision:** Events are submitted via `POST /api/v1/events/monitor`, or in-process via `EventMonitoringService`, and the result is returned synchronously.
- **Status:** Implemented.
- **Context:** The module must be usable from any backend without extra infrastructure.
- **Consequences:**
  - Callers wait up to `ollama.request-timeout` when AI runs.
  - There is no queue, retry or backpressure beyond 409/503 responses.
  - Asynchronous delivery is future work ([ROADMAP.md](ROADMAP.md)).

### D2. No database or external storage; no event persistence

- **Decision:** No database, broker, Redis or vector store. Events and results are not persisted. Results are passed to `MonitoringResultPublisher` beans; the default only logs a summary.
- **Status:** **Partly superseded by D14 (2026-10-25).** Raw events and results are still not persisted; a minimized Rise projection is.
- **Context:** The MVP must run without infrastructure, and storing event data by default raises data-handling concerns.
- **Consequences:**
  - There is no history or audit trail.
  - Persistence can be added behind `MonitoringResultPublisher` and `EventDeduplicator` without changing the pipeline.

### D3. Bounded deduplication (superseded in part: JDBC store added 2026-10-30)

- **Update 2026-10-30:** `JdbcEventDeduplicator` is now the default and persists results in `monitoring_event_ledger`; `InMemoryEventDeduplicator` is selected with `monitoring.deduplication.store=memory`. The text below describes the in-memory semantics, which the JDBC store reproduces (NEW, DUPLICATE, CONFLICT, IN_PROGRESS, capacity), plus stale in-progress recovery.

- **Decision (original):** `InMemoryEventDeduplicator` keyed by `source`+`eventId`, with a content fingerprint, bounded retention and capacity, and synchronized operations. `JdbcEventDeduplicator` applies the same keys and outcomes on top of a unique primary key instead of synchronized methods.
  - Same content with a stored result → replay.
  - Same content still in progress → 409.
  - Different content → 409 conflict.
  - Full of in-progress entries → 503.
- **Status:** Implemented.
- **Context:** Retries from sources must not produce duplicate processing or contradictory results within a single instance, and a different payload must never be silently accepted under a reused ID.
- **Consequences:**
  - In-memory store: the guarantee is lost on restart and does not hold across instances. JDBC store: it survives restarts only when the configured database is a file or server database (not `jdbc:h2:mem:`), and holds across instances that share that database. Neither gives exactly-once processing.
  - `INCONCLUSIVE` results are released so retries can obtain an AI analysis.
  - `REJECTED` results are stored and replayed.
  - Any exception or `java.lang.Error` during evaluation releases the in-progress entry (`EventMonitoringServiceImpl.evaluate`).

### D4. Local-only AI through Ollama; no cloud, no fallback

- **Decision:** The only AI runtime is Ollama at `ollama.base-url`, called with the JDK `HttpClient`. There are no Spring AI or LangChain dependencies, no automatic model download, and no fallback model.
- **Status:** Implemented.
- **Context:** Event data must not leave the operator's environment, and the build must not depend on AI frameworks.
- **Consequences:**
  - Operators install the model.
  - A missing model is reported as `UNAVAILABLE` / `MODEL_NOT_INSTALLED`.
  - All Ollama formats are confined to the `ollama` package.

### D5. Model identifier is configuration, default `qwen3.5:9b`

- **Decision:** `ollama.model` comes from `${OLLAMA_MODEL:qwen3.5:9b}`. `qwen3.5:4b` is documented as a lower-resource alternative.
- **Status:** Implemented as configuration. The model itself is **untested**.
- **Context:** Model choice depends on hardware and data, and must change without Java edits.
- **Consequences:** No benchmark supports the default; evaluation on representative data is required ([AI_AND_OLLAMA.md](AI_AND_OLLAMA.md#model-configuration)).

### D6. Deterministic rules are primary; AI is optional and selective

- **Decision:** `RuleEngine` evaluates every accepted event. AI runs only when rules decide `REQUIRES_AI_ANALYSIS` (default trigger `ON_RULE_FLAG`). VALIDATION rules cannot be disabled.
- **Status:** Implemented.
- **Context:** Rules are testable, explainable and cheap; LLM calls are slow and nondeterministic.
- **Consequences:** Most events never reach the model. Rule behaviour is fully covered by unit tests.

### D7. Risk level is rule-derived; AI influences only the review flag

- **Decision:** `overallRiskLevel` is computed from FLAG finding severities only. The AI assessment can add `reviewReasons` through configurable `monitoring.policy`. Missing AI output when AI was required gives `UNKNOWN` risk (when there are no flags) and a review reason.
- **Status:** Implemented. This **supersedes D7-old** below.
- **Context:** Risk policy must be deterministic, transparent and not decided by an unconstrained model response.
- **Consequences:**
  - Risk is stable across model changes.
  - The AI cannot raise risk directly, so integrators should consider `reviewRequired` as well as risk.

### D7-old. Model-produced `riskScore` mapped to levels by fixed thresholds

- **Decision:** The model returned `riskScore` (0–1), which was mapped to levels at 0.4/0.7/0.9.
- **Status:** **Deprecated / removed.** No longer in code.
- **Context:** It was replaced because the thresholds were arbitrary and risk would have depended on model output.
- **Consequences:** The `riskScore`, `anomalyDetected` and `findings` AI fields no longer exist.

### D8. Versioned, closed AI output contract with strict validation

- **Decision:** The model must return `monitoring-assessment.v1`. It is requested via an Ollama `format` JSON schema and re-validated by `AiOutputParser`, which rejects extra fields, unknown enums and wrong types.
- **Status:** Implemented.
- **Context:** Valid-looking JSON is not trustworthy, and model output must not overwrite identity, evidence or risk.
- **Consequences:**
  - Weak models may produce more `INVALID_RESPONSE` results.
  - Contract changes require a new schema version.

### D9. AI failures are explicit and never mean "safe"

- **Decision:** Distinct statuses `DISABLED`, `UNAVAILABLE`, `TIMEOUT`, `INVALID_RESPONSE` and `FAILED`. The default `on-failure: INCONCLUSIVE`. Rule findings are always preserved.
- **Status:** Implemented.
- **Consequences:** Callers must handle `INCONCLUSIVE`, and the event can be retried.

### D10. Data minimization toward the model

- **Decision:** The payload is excluded by default. Sensitive-looking field names are redacted. `eventId`, `correlationId` and `receivedAt` are never sent. Event data is delimited and escaped.
- **Status:** Implemented.
- **Consequences:** The model has less context by default; enable `include-payload` deliberately.

### D11. Ollama is not part of application health

- **Decision:** There is no Ollama health contributor; AI availability is reported by `/api/v1/monitoring/status`.
- **Status:** Implemented.
- **Consequences:** Orchestrators will not restart the app when the model is down. Monitor the status endpoint separately if needed.

### D12. Code style follows the reference examples; no Java comments

- **Decision:** Layered packages, Lombok patterns, `ResponseAPI`, `ErrorCode`/`ApplicationException`, constructor injection, and no comments or Javadocs.
- **Status:** Implemented.
- **Consequences:** Explanations belong in `docs/`.

### D13. Test simulator as a separate Maven project with profile-based ground truth

- **Decision:** `test-simulator/` is a standalone Maven project, not a module; the Core's `pom.xml` is unchanged. It exercises the Core only over REST. Expected outcomes come from the documented contract and an explicit Core profile, `test-simulator/core-config/core-simulator.yml` (two thresholds and an event-type allow-list). Calibration probes verify that profile before a run.
- **Status:** Implemented. First run against the real service on 2026-10-25: 2,000/2,000 scenarios passed (seed 42, AI scenarios excluded), before and after the rename and the Rise changes.
- **Context:**
  - The Core is a single-module project with no aggregator.
  - The Core's defaults have no thresholds or allow-list, so those rules cannot be scored without a known configuration.
  - Ground truth must not be derived from Core responses.
- **Consequences:**
  - Changes to rule IDs, thresholds, limits, error codes, statuses or deduplication semantics require updating `CoreContract` and `ScenarioGenerator` in the simulator.
  - Simulator results are valid only when the Core runs with that profile.

### D14. Rise AI integration: module rename, minimized projection store, deterministic charity findings

- **Decision:**
  - The module is renamed from `local-ai-event-monitor` (`com.aicore.eventmonitor`) to `rise-ai-monitoring` (`com.riseai.monitoring`), default port 8081. API paths, configuration keys and environment variables are unchanged. The plan and naming table are in `docs/ai-monitoring/01-module-rename-plan.md` at the repository root.
  - Rise events are accepted on the existing endpoint. Cross-event rules use a projection in the monitor's own JDBC store (H2 by default), never the backend database.
  - Findings (`finding.v1`) are deterministic, template-explained and returned synchronously; the backend relay stores them.
- **Status:** Implemented 2026-10-25.
- **Context:** Charity patterns such as reused receipts across milestones need history; the backend owns business data and must not be read directly (backend ADR-004).
- **Consequences:**
  - The module now depends on `spring-boot-starter-jdbc`, `flyway-core` and H2.
  - Deduplication is persistent by default since 2026-10-30 (D3 update); re-evaluation after a lost ledger stays harmless because rules are deterministic and projection writes are idempotent.
  - Time-based rules (overdue milestones) need a push path and are not implemented.

### P1. Authentication for API and actuator

- **Status:** **Partly implemented (2026-10-25).** A static service key (`monitoring.security.service-key`, Bearer header, constant-time compare) protects `/api/v1/**` and `/actuator/metrics/**`; required by the `riseai` profile. Health stays open. Not implemented: key rotation (two keys), mTLS, per-client identities.
- **Context:** Securing endpoints is required before exposing the service beyond a trusted network; the `riseai` profile also binds to `127.0.0.1` by default.
