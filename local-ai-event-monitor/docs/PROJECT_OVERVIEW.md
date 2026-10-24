# Project Overview

## Purpose

Applications often need to ask *"does this event look wrong?"*. This module answers that question in one reusable, domain-agnostic place. It:

1. receives an event from another system over REST, or in-process via `EventMonitoringService`;
2. validates and normalizes the event;
3. detects duplicate event IDs within a bounded, in-memory scope;
4. applies deterministic rules;
5. when rules call for it, asks a **locally hosted Ollama model** for a constrained, advisory interpretation;
6. returns a structured result: status, rule findings, AI analysis, a rule-derived risk level, and a human-review flag.

It produces **findings only**. It never acts on them.

## Scope

**In scope (implemented):**

- event ingestion and validation, with size limits;
- normalization;
- in-memory duplicate detection;
- six deterministic rules;
- optional Ollama analysis with strict output validation;
- deterministic risk and review policy;
- metrics;
- a status endpoint and a rules endpoint.

**Non-goals (deliberately absent; see [DECISIONS.md](DECISIONS.md)):**

- No business actions: no blocking, payments, commands, tool calls, or writes to other systems.
- No domain-specific rules (finance, healthcare, and so on).
- No cloud AI APIs, Spring AI or LangChain. No automatic model download, and no model fallback.
- No database, message broker, Redis or vector store. No event or result persistence.
- No authentication or authorization.

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
| Rule engine and six rules | Implemented and unit-tested |
| Ollama adapter and analyzer | Implemented. Tested against a local stub HTTP server, **not** against a real Ollama or model. |
| AI output contract `monitoring-assessment.v1` | Implemented and unit-tested |
| Deterministic risk and review policy | Implemented and unit-tested |
| Metrics | Implemented. `MonitoringMetricsTest` has not been executed. |
| Spring wiring and integration tests | Written. **Never executed** (see [TESTING.md](TESTING.md)). |
| Test simulator (`test-simulator/`, separate Maven project) | Implemented; 38 unit tests pass; **never run against the real Core** (see [TEST_SIMULATOR.md](TEST_SIMULATOR.md)) |
| Maven build (`mvn clean test`, `mvn package`) | **Never executed.** Maven Central was unreachable in the authoring environment. |
| Persistence, authentication, async processing | Not implemented (see [ROADMAP.md](ROADMAP.md)) |
