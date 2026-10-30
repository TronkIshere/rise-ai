# Project Overview

## Purpose

**Rise AI Monitoring** is the monitoring service of the Rise AI charity platform. Since 2026-10-25 it receives versioned business events from `rise-ai-backend` (through the backend's transactional outbox and relay) and returns deterministic, explainable charity findings, such as the same receipt file reused across milestones, reconciliation inconsistencies, and large approvals on minimal evidence. The backend stores the findings for administrator review. Findings are advisory: they never approve, block or move funds. The generic, domain-agnostic pipeline it started as is unchanged and still serves other event sources.

Applications often need to ask *"does this event look wrong?"*. The generic pipeline answers that question in one reusable place. It:

1. receives an event from another system over REST, or in-process via `EventMonitoringService`;
2. validates and normalizes the event;
3. detects duplicate event IDs within a bounded scope, persisted in the monitoring database by default;
4. applies deterministic rules;
5. when rules call for it, asks a **locally hosted Ollama model** for a constrained, advisory interpretation;
6. returns a structured result: status, rule findings, AI analysis, a rule-derived risk level, and a human-review flag.

It produces **findings only**. It never acts on them.

## Scope

**In scope (implemented):**

- event ingestion and validation, with size limits;
- normalization;
- duplicate detection (persistent JDBC ledger by default, in-memory optional);
- six deterministic rules;
- eight Rise AI charity rules with a minimized, durable cross-event projection (H2), producing `finding.v1` findings;
- service-key authentication (Bearer) for API and metrics;
- optional Ollama analysis with strict output validation;
- deterministic risk and review policy;
- metrics;
- a status endpoint and a rules endpoint.

**Non-goals (deliberately absent; see [DECISIONS.md](DECISIONS.md)):**

- No business actions: no blocking, payments, commands, tool calls, or writes to other systems.
- No domain-specific rules (finance, healthcare, and so on).
- No cloud AI APIs, Spring AI or LangChain. No automatic model download, and no model fallback.
- No message broker, Redis or vector store. No raw event or result persistence; only the minimized Rise projection (H2) is stored.
- Authentication is limited to one static service key (optional; required by the `riseai` profile). No per-user authorization: findings are reviewed in the backend, not here.

## Intended users and integration model

- **Backend services** call `POST /api/v1/events/monitor` and use `monitoringStatus`, `reviewRequired` and `overallRiskLevel`.
- **Spring applications** that include the source can inject `EventMonitoringService` directly. The project is packaged as a runnable Spring Boot application, **not** as a library jar.
- **Operators** run Ollama locally and pull a model themselves.

## Technology stack

Verified from `pom.xml` and the source:

| Item | Value | Notes |
|---|---|---|
| Java | 21 | `<java.version>21</java.version>` |
| Spring Boot | 3.5.15 | Parent POM |
| Build | Maven, with Maven Wrapper 3.3.2 → Maven 3.9.11 | `.mvn/wrapper/maven-wrapper.properties`; the wrapper download has never been exercised |
| Starters | web, validation, actuator, test | Plus `spring-boot-configuration-processor` |
| Lombok | version managed by the Spring Boot parent | Explicit annotation-processor configuration in `pom.xml` |
| HTTP client for Ollama | JDK `java.net.http.HttpClient` | No extra dependency |
| JSON | Jackson, via Spring Web | |
| Metrics | Micrometer, via Actuator | |
| AI runtime | Ollama, external process | Configured model `qwen3.5:9b`, **never run against this code** |

## Current implementation status

| Area | Status |
|---|---|
| REST API (3 endpoints) and actuator | Implemented. Compiled; not run in a Spring container. |
| Validation, normalization, request size limit | Implemented and unit-tested |
| In-memory deduplication (conflict, in-progress, replay, capacity) | Implemented and unit-tested, including concurrency |
| Over-long envelope event ids rejected as invalid (400, `FAILED` outbox row and alert) instead of failing the projection insert | Implemented 2026-10-30; covered by `RiseMonitoringIntegrationTest` (passed in the 218/218 baseline) |
| JDBC deduplication ledger (restart replay on a durable database, stale in-progress takeover, retention, capacity, concurrent registration) | Implemented 2026-10-30; `JdbcEventDeduplicatorTest` passed (10/10); restart replay verified manually on a file-backed H2 database only, multi-instance use unverified |
| Rule engine and six rules | Implemented and unit-tested |
| Ollama adapter and analyzer | Implemented. Tested against a local stub HTTP server, **not** against a real Ollama or model. |
| AI output contract `monitoring-assessment.v1` | Implemented and unit-tested |
| Deterministic risk and review policy | Implemented and unit-tested |
| Metrics | Implemented. `MonitoringMetricsTest` has not been executed. |
| Spring wiring and integration tests | Written. **Never executed** (see [TESTING.md](TESTING.md)). |
| Test simulator (`test-simulator/`, separate Maven project) | Implemented; 38 unit tests pass; ran against the real service on 2026-10-25: 2,000/2,000 (see [TEST_SIMULATOR.md)) |
| Maven build (`mvn clean test`, `mvn package`) | **Never executed.** Maven Central was unreachable in the authoring environment. |
| Persistence, authentication, async processing | Not implemented (see [ROADMAP.md](ROADMAP.md)) |
