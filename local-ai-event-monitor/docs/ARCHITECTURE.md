# Architecture

Everything in the **Implemented architecture** sections is verified from the source in `src/main/java/com/aicore/eventmonitor`. Anything not yet built is confined to [Future architecture](#future-architecture-not-implemented).

## Shape

A **single Spring Boot application** (modular monolith). Package boundaries are by convention, not by Maven modules.

The repository also contains `test-simulator/`, a **separate** Maven project that tests a running Core over HTTP. It is not part of the Core's architecture or build; see [TEST_SIMULATOR.md](TEST_SIMULATOR.md).

Layer packages follow the reference code style:

- `controller`, `service` / `service.impl`, `dto.request.*` / `dto.response.*`, `exception`, `configuration`, `constants`;
- Lombok DTOs;
- the `ResponseAPI<T>` envelope;
- `ErrorCode` + `ApplicationException` + `GlobalExceptionHandler`.

## Implemented architecture

### Packages and responsibilities

| Package | Key types | Responsibility |
|---|---|---|
| `controller` | `EventMonitoringController`, `MonitoringStatusController` | Thin REST layer that wraps service results in `ResponseAPI` |
| `service`, `service.impl` | `EventMonitoringService` / `EventMonitoringServiceImpl`, `MonitoringStatusService` / `MonitoringStatusServiceImpl` | `EventMonitoringServiceImpl` is the **only orchestrator** of the pipeline |
| `dto.request.event`, `dto.response.*` | `MonitoringEventRequest`, `MonitoringResultResponse`, `RuleFindingResponse`, `AiAnalysisResponse`, `MonitoringStatusResponse`, `AiRuntimeStatusResponse`, `RuleResponse`, `ResponseAPI`, `ErrorResponse` | API contract types |
| `core.event` | `MonitoringEvent`, `Severity` | Normalized, immutable event |
| `core.rule` | `MonitoringRule` (interface), `RuleFinding`, `RuleEffect`, `RuleCategory`, `RuleDecision`, `RuleEvaluation` | Rule contracts |
| `core.analysis` | `AiEventAnalyzer` (interface), `AiAnalysis`, `AiAssessment`, `AiConfidence`, `AnalysisStatus`, `RiskLevel`, `AiRuntimeStatus`, `AiRuntimeState` | AI contracts, independent of any runtime |
| `core.result` | `MonitoringResult`, `MonitoringStatus`, `MonitoringResultPublisher` (interface) | Result model and delivery extension point |
| `normalization` | `EventNormalizer` | Trims and normalizes, validates attributes and payload depth, computes size and fingerprint |
| `deduplication` | `EventDeduplicator` (interface), `InMemoryEventDeduplicator`, `DeduplicationOutcome`, `DeduplicationStatus` | Idempotency within one instance |
| `rule` | `RuleEngine`, `RequiredFieldsRule`, `TimestampRule`, `EventTypeRule`, `PayloadLimitsRule`, `AttributeThresholdRule`, `ReportedSeverityRule` | Deterministic evaluation and the AI decision |
| `analysis` | `AnalysisPromptBuilder`, `AiOutputParser`, `AiAssessmentOutput`, `AnalysisPrompt`, `InvalidAiOutputException`, `RiskLevelNormalizer` | Prompt construction, output validation, severity→risk mapping. No Ollama types. |
| `ollama`, `ollama.dto` | `OllamaClient`, `OllamaEventAnalyzer`, `OllamaException`, `OllamaErrorType`, `OllamaChatRequest`, `OllamaChatResponse`, `OllamaChatMessage`, `OllamaTagsResponse`, `OllamaModel` | **The only place with Ollama-specific formats** |
| `result` | `MonitoringResultAssembler`, `LoggingMonitoringResultPublisher` | Deterministic status, risk and review; log-based delivery |
| `mapper` | `MonitoringResultMapper` | Domain → response DTO |
| `observability` | `MonitoringMetrics` | Micrometer counters and timer |
| `filter` | `CorrelationIdFilter`, `RequestSizeLimitFilter`, `RequestBodyTooLargeException` | Correlation ID; request body limit for `/api/v1/events/**` |
| `exception` | `ErrorCode`, `ApplicationException`, `GlobalExceptionHandler` | Error codes and the error response |
| `configuration` | `MonitoringProperties` (`monitoring.*`), `OllamaProperties` (`ollama.*`), `ClockConfig` | Validated configuration (see [CONFIGURATION.md](CONFIGURATION.md)) |
| `constants` | `PlatformConstants` | Header name, MDC keys, identifier regexes, `EVENT_API_PATH` |

### Dependency direction

```mermaid
flowchart LR
  controller --> service
  service --> normalization & deduplication & rule & result & mapper & observability
  service --> coreAnalysis["core.analysis (AiEventAnalyzer)"]
  ollama -- implements --> coreAnalysis
  ollama --> analysis
  rule --> coreRule["core.rule"]
  result --> analysis
  analysis --> coreEvent["core.event"]
  normalization --> coreEvent
```

Key rules:

- `core.*` depends on nothing Ollama- or HTTP-specific.
- The orchestrator depends on the `AiEventAnalyzer` **interface**. `OllamaEventAnalyzer` is the only implementation bean.
- Rules never call the AI. The AI never sees `eventId` or `correlationId`.

### Request flow

```mermaid
sequenceDiagram
  participant C as Client
  participant F as Filters
  participant Ctl as EventMonitoringController
  participant S as EventMonitoringServiceImpl
  participant D as InMemoryEventDeduplicator
  participant R as RuleEngine
  participant A as OllamaEventAnalyzer
  participant O as Ollama (local)
  C->>F: POST /api/v1/events/monitor
  F->>Ctl: correlation id set, size limit checked
  Ctl->>S: monitor(@Valid request)
  S->>S: EventNormalizer.normalize
  S->>D: register(event)
  alt duplicate / conflict / in progress / capacity
    D-->>S: DUPLICATE (stored result) or error status
  else NEW
    S->>R: evaluate(event)
    opt decision REQUIRES_AI_ANALYSIS and AI enabled
      S->>A: analyze(event, findings)
      A->>O: POST /api/chat (stream=false, format=JSON schema)
      O-->>A: message.content
      A-->>S: AiAnalysis (validated or failure status)
    end
    S->>S: MonitoringResultAssembler.assemble
    S->>D: complete or release
    S->>S: metrics, publishers
  end
  S-->>Ctl: MonitoringResultResponse
  Ctl-->>C: ResponseAPI
```

[EVENT_LIFECYCLE.md](EVENT_LIFECYCLE.md) is the detailed and canonical description of this flow.

### Extension points (implemented interfaces)

| Interface | Default implementation | Discovered how |
|---|---|---|
| `MonitoringRule` | Six rules in `rule` | All beans, injected as `List<MonitoringRule>` into `RuleEngine`. IDs must be unique, checked in the constructor. |
| `AiEventAnalyzer` | `OllamaEventAnalyzer` | Single bean. A second bean would make injection ambiguous unless marked `@Primary`. |
| `EventDeduplicator` | `InMemoryEventDeduplicator` | Single bean |
| `MonitoringResultPublisher` | `LoggingMonitoringResultPublisher` | All beans, as `List<MonitoringResultPublisher>`. Called only for newly produced results, not for replays. |

### Architectural constraints

These are enforced by code and/or tests:

- **Risk comes only from rule findings.** `MonitoringResultAssembler` never reads AI output for `overallRiskLevel`; the AI affects only `reviewRequired` / `reviewReasons`. This is tested.
- **AI failure never erases rule findings,** and never yields `LOW` risk when AI was required and missing.
- **Startup makes no network calls.** `OllamaClient` creates its `HttpClient` at construction but sends no request until analysis or a status check. This is tested.
- **The Ollama adapter calls only `/api/chat` and `/api/tags`** on the configured base URL, without following redirects.
- **Ollama is not a health contributor.** `/actuator/health` does not depend on it.
- **No Java comments or Javadocs** in source files (project convention).

## Future architecture (not implemented)

These are ideas only; none exist in the code. See [ROADMAP.md](ROADMAP.md) and [DECISIONS.md](DECISIONS.md).

- A persistent or shared `EventDeduplicator` (for example, backed by a database or Redis) for multi-instance deployments.
- `MonitoringResultPublisher` implementations that store or forward results (database, queue, webhook).
- Asynchronous processing, where the request returns immediately and the result is delivered later.
- Extraction into a reusable library or Spring Boot starter.
