# AI Assistant Handoff

## Mandatory rules

1. **Inspect the source before modifying behaviour.** Documents can be stale; source code and tests take precedence when they disagree with documentation.
2. Before **substantial** changes (new features, API, architecture, configuration semantics), read [README.md](README.md), [ARCHITECTURE.md](ARCHITECTURE.md), [DECISIONS.md](DECISIONS.md) and [ROADMAP.md](ROADMAP.md). For small, isolated fixes, read this file and the code involved.
3. **Update the affected documentation in the same change** whenever you alter architecture, API contracts, configuration, behaviour or test coverage.
4. Never claim a build, test or model run you did not execute. Record real results in [TESTING.md](TESTING.md).
5. Do not add comments or Javadocs to Java files. Follow the existing conventions ([DEVELOPMENT.md](DEVELOPMENT.md#code-conventions)).
6. If you change rule IDs, thresholds, default limits, error codes, statuses or deduplication semantics, update the test simulator's `CoreContract` and `ScenarioGenerator` as well ([TEST_SIMULATOR.md](TEST_SIMULATOR.md)). Lombok constructor parameters follow field order, so reordering fields means updating tests that call constructors directly.

## What the project is

A Spring Boot 3.5.15 / Java 21 / Maven module.

- **Input:** events over `POST /api/v1/events/monitor`, including Rise AI backend envelopes (`source = rise-ai-backend`).
- **Processing:** validate, normalize and deduplicate in memory; apply six deterministic rules; for Rise events, apply eight charity rules against a minimized projection; when rules require it, ask a local Ollama model for an advisory assessment.
- **Output:** status, rule findings, `finding.v1` charity findings, AI analysis, a **rule-derived** risk level, and a review flag.

It takes no actions and uses no cloud AI. Its only database is its own projection store (H2), never the backend's. Details: [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md). Cross-module plan and status: `docs/ai-monitoring/` at the repository root.

## Where things are

Package root: `src/main/java/com/riseai/monitoring`.

| Concern | Start at |
|---|---|
| Orchestration | `service/impl/EventMonitoringServiceImpl.java` |
| Rules | `rule/RuleEngine.java` and the `*Rule.java` classes |
| Deduplication | `deduplication/InMemoryEventDeduplicator.java` |
| Status, risk, review | `result/MonitoringResultAssembler.java` |
| AI prompt and validation | `analysis/AnalysisPromptBuilder.java`, `analysis/AiOutputParser.java` |
| Ollama HTTP | `ollama/OllamaClient.java`, `ollama/OllamaEventAnalyzer.java` |
| Configuration | `configuration/MonitoringProperties.java`, `configuration/OllamaProperties.java`, `src/main/resources/application.yml` |
| Test simulator (separate Maven project) | `test-simulator/README.md`, [TEST_SIMULATOR.md](TEST_SIMULATOR.md) |
| Errors | `exception/ErrorCode.java`, `exception/GlobalExceptionHandler.java` |

## Constraints you must not break

These are each backed by a test or decision:

- `overallRiskLevel` never depends on AI output ([DECISIONS.md](DECISIONS.md) D7).
- AI failure never removes rule findings and never yields `LOW` risk when AI was required ([D9](DECISIONS.md)).
- No network call at startup; no call to Ollama when `monitoring.ai.enabled=false`.
- AI output must match `monitoring-assessment.v1` exactly. Extra fields are rejected ([D8](DECISIONS.md)).
- `eventId` and `correlationId` stay distinct, and neither is sent to the model.
- Only Ollama, only at the configured URL ([D4](DECISIONS.md)). Do not add cloud providers or a model fallback without an explicit requirement.
- No broker, and no storage beyond the minimized Rise projection, without a concrete requirement ([D2](DECISIONS.md), [D14](DECISIONS.md)).

## Current status and known issues

- **Verified (2026-10-25, JDK 21, `./mvnw`):**
  - `./mvnw clean package`: 144 of 144 tests pass, including all Spring/MockMvc integration tests and the metrics tests;
  - 38 of 38 test-simulator unit tests pass; the simulator scored 2,000/2,000 against the running jar;
  - end-to-end delivery from `rise-ai-backend` (see `docs/ai-monitoring/06-progress-report.md` at the repository root).
- **Unverified:**
  - any real Ollama or model run, including whether the `qwen3.5` tags exist.
- **Known issues:**
  - an uncached Ollama call on each status request;
  - authentication is a single static service key (no rotation, no mTLS).

  Full list: [ROADMAP.md](ROADMAP.md).

## How to verify a change

1. `./mvnw clean test`. All tests run without Ollama ([TESTING.md](TESTING.md)).
2. For API changes, run the smoke test in [DEVELOPMENT.md](DEVELOPMENT.md#manual-smoke-test-after-starting-the-app).
3. For AI changes, test against `StubOllamaServer` first, then a real model if available, and record which you used.
4. For changes to the API contract, rules or deduplication, run the test simulator smoke test against the running Core.

## Expected workflow

1. Read this file.
2. Read the documents relevant to the task.
3. Inspect the actual source files.
4. List any doc/code conflicts **before** editing.
5. Implement and test.
6. Update the affected documentation.
7. Report what changed, what was tested (with real results), and what remains unverified.
