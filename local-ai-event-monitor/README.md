# Local AI Event Monitoring Core

A reusable, domain-agnostic Spring Boot module. It receives events from other systems, validates and normalizes them, and applies deterministic monitoring rules. When policy calls for it, it asks a **locally hosted Ollama model** for a constrained, advisory interpretation. It then returns a structured monitoring result.

It never calls cloud AI APIs, never executes anything an event or a model asks for, and keeps working when Ollama is offline.

> **Verification status.**
> - What was run: the code compiles on JDK 21, and 109 non-Spring tests were run and passed.
> - What was not run: `mvn clean test`, `mvn package`, and the Spring Boot integration tests. Maven Central was unreachable in the build environment.
> - The configured model `qwen3.5:9b` has **not** been run locally.
>
> Details are in [section 21](#21-current-limitations). Run `./mvnw clean test` on your machine first.

## Documentation for developers and AI assistants

Precise, maintained project documentation lives in [`docs/`](docs/README.md). It is canonical where it overlaps with this README.

- [docs/README.md](docs/README.md): documentation index and reading order.
- [docs/AI_HANDOFF.md](docs/AI_HANDOFF.md): start here if you are an AI coding assistant continuing this project.

## Contents

1. [What it is](#1-what-it-is)
2. [Boundaries and non-goals](#2-boundaries-and-non-goals)
3. [Storage and idempotency policy](#3-storage-and-idempotency-policy)
4. [Event identity: eventId vs correlationId](#4-event-identity-eventid-vs-correlationid)
5. [Architecture and package structure](#5-architecture-and-package-structure)
6. [Event lifecycle](#6-event-lifecycle)
7. [Responsibilities: rules, AI analyzer, orchestrator](#7-responsibilities-rules-ai-analyzer-orchestrator)
8. [Deterministic rules](#8-deterministic-rules)
9. [AI analysis contract and validation](#9-ai-analysis-contract-and-validation)
10. [Risk and review policy](#10-risk-and-review-policy)
11. [Ollama and model setup](#11-ollama-and-model-setup)
12. [Requirements](#12-requirements)
13. [Configuration](#13-configuration)
14. [API](#14-api)
15. [Running locally, with or without Ollama](#15-running-locally-with-or-without-ollama)
16. [Running tests without Ollama](#16-running-tests-without-ollama)
17. [Extending: rules, analysis, delivery, storage](#17-extending-rules-analysis-delivery-storage)
18. [Integrating from another backend](#18-integrating-from-another-backend)
19. [Security and data handling](#19-security-and-data-handling)
20. [Limitations of LLM-based interpretation](#20-limitations-of-llm-based-interpretation)
21. [Current limitations](#21-current-limitations)
22. [Change history](#22-change-history)
23. [Troubleshooting](#23-troubleshooting)

## 1. What it is

A single Spring Boot 3.5 application (Java 21, Maven) exposing:

- `POST /api/v1/events/monitor`: monitor one event.
- `GET /api/v1/monitoring/status`: settings, storage policy, and Ollama/model availability.
- `GET /api/v1/monitoring/rules`: the deterministic rules and whether each is active.
- Actuator: `/actuator/health`, `/actuator/info`, `/actuator/metrics`.

The same pipeline is available in-process through the `EventMonitoringService` bean.

## 2. Boundaries and non-goals

- **Findings only.** It never blocks transactions, moves money, executes commands, calls tools, or mutates other systems. The consuming application decides what to do with a result.
- **Domain-agnostic.** It contains no finance, healthcare, charity or other domain logic. Behaviour comes from configuration and from rules you add.
- **Local AI only.** Ollama on a configured URL is the only AI runtime. There are no OpenAI, Anthropic, Gemini, Spring AI or LangChain dependencies, and no cloud fallback.
- **No model management.** Models are never downloaded by the application.
- **No database, broker, Redis or vector store.** See [section 3](#3-storage-and-idempotency-policy).
- **No authentication.** See [section 19](#19-security-and-data-handling).

## 3. Storage and idempotency policy

### Default MVP policy

| Concern | Default behaviour |
|---|---|
| Event persistence | **None.** Events are processed in memory and discarded. No event history is written anywhere. |
| Monitoring result persistence | **None.** Results are returned to the caller and passed to `MonitoringResultPublisher` beans. The bundled publisher only writes a one-line log summary without payload data. |
| Idempotency store | **In-memory**, per application instance (`InMemoryEventDeduplicator`). |
| External storage dependencies | **None.** |

`GET /api/v1/monitoring/status` reports this explicitly:

```json
"eventPersistence": "NONE",
"deduplicationStore": "IN_MEMORY",
"deduplicationScope": "source+eventId, single application instance",
"deduplicationRetention": "PT24H",
"maxTrackedEvents": 10000
```

### Deduplication scope

- **Key:** `source` + `eventId`. The same `eventId` from two different sources is treated as two different events.
- **Content fingerprint:** a SHA-256 over the normalized `eventType`, `source`, `occurredAt`, `severity`, `attributes` and `payload`. Object keys are sorted. `receivedAt` and `correlationId` are excluded, so a retry carrying new delivery metadata still matches.
- **Retention:** `monitoring.deduplication.retention`, default 24 h, measured from first registration.
- **Capacity:** `monitoring.deduplication.max-tracked-events`, default 10 000. When full, the oldest *completed* entry is evicted. An *in-progress* entry is never evicted. If every slot is in progress, new events get **503 / code 2003** until capacity frees up. This keeps concurrent duplicate protection intact under load.

### Duplicate behaviour

| Situation | Response |
|---|---|
| Same key, same fingerprint, earlier result **available** | HTTP 200 with the **stored** result, `duplicate: true`. No reprocessing, no second AI call. |
| Same key, same fingerprint, still **being processed** (concurrent submission) | HTTP 409, code `2002` (`EVENT_IN_PROGRESS`). Retry later. A result is never invented. |
| Same key, **different** fingerprint | HTTP 409, code `2001` (`EVENT_ID_CONFLICT`). It is not silently treated as an unrelated event. |
| Earlier attempt ended `INCONCLUSIVE` or failed | That entry was released, so the event is processed again. Retrying after Ollama recovers yields a real analysis. |
| Earlier attempt expired, was evicted, or the application restarted | **Not detected.** The event is processed as new. |

Concurrency safety comes from synchronized registration: exactly one of N simultaneous identical submissions proceeds, and this is tested.

### What this is not

- **Not durable.** All deduplication state is lost on restart.
- **Not distributed.** Two instances behind a load balancer do not share state and can each process the same event.
- **Not exactly-once.** Processing is at-most-once per instance within the retention window and capacity, and nothing more.
- **Numbers are compared by JSON form.** `1` and `1.0` are treated as different content.

Durable event history and cross-instance idempotency are **future capabilities**. The extension points already exist (see [section 17](#17-extending-rules-analysis-delivery-storage)).

## 4. Event identity: eventId vs correlationId

| Field | Meaning | Used for |
|---|---|---|
| `eventId` | Identity of **this event** as assigned by the source. **Required**: non-blank, ≤128 characters, `[A-Za-z0-9._:-]`. Must stay the same when the source retries delivery of the same event. | Deduplication, together with `source`. Logged and returned. **Never sent to the model.** |
| `correlationId` | Identifier that **links related activity** (a request trace, a business flow) across systems. Many events may share one. Optional, 1–64 characters `[A-Za-z0-9._:-]`. | Logging and tracing, and returned in the result. **Never used for deduplication, never sent to the model.** |

`correlationId` resolution order:

1. the event's `correlationId` field;
2. the `X-Correlation-Id` header (validated);
3. a generated UUID.

The two values are never derived from each other.

## 5. Architecture and package structure

Root package: `com.aicore.eventmonitor`. Layer packages follow the reference conventions:

- `controller`, `service` / `service.impl`, `dto.request.*` / `dto.response.*`, `exception`, `configuration`, `constants`;
- Lombok DTOs;
- the `ResponseAPI<T>` envelope and `ErrorResponse` errors;
- `ErrorCode` + `ApplicationException`.

```
configuration/   MonitoringProperties, OllamaProperties, ClockConfig
constants/       PlatformConstants
controller/      EventMonitoringController, MonitoringStatusController
dto/…            MonitoringEventRequest, ResponseAPI, ErrorResponse, MonitoringResultResponse,
                 RuleFindingResponse, AiAnalysisResponse, MonitoringStatusResponse, AiRuntimeStatusResponse, RuleResponse
exception/       ErrorCode, ApplicationException, GlobalExceptionHandler
filter/          CorrelationIdFilter, RequestSizeLimitFilter, RequestBodyTooLargeException
core/event/      MonitoringEvent, Severity
core/rule/       MonitoringRule, RuleFinding, RuleEffect, RuleCategory, RuleDecision, RuleEvaluation
core/analysis/   AiEventAnalyzer, AiAnalysis, AiAssessment, AiConfidence, AnalysisStatus, RiskLevel,
                 AiRuntimeStatus, AiRuntimeState
core/result/     MonitoringResult, MonitoringStatus, MonitoringResultPublisher
normalization/   EventNormalizer
deduplication/   EventDeduplicator, InMemoryEventDeduplicator, DeduplicationOutcome, DeduplicationStatus
rule/            RuleEngine + six rules
analysis/        AnalysisPromptBuilder, AiOutputParser, AiAssessmentOutput, AnalysisPrompt,
                 InvalidAiOutputException, RiskLevelNormalizer
ollama/          OllamaClient, OllamaEventAnalyzer, OllamaException, OllamaErrorType, dto/*
result/          MonitoringResultAssembler, LoggingMonitoringResultPublisher
mapper/          MonitoringResultMapper
observability/   MonitoringMetrics
service/         EventMonitoringService, MonitoringStatusService (+ impl/)
```

**Boundaries:**

- `core.*` has no Ollama or HTTP types.
- All Ollama request and response formats live in `ollama`.
- `analysis` builds prompts and validates output without depending on Ollama.

## 6. Event lifecycle

```
 1. CorrelationIdFilter        validate X-Correlation-Id or generate one
 2. RequestSizeLimitFilter     reject bodies > monitoring.limits.max-request-bytes (413)
 3. Bean Validation            required eventId/eventType/source/occurredAt, patterns, lengths (400)
 4. EventNormalizer            trim, lower-case eventType, default receivedAt, scalar-only attributes,
                               payload depth limit, payload size, content fingerprint (400 on failure)
 5. EventDeduplicator          NEW | DUPLICATE (replay) | CONFLICT (409) | IN_PROGRESS (409) | CAPACITY_EXHAUSTED (503)
 6. RuleEngine                 findings + decision: REJECT | REQUIRES_AI_ANALYSIS | RULES_SUFFICIENT
 7. AI policy                  skip if rejected or not required; DISABLED if monitoring.ai.enabled=false
 8. OllamaEventAnalyzer        minimized, redacted prompt -> Ollama -> strict schema validation
 9. MonitoringResultAssembler  deterministic status, risk level and review decision
10. Deduplicator               store result (INCONCLUSIVE results are released for retry)
11. Metrics + publishers       Micrometer counters/timer; MonitoringResultPublisher beans
12. ResponseAPI<MonitoringResultResponse>
```

A rule rejection is a monitoring outcome, not an API error. It returns HTTP 200 with `monitoringStatus: REJECTED`.

## 7. Responsibilities: rules, AI analyzer, orchestrator

| Component | Responsibility | Must not |
|---|---|---|
| **Rule engine** | Deterministic findings with evidence (rule id, effect, severity, message). Decides whether an event is rejected, needs AI, or is fully handled by rules. | Depend on Ollama or on model output. |
| **AI analyzer** | Optional semantic layer. It interprets normalized event data and matched rule findings, suggests plausible causes, names missing evidence, and states its uncertainty. | Override, remove or downgrade rule findings. It also must not set risk level, event identity or source metadata, claim fraud or malice, or take actions. |
| **Orchestrator** (`EventMonitoringServiceImpl` + `MonitoringResultAssembler`) | Applies explicit, configurable policy. Rules decide whether the AI is called. Risk comes from rules only. The AI assessment and AI availability feed the human-review decision. | Treat missing AI output as safe, or let model output alone determine risk. |

AI analysis is **not** run for every event. With the default `monitoring.ai.trigger: ON_RULE_FLAG`, the model is called only when a monitoring rule flags the event, or when the event type is listed in `monitoring.ai.always-analyze-event-types`.

## 8. Deterministic rules

- **VALIDATION** rules always run and REJECT.
- **MONITORING** rules can be disabled and FLAG.

| Rule id | Category | What it checks |
|---|---|---|
| `required-fields` | VALIDATION | `eventId`, `eventType`, `source`, `occurredAt`, plus `monitoring.rules.required-attributes` |
| `timestamp-validity` | VALIDATION | `receivedAt` not in the future; `occurredAt` not after `receivedAt` beyond `max-clock-skew`; not older than `max-event-age` |
| `supported-event-type` | VALIDATION | `eventType` in `allowed-event-types` (an empty list allows all) |
| `payload-limits` | VALIDATION | payload bytes ≤ `max-payload-bytes`; attribute count ≤ `max-attributes` |
| `attribute-threshold` | MONITORING | configured numeric `min`/`max` per attribute, optionally per event type; non-numeric values are flagged MEDIUM |
| `reported-severity` | MONITORING | source-reported `severity` ≥ `flag-reported-severity-at` |

A rule that throws an exception becomes a MEDIUM flag, "rule could not be evaluated", rather than a failed request.

**Decision:**

- any REJECT → `REJECT`;
- otherwise any FLAG, `trigger: ALWAYS`, or the event type in `always-analyze-event-types` → `REQUIRES_AI_ANALYSIS`;
- otherwise → `RULES_SUFFICIENT`.

## 9. AI analysis contract and validation

### Output DTO: `monitoring-assessment.v1`

Defined by `analysis.AiAssessmentOutput` (`SCHEMA_VERSION = "monitoring-assessment.v1"`). The model must return exactly:

| Field | Type | Meaning |
|---|---|---|
| `assessment` | enum | `CONSISTENT_WITH_EXPECTED_BEHAVIOR`, `NEEDS_REVIEW`, `LIKELY_ANOMALOUS`, `INSUFFICIENT_EVIDENCE` |
| `confidence` | enum | `LOW`, `MEDIUM`, `HIGH`: the **model's own qualitative self-report**. It is **not** a probability and **not calibrated**. Do not threshold business decisions on it. |
| `explanation` | string | Short interpretation including its uncertainty |
| `possibleCauses` | string[] | ≤ `max-list-items` plausible causes supported by the evidence |
| `missingEvidence` | string[] | ≤ `max-list-items` items of information that would reduce uncertainty |
| `suggestedReview` | boolean | Whether the model thinks a human should look at the event |

There is deliberately no "fraud", "malicious" or numeric risk field. The schema version is set by the application, not by the model. Change the schema by adding a new version, not by editing v1 in place.

### How the output is obtained and validated

- The JSON schema (enums, `maxItems`, all fields required, `additionalProperties: false`) is sent as Ollama's `format` parameter. This is Ollama's structured-output feature and needs Ollama 0.5+.
- `AiOutputParser` then **re-validates everything**. Constrained decoding is not trusted on its own, and valid-looking JSON is not trusted either. The parser:
  - requires a single JSON object with no trailing text (a surrounding Markdown code fence is tolerated);
  - requires all six fields;
  - **rejects any extra field**, so the model cannot inject `eventId`, `riskLevel`, `ruleFindings` or similar;
  - accepts only exact enum names (`needs_review` or `FRAUD` are rejected);
  - checks types strictly (a string `"true"` is not a boolean);
  - enforces list size limits;
  - requires a non-blank explanation;
  - strips control characters and truncates text to the configured lengths.

### Analysis statuses

| Status | Meaning |
|---|---|
| `COMPLETED` | Valid `monitoring-assessment.v1` received |
| `SKIPPED` | Not needed: event rejected, or rules sufficient |
| `DISABLED` | AI needed by policy but `monitoring.ai.enabled=false` (no network call is made) |
| `UNAVAILABLE` | Connection refused, connect timeout, model not installed, or no model configured |
| `TIMEOUT` | No response within `ollama.request-timeout` |
| `INVALID_RESPONSE` | Empty output, invalid JSON, schema violation, unexpected enum or field, or an unreadable Ollama response body |
| `FAILED` | Ollama returned an HTTP error other than "model not found", or the analyzer failed unexpectedly |

For any status other than `COMPLETED`, every assessment field is absent. `statusDetail` explains why, using fixed application text and never raw model output.

### What is sent to the model (data minimization)

| Data | Sent? |
|---|---|
| Fields | `eventType`, `source`, `occurredAt`, `reportedSeverity`, `attributes` |
| Rule findings | All, as authoritative evidence |
| `payload` | **Not sent by default.** The model only sees `payloadOmitted: true`. Opt in with `monitoring.ai.include-payload: true`. |
| `eventId`, `correlationId`, `receivedAt` | Never |

When fields are sent:

- Any attribute or payload field whose name contains a `monitoring.ai.redacted-field-names` token (default `password, secret, token, authorization, apikey, credential`, case-insensitive) has its value replaced by `[REDACTED]`. The redaction is recursive for the payload.
- Event data is serialized as JSON inside `<event_data>` delimiters, with every `<` escaped as `\u003c`, so payload text cannot close the delimiter.
- Data longer than `max-prompt-event-characters` is truncated, and the analysis `statusDetail` says so.

## 10. Risk and review policy

Risk and review are computed by `MonitoringResultAssembler` with explicit, deterministic rules.

**`overallRiskLevel` comes from rule findings only.** It is the highest severity among FLAG findings (INFO/LOW→LOW, MEDIUM→MEDIUM, HIGH→HIGH, CRITICAL→CRITICAL), and it is identical whatever the AI returns. This is tested by `riskLevelIsDeterminedByRulesWhateverTheAiReturns`. With no flags:

| Situation | `overallRiskLevel` |
|---|---|
| Rejected | `UNKNOWN` (not assessed) |
| AI not required, completed, or disabled by configuration | `LOW` |
| AI was required but did not complete (`UNAVAILABLE`, `TIMEOUT`, `INVALID_RESPONSE`, `FAILED`) | **`UNKNOWN`**: never `LOW` |

**`monitoringStatus`:**

| Status | When |
|---|---|
| `REJECTED` | A VALIDATION rule rejected the event |
| `COMPLETED` | Rules sufficient, or AI completed |
| `COMPLETED_WITHOUT_AI` | AI disabled by configuration, or AI failed while `monitoring.ai.on-failure: RULES_ONLY` |
| `INCONCLUSIVE` | AI required but not completed (default `on-failure: INCONCLUSIVE`). Rule findings are preserved, and the result is not stored for deduplication. |

**`reviewRequired` / `reviewReasons`:** the AI's influence is limited to this field and is governed by `monitoring.policy`. A review is required when any of the following holds:

- a FLAG finding has severity ≥ `review-rule-severity` (default `HIGH`);
- AI was required but did not complete;
- the AI `assessment` is in `review-ai-assessments` (default `NEEDS_REVIEW`, `LIKELY_ANOMALOUS`, `INSUFFICIENT_EVIDENCE`);
- `suggestedReview` is true and `review-when-ai-suggests: true`.

Rejected events never require review; the data must be fixed instead.

These defaults are **illustrative starting points, not universal thresholds**. Tune them per deployment.

## 11. Ollama and model setup

The application never downloads or manages models. You do that once, manually:

1. Install Ollama from <https://ollama.com/download>. Version 0.5+ is needed for schema-constrained `format`. The default `think: false` setting needs a version that supports the `think` request field; see the troubleshooting section if yours does not.
2. Start the server:
   ```bash
   ollama serve
   ```
   It listens on `http://localhost:11434`.
3. Pull the configured default model:
   ```bash
   ollama pull qwen3.5:9b
   ```
4. Optional: try the lower-resource alternative.
   ```bash
   ollama pull qwen3.5:4b
   export OLLAMA_MODEL=qwen3.5:4b
   ```
5. Confirm what is installed:
   ```bash
   ollama list
   curl -s http://localhost:8080/api/v1/monitoring/status
   ```
   The status call should show `"aiRuntime": {"state": "AVAILABLE", ...}`.

**About the model choice.**

- `qwen3.5:9b` is the configured default and `qwen3.5:4b` is the documented lower-resource alternative. They are practical starting points, not a claim of optimality.
- Check the official library (<https://ollama.com/library/qwen3.5>) for current tags. The tags could not be retrieved from the environment this project was built in, and **neither model has been run locally against this code**.
- If a tag does not exist or does not fit your hardware, set `OLLAMA_MODEL` to any installed chat model. No Java change is needed.
- There is **no automatic model fallback**. A missing model is reported as `UNAVAILABLE` / `MODEL_NOT_INSTALLED`.

**Approximate artifact sizes: reference estimates only.**

| Tag | Rough download size (≈4-bit quantization) |
|---|---|
| `qwen3.5:9b` | ~6–7 GB |
| `qwen3.5:4b` | ~3–3.5 GB |

These figures are **estimated from parameter count at roughly 0.6–0.75 GB per billion parameters**, not read from the library. Actual disk and memory use depends on:

- the quantization of the tag you pull;
- the context size (`ollama.context-length` / `num_ctx`);
- any extra model components;
- runtime settings such as GPU offload and keep-alive.

Check the real figure with `ollama show <tag>` or on the library page.

**Choosing a model for real use.** Evaluate candidate models on **representative event data from your domain**, measuring:

- the rate of `INVALID_RESPONSE` results (schema adherence);
- latency against `request-timeout`;
- memory and CPU/GPU use;
- the quality of `possibleCauses` and `missingEvidence` as judged by people who know the domain.

**Changing the model or timeouts:** use environment variables, or edit `application.yml`.

```bash
export OLLAMA_MODEL=qwen3.5:4b
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_CONNECT_TIMEOUT=2s
export OLLAMA_REQUEST_TIMEOUT=120s
export OLLAMA_THINK=false
```

Qwen3-family models can emit a "thinking" phase; `think: false` asks Ollama to skip it. `ollama.context-length` is unset by default (Ollama's default applies). Set it to control `num_ctx`.

## 12. Requirements

- **Java 21.**
- **Maven 3.9+**, or the included `./mvnw` (Wrapper 3.3.2 → Maven 3.9.11).
- **Network access to Maven Central** for the first build.
- **Ollama** is optional at runtime.

`pom.xml` keeps the baseline: Spring Boot 3.5.15, Web, Validation, Actuator, configuration processor and Test, plus Lombok (part of the reference style).

**No dependency was added for Ollama.** The adapter uses the JDK's `java.net.http.HttpClient` and the Jackson already provided by Spring Web.

## 13. Configuration

Environment variables override the defaults shown.

| Property | Env | Default |
|---|---|---|
| `ollama.base-url` | `OLLAMA_BASE_URL` | `http://localhost:11434` |
| `ollama.model` | `OLLAMA_MODEL` | `qwen3.5:9b` |
| `ollama.connect-timeout` | `OLLAMA_CONNECT_TIMEOUT` | `2s` |
| `ollama.request-timeout` (read/request) | `OLLAMA_REQUEST_TIMEOUT` | `60s` |
| `ollama.status-timeout` | (none) | `3s` |
| `ollama.temperature` | (none) | `0.0` |
| `ollama.max-output-tokens` (`num_predict`) | (none) | `512` |
| `ollama.think` | `OLLAMA_THINK` | `false` (set empty to omit the field) |
| `ollama.context-length` (`num_ctx`) | (none) | unset |
| `monitoring.ai.enabled` | `MONITORING_AI_ENABLED` | `true` |
| `monitoring.ai.trigger` (`ON_RULE_FLAG` \| `ALWAYS`) | `MONITORING_AI_TRIGGER` | `ON_RULE_FLAG` |
| `monitoring.ai.always-analyze-event-types` | (none) | `[]` |
| `monitoring.ai.on-failure` (`INCONCLUSIVE` \| `RULES_ONLY`) | `MONITORING_AI_ON_FAILURE` | `INCONCLUSIVE` |
| `monitoring.ai.include-payload` | `MONITORING_AI_INCLUDE_PAYLOAD` | `false` |
| `monitoring.ai.redacted-field-names` | (none) | `[password, secret, token, authorization, apikey, credential]` |
| `monitoring.ai.max-prompt-event-characters` | (none) | `8000` |
| `monitoring.ai.max-list-items` / `max-item-length` / `max-explanation-length` | (none) | `5` / `300` / `1000` |
| `monitoring.policy.review-rule-severity` | (none) | `HIGH` |
| `monitoring.policy.review-ai-assessments` | (none) | `[NEEDS_REVIEW, LIKELY_ANOMALOUS, INSUFFICIENT_EVIDENCE]` |
| `monitoring.policy.review-when-ai-suggests` | (none) | `true` |
| `monitoring.rules.enabled` (MONITORING rules only) | `MONITORING_RULES_ENABLED` | `true` |
| `monitoring.rules.disabled-rules` / `allowed-event-types` / `required-attributes` | (none) | `[]` |
| `monitoring.rules.max-clock-skew` / `max-event-age` | (none) | `5m` / `7d` |
| `monitoring.rules.flag-reported-severity-at` | (none) | `HIGH` |
| `monitoring.rules.thresholds` | (none) | `[]` |
| `monitoring.limits.max-request-bytes` | `MONITORING_MAX_REQUEST_BYTES` | `262144` |
| `monitoring.limits.max-payload-bytes` | `MONITORING_MAX_PAYLOAD_BYTES` | `65536` |
| `monitoring.limits.max-payload-depth` / `max-attributes` / `max-attribute-value-length` | (none) | `20` / `50` / `1000` |
| `monitoring.deduplication.enabled` | `MONITORING_DEDUPLICATION_ENABLED` | `true` |
| `monitoring.deduplication.retention` | `MONITORING_DEDUPLICATION_RETENTION` | `24h` |
| `monitoring.deduplication.max-tracked-events` | (none) | `10000` |
| `monitoring.delivery.log-results` | (none) | `true` |
| `server.port` | `SERVER_PORT` | `8080` |

Threshold example:

```yaml
monitoring:
  rules:
    thresholds:
      - attribute: latencyMs
        max: 1000
        severity: HIGH
      - attribute: retryCount
        max: 5
        event-types: [job.completed]
        severity: MEDIUM
```

Disabling AI never disables request validation or the VALIDATION rules.

`.env.example` lists the variables. **Spring Boot does not read `.env` files**, so export the variables or set them in your IDE.

## 14. API

All responses use `ResponseAPI` `{code, message, data}`. Errors use `ErrorResponse` `{timestamp, status, error, path, correlationId}`, where `status` is the application error code.

### POST /api/v1/events/monitor

```bash
curl -s -X POST http://localhost:8080/api/v1/events/monitor \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: trace-42' \
  -d '{
    "eventId": "evt-1001",
    "eventType": "request.completed",
    "source": "edge-gateway",
    "occurredAt": "2026-10-23T12:00:00Z",
    "severity": "LOW",
    "attributes": { "latencyMs": 5400, "region": "eu-west" },
    "payload": { "path": "/orders", "status": 200 }
  }'
```

| Field | Required | Rules |
|---|---|---|
| `eventId` | yes | ≤128 chars, `[A-Za-z0-9._:-]` |
| `eventType` | yes | ≤100 chars, same characters; lower-cased |
| `source` | yes | ≤100 chars, same characters |
| `occurredAt` | yes | ISO-8601 instant |
| `receivedAt` | no | ISO-8601 instant; defaults to server time |
| `correlationId` | no | 1–64 chars `[A-Za-z0-9._:-]` |
| `severity` | no | `INFO`, `LOW`, `MEDIUM`, `HIGH` or `CRITICAL` (case-insensitive) |
| `attributes` | no | flat map of string, number or boolean values |
| `payload` | no | any JSON, depth- and size-limited |

Example response, with a `latencyMs max 1000` threshold configured and a model available:

```json
{
  "code": 200,
  "message": "Event processed",
  "data": {
    "eventId": "evt-1001",
    "source": "edge-gateway",
    "eventType": "request.completed",
    "correlationId": "trace-42",
    "monitoringStatus": "COMPLETED",
    "duplicate": false,
    "ruleDecision": "REQUIRES_AI_ANALYSIS",
    "ruleFindings": [
      { "ruleId": "attribute-threshold", "effect": "FLAG", "severity": "HIGH",
        "message": "attribute 'latencyMs' value 5400 is above the configured maximum 1000" }
    ],
    "aiAnalysis": {
      "analysisStatus": "COMPLETED",
      "schemaVersion": "monitoring-assessment.v1",
      "assessment": "NEEDS_REVIEW",
      "confidence": "MEDIUM",
      "explanation": "Latency is far above the configured maximum; the event alone does not show why.",
      "possibleCauses": ["Upstream dependency slowness"],
      "missingEvidence": ["Recent latency baseline for this source"],
      "suggestedReview": true,
      "modelName": "qwen3.5:9b",
      "analysisDurationMs": 2140
    },
    "overallRiskLevel": "HIGH",
    "reviewRequired": true,
    "reviewReasons": [
      "rule attribute-threshold reported HIGH severity",
      "AI assessment NEEDS_REVIEW",
      "AI suggested human review"
    ],
    "createdAt": "2026-10-23T12:00:02.511Z",
    "processingDurationMs": 2156
  }
}
```

The same event with Ollama offline:

```json
"monitoringStatus": "INCONCLUSIVE",
"aiAnalysis": { "analysisStatus": "UNAVAILABLE", "statusDetail": "Ollama could not be reached",
                "modelName": "qwen3.5:9b", "analysisDurationMs": 3 },
"overallRiskLevel": "HIGH",
"reviewRequired": true,
"reviewReasons": ["rule attribute-threshold reported HIGH severity", "AI analysis did not complete: UNAVAILABLE"]
```

Error codes:

| HTTP | Code | Meaning |
|---|---|---|
| 400 | 1000 | DTO validation failed |
| 400 | 1001 | Malformed JSON, or bad enum or timestamp format |
| 404 / 405 / 415 | 1002 / 1003 / 1004 | Unknown path / method / content type |
| 413 | 1005 | Body exceeds `max-request-bytes` |
| 400 | 2000 | Normalization failed (non-scalar attribute, payload too deep, …) |
| 409 | 2001 | Same `source`+`eventId` with different content |
| 409 | 2002 | Same event currently being processed |
| 503 | 2003 | Deduplication capacity fully occupied by in-flight events |
| 500 | 9000 / 9999 | Unexpected failure |

### GET /api/v1/monitoring/status

Returns:

- rule, AI and deduplication settings;
- the storage policy fields from [section 3](#3-storage-and-idempotency-policy);
- `aiRuntime {runtime, state, modelName, detail}`, where `state` is `AVAILABLE`, `MODEL_NOT_INSTALLED`, `NOT_CONFIGURED`, `UNREACHABLE` or `DISABLED`.

When AI is enabled, this calls Ollama's `/api/tags` with `status-timeout`. When AI is disabled, it does not contact Ollama. The base URL is never exposed.

### GET /api/v1/monitoring/rules

Returns `[{id, description, category, effect, active}]`. Thresholds and other configuration values are not exposed.

### Actuator and metrics

`/actuator/health` reflects the application only. **Ollama is deliberately not a health contributor**, so an offline model does not mark the service DOWN.

| Metric | Type | Tags |
|---|---|---|
| `eventmonitor.events.received` | counter | (none) |
| `eventmonitor.events.rejected` | counter | `stage` = `validation` \| `rules` |
| `eventmonitor.events.duplicates` | counter | `outcome` = `replayed` \| `conflict` \| `in_progress` |
| `eventmonitor.rule.findings` | counter | `rule`, `effect` |
| `eventmonitor.ai.analyses.attempted` | counter | (none) |
| `eventmonitor.ai.analyses` | counter | `status` (`completed`, `timeout`, `unavailable`, `invalid_response`, `failed`) |
| `eventmonitor.processing` | timer | `status` |

## 15. Running locally, with or without Ollama

```bash
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
./mvnw clean package && java -jar target/local-ai-event-monitor.jar
```

The second command enables the `local` profile, which shows health details and DEBUG logging for `com.aicore.eventmonitor`.

**Without Ollama**, nothing extra is needed. Startup never contacts Ollama. Rules work, and events that need AI come back `INCONCLUSIVE` with rule findings intact.

To run deliberately in rules-only mode with no network attempts to Ollama:

```bash
MONITORING_AI_ENABLED=false ./mvnw spring-boot:run
```

Results that would have needed AI then show `analysisStatus: DISABLED` and `monitoringStatus: COMPLETED_WITHOUT_AI`.

## 16. Running tests without Ollama

```bash
./mvnw clean test
```

No Ollama installation, model download or internet access is needed (beyond Maven fetching dependencies):

- `StubOllamaServer` is a JDK `HttpServer` on `127.0.0.1` that imitates `/api/chat` and `/api/tags`, including slow responses, 404 "model not found", 500, malformed bodies and empty output.
- Connection-refused cases use a closed local port.
- `FakeAiEventAnalyzer` drives orchestration tests.

| Test class | Covers |
|---|---|
| `EventNormalizerTest` | normalization, fingerprint stability, attribute/payload validation |
| `InMemoryEventDeduplicatorTest` | replay, conflict, in-progress, source scope, release, expiry, completed-first eviction, capacity exhaustion, 16-thread concurrent registration |
| `RuleEngineTest` | every rule, decisions, triggers, disabling, failing rules |
| `AiOutputParserTest` | valid v1 output, malformed/trailing JSON, missing fields, unexpected enums, wrong types, forbidden extra fields (`eventId`, `riskLevel`, `ruleFindings`), limits, sanitization |
| `AnalysisPromptBuilderTest` | role constraints, payload omitted by default, redaction, identifier exclusion, delimiter escaping, truncation, closed schema |
| `OllamaClientTest` | request format, model not found, HTTP error, unreadable body, empty output, real timeout, connection refused, tags |
| `OllamaEventAnalyzerTest` | completed v1 assessment, configured model id and `think`/`num_ctx` options, invalid responses, offline, timeout, missing model, no request without model, no request on client creation, runtime status |
| `MonitoringResultAssemblerTest` | rule-only risk regardless of AI output, preserved findings on AI failure, UNKNOWN on missing AI, review policy |
| `EventMonitoringServiceImplTest` | orchestration, AI on/off, concurrent duplicate submissions, eventId vs correlationId, **no Ollama request when AI disabled** (real adapter + stub server) |
| `ConfigurationDefaultsTest` | default model `qwen3.5:9b` and timeouts via overridable placeholders, no cloud provider configured, no model hardcoded in Java, payload excluded by default |
| `MonitoringMetricsTest` | Micrometer counters and timer |
| `EventMonitoringApiIntegrationTest` | Spring context + stub: API responses and error semantics, status/storage fields, rules, health, metrics |
| `OllamaOfflineIntegrationTest` | Spring context with Ollama unreachable: healthy app, INCONCLUSIVE results, rules still work |
| `AiDisabledIntegrationTest` | Spring context with AI disabled: DISABLED status, no request reaches the stub |
| `StartupIsolationIntegrationTest` | Spring context startup sends **no** request to Ollama (no download); configured model id reaches `/api/chat` |

## 17. Extending: rules, analysis, delivery, storage

**A monitoring rule.** Implement `MonitoringRule` as a `@Component`; `RuleEngine` picks it up.

```java
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissingTenantRule implements MonitoringRule {

    public static final String ID = "missing-tenant";

    @Override public String getId() { return ID; }
    @Override public String getDescription() { return "Flags events without a tenant attribute"; }
    @Override public RuleCategory getCategory() { return RuleCategory.MONITORING; }
    @Override public RuleEffect getEffect() { return RuleEffect.FLAG; }

    @Override
    public List<RuleFinding> evaluate(MonitoringEvent event) {
        return event.getAttributes().containsKey("tenant")
                ? List.of()
                : List.of(RuleFinding.flag(ID, Severity.MEDIUM, "tenant attribute is missing"));
    }
}
```

Keep rules deterministic: inject `Clock`, and never call external systems. Rule ids must be unique and static, because they appear in metrics tags.

**An analysis capability.**

- To change the assessment contract, introduce `monitoring-assessment.v2`: a new field set and schema in `AiAssessmentOutput` / `AnalysisPromptBuilder` / `AiOutputParser`, mapped through `AiAnalysis` and `AiAnalysisResponse`.
- To use another *local* runtime, implement `core.analysis.AiEventAnalyzer`, reusing the prompt builder and parser, and make it the only (or `@Primary`) analyzer bean. `analyze` must return an `AiAnalysis` and should not throw; the orchestrator converts exceptions to `FAILED`.

**Result delivery or persistence.** Add a `MonitoringResultPublisher` bean (database, queue, webhook). All publishers are called after each new result; their exceptions are logged and do not affect the response.

**Durable or shared idempotency.** Implement `EventDeduplicator` against a shared store and make it the only deduplicator bean. The contract is:

| Method | Must |
|---|---|
| `register` | Return `NEW`, `DUPLICATE` (with the stored result), `CONFLICT`, `IN_PROGRESS` or `CAPACITY_EXHAUSTED`, atomically |
| `complete` | Store the result |
| `release` | Forget an in-progress entry |
| `getTrackedEventCount` / `getStoreType` | Report on the store for the status endpoint |

The pipeline does not change.

## 18. Integrating from another backend

**Over HTTP**, call `POST /api/v1/events/monitor`, then:

| Outcome | Treat as |
|---|---|
| `REJECTED` | Fix the event |
| `INCONCLUSIVE` | Retry later or route to review; **never** as "safe" |
| `reviewRequired` | Your trigger for human attention |
| `overallRiskLevel` | Rule-derived risk |

Treat AI fields as advisory context only.

**In-process:**

```java
@Service
@RequiredArgsConstructor
public class LoginAuditListener {

    private final EventMonitoringService eventMonitoringService;

    public void onLogin(String loginEventId, String traceId, int failedAttempts) {
        MonitoringEventRequest request = new MonitoringEventRequest();
        request.setEventId(loginEventId);
        request.setCorrelationId(traceId);
        request.setEventType("auth.login");
        request.setSource("identity-service");
        request.setOccurredAt(Instant.now());
        request.setAttributes(Map.of("failedAttempts", failedAttempts));

        MonitoringResultResponse result = eventMonitoringService.monitor(request);
        if (result.isReviewRequired()) {
            // route to your review queue; this module takes no action itself
        }
    }
}
```

`EventMonitoringService` is `@Validated`; invalid input throws `ConstraintViolationException`. The project is packaged as a runnable application; extracting a library or starter is future work.

## 19. Security and data handling

**Untrusted input**

- Request, payload, depth, attribute and length limits apply.
- Identifiers are restricted to `[A-Za-z0-9._:-]`.
- Nothing in an event is executed.

**Data minimization toward the model**

- The payload is excluded by default.
- `eventId`, `correlationId` and `receivedAt` are never sent.
- Sensitive-looking field names are redacted.
- Event data goes only to the Ollama URL you configure. If that URL points to another machine, the included data travels there.

**Prompt injection**

- Data is placed in escaped delimiters.
- The system prompt marks the data as untrusted and forbids actions.
- The output schema is closed, and extra fields are rejected.
- Risk is never taken from the model.

These measures reduce but cannot eliminate a model being influenced by event content.

**No actions, no tools**

- The adapter calls only `/api/chat` and `/api/tags` on the configured URL, and does not follow redirects.

**Logging**

- Logs contain ids, types, sources, statuses and error categories, never payloads or attributes.
- Ollama error details are logged at DEBUG only.

**No authentication**

- The API and Actuator endpoints are unauthenticated. Put the service behind a gateway or add Spring Security before exposing it beyond a trusted network.

**No credentials**

- Ollama needs no key, and none is stored.

## 20. Limitations of LLM-based interpretation

- **Advisory only.** A local LLM interprets the limited data it is given. It has no access to history, baselines or business context unless you put them into the event.
- **Not a detector.** It is not a trained anomaly detector, a fraud model or a security scanner.
- **No probabilities.** `assessment` and `confidence` are the model's own qualitative output. They are **not calibrated probabilities** and have not been evaluated for any domain.
- **Repeatability.** Results can change with the model, tag, quantization, Ollama version, prompt wording or `temperature`. `temperature: 0.0` improves repeatability but does not guarantee identical output.
- **Size trade-offs.** Smaller models are faster but tend to produce more `INVALID_RESPONSE` results and shallower interpretations.
- **Evaluate before relying on it.** Before AI output informs any operational decision, assess it on labelled, representative events from **your** domain: accuracy of assessments, false-review and missed-review rates, schema adherence, latency and resource use. Re-evaluate after changing models.

## 21. Current limitations

- **Storage:** in-memory, per-instance deduplication; no event or result history (see [section 3](#3-storage-and-idempotency-policy)).
- **Processing:** synchronous; the HTTP call waits up to `request-timeout` for the model.
- **Model:** one model at a time, with no per-event-type model selection and no model fallback.
- **Packaging:** a runnable application, not a library jar.
- **Verification performed for this delivery:**
  - Maven Central was unreachable from the build environment, so **`mvn clean test` and `mvn package` were not executed**, and the Maven Wrapper download was not exercised.
  - All main and test sources were compiled with `javac` on JDK 21 against the real Lombok, Jackson, Jakarta Validation, Servlet, JUnit 5, AssertJ and Hamcrest libraries. Spring and Micrometer were represented by signature-only stubs, so Spring-specific wiring is unverified.
  - **109 non-Spring tests were executed and all passed.** These include real HTTP calls against the local stub Ollama server (timeouts, refused connections, schema validation) and concurrency tests.
  - The five Spring integration test classes and `MonitoringMetricsTest` were compiled but **not executed**.
  - The `qwen3.5` tags were not retrieved from the Ollama library and **were not run locally**.

## 22. Change history

**This refinement**

- Made the storage policy explicit and reported it on the status endpoint.
- Deduplication capacity now evicts only completed entries, and returns 503 rather than dropping in-flight protection.
- Replaced the model-produced risk score and its fixed thresholds with:
  - the versioned `monitoring-assessment.v1` contract (assessment, confidence, explanation, possible causes, missing evidence, suggested review);
  - deterministic rule-only risk;
  - a configurable review policy.
- Added the `DISABLED` and `INVALID_RESPONSE` statuses.
- The payload is now excluded from prompts by default, and sensitive field names are redacted.
- The default model is now `qwen3.5:9b`, with configurable `think` and `context-length`.

**Previous refactor** from the generic AI Core Platform scaffold: retained the response envelope, error handling, correlation filter, clock config and Maven Wrapper; removed the multi-provider, capability and policy framework.

## 23. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `aiRuntime.state: UNREACHABLE` | Ollama not running or wrong `OLLAMA_BASE_URL`. Run `ollama serve`; check `curl http://localhost:11434/api/tags`. |
| `MODEL_NOT_INSTALLED`, or `UNAVAILABLE` with "not installed" | Run `ollama pull qwen3.5:9b`, or set `OLLAMA_MODEL` to a tag shown by `ollama list`. |
| `FAILED` on every call after upgrading or downgrading Ollama | Your Ollama version may not accept the `think` field. Set `OLLAMA_THINK=` (empty) to omit it. Check the Ollama server log. |
| Frequent `TIMEOUT` | Raise `OLLAMA_REQUEST_TIMEOUT`, try `qwen3.5:4b`, lower `ollama.max-output-tokens`, or reduce `context-length`. |
| Frequent `INVALID_RESPONSE` | The model is not following the schema. Use Ollama ≥ 0.5, keep `think: false`, or try a different model. Check `statusDetail`. |
| 409 with code 2001 | Same `source`+`eventId` re-sent with different content. Use a new `eventId` for a different event. |
| 409 with code 2002 | Same event is still being processed. Retry after it completes. |
| 503 with code 2003 | Too many in-flight events for `max-tracked-events`. Retry later, or raise the limit. |
| Every event `REJECTED` by `timestamp-validity` | Clock skew beyond `max-clock-skew`, or `occurredAt` older than `max-event-age`. |
| Startup fails on `ollama.base-url` | It must be an absolute http(s) URL. |
| `./mvnw` cannot download Maven | Network or proxy blocks Maven Central. Configure a mirror or proxy, or use a local Maven 3.9+. |
