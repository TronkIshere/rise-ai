# API Contract

All behaviour here is verified from the controllers, DTOs, `GlobalExceptionHandler`, `ErrorCode` and the filters, and exercised by the MockMvc integration tests and a live end-to-end run with the Rise AI backend on 2026-10-25 (see [TESTING.md](TESTING.md)).

## Conventions

- Success responses use `ResponseAPI<T>`: `{ "code": 200, "message": "...", "data": { ... } }`. `null` fields are omitted (`@JsonInclude(NON_NULL)`).
- Error responses use `ErrorResponse`: `{ "timestamp", "status", "error", "path", "correlationId" }`. **`status` is the application error code** (for example `2001`), not the HTTP status.
- `X-Correlation-Id` request header: accepted if it matches `^[A-Za-z0-9._:-]{1,64}$`; otherwise a UUID is generated. It is always echoed in the response header.
- Enum values in requests are case-insensitive (`spring.jackson.mapper.accept-case-insensitive-enums: true`).
- **Service key.** When `monitoring.security.service-key` is set, every `/api/v1/**` and `/actuator/metrics/**` request must send `Authorization: Bearer <key>` (constant-time comparison in `ServiceKeyAuthenticationFilter`); otherwise HTTP 401 with application code `1006`. `/actuator/health` and `/actuator/info` stay open. With no key configured (default profile, simulator), there is no authentication. The `riseai` profile refuses to start without a key (`monitoring.security.require-service-key: true`).

## Endpoints

| Method | Path | Controller method | Purpose |
|---|---|---|---|
| POST | `/api/v1/events/monitor` | `EventMonitoringController.monitor` | Monitor one event |
| GET | `/api/v1/monitoring/status` | `MonitoringStatusController.getStatus` | Settings, storage policy, AI runtime state |
| GET | `/api/v1/monitoring/rules` | `MonitoringStatusController.getRules` | Rule list |
| GET | `/actuator/health`, `/actuator/info`, `/actuator/metrics/**` | Spring Boot Actuator | Health and metrics |

## POST /api/v1/events/monitor

### Request: `MonitoringEventRequest`

| Field | Type | Required | Validation |
|---|---|---|---|
| `eventId` | string | yes | `@NotBlank`, ≤128, `^[A-Za-z0-9._:-]+$` |
| `eventType` | string | yes | `@NotBlank`, ≤100, same pattern; lower-cased by the normalizer |
| `source` | string | yes | `@NotBlank`, ≤100, same pattern |
| `occurredAt` | ISO-8601 instant | yes | `@NotNull` |
| `receivedAt` | ISO-8601 instant | no | Defaults to server time |
| `correlationId` | string | no | `^[A-Za-z0-9._:-]{1,64}$` |
| `severity` | enum `Severity` | no | `INFO`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `attributes` | object | no | ≤200 keys at DTO level (the default rule limit is 50); keys match the identifier pattern, ≤64 chars; values must be string, number or boolean (enforced by `EventNormalizer`) |
| `payload` | any JSON | no | Nesting ≤ `max-payload-depth`; size checked by the `payload-limits` rule |

The whole body is limited to `monitoring.limits.max-request-bytes` by `RequestSizeLimitFilter`.

```json
{
  "eventId": "evt-1001",
  "eventType": "request.completed",
  "source": "edge-gateway",
  "occurredAt": "2026-10-23T12:00:00Z",
  "correlationId": "trace-42",
  "severity": "LOW",
  "attributes": { "latencyMs": 5400, "region": "eu-west" },
  "payload": { "path": "/orders", "status": 200 }
}
```

### Response: `ResponseAPI<MonitoringResultResponse>`

HTTP 200 is returned for **every monitoring outcome**, including `REJECTED`. `message` is `"Event processed"`, or `"Duplicate event, returning the original result"` when `duplicate` is true.

| Field | Type | Notes |
|---|---|---|
| `eventId`, `source`, `eventType`, `correlationId` | string | From the normalized event. A replayed duplicate returns the **original** values, including the original `correlationId`. |
| `monitoringStatus` | `COMPLETED` \| `COMPLETED_WITHOUT_AI` \| `INCONCLUSIVE` \| `REJECTED` | See [EVENT_LIFECYCLE.md](EVENT_LIFECYCLE.md#result-assembly) |
| `duplicate` | boolean | `true` when a stored result is replayed |
| `ruleDecision` | `REJECT` \| `REQUIRES_AI_ANALYSIS` \| `RULES_SUFFICIENT` | |
| `ruleFindings[]` | `{ruleId, effect (REJECT\|FLAG), severity, message}` | |
| `aiAnalysis` | `AiAnalysisResponse` | Always present; see below |
| `overallRiskLevel` | `UNKNOWN` \| `LOW` \| `MEDIUM` \| `HIGH` \| `CRITICAL` | Rule-derived only |
| `reviewRequired`, `reviewReasons[]` | boolean, string[] | Policy-driven |
| `createdAt` | ISO-8601 instant | Original value on replay |
| `processingDurationMs` | long | Original value on replay |

`AiAnalysisResponse` fields: `analysisStatus`, `statusDetail`, `schemaVersion`, `assessment`, `confidence`, `explanation`, `possibleCauses`, `missingEvidence`, `suggestedReview`, `modelName`, `analysisDurationMs`. The assessment fields are present only when `analysisStatus` is `COMPLETED`. Statuses and their meaning are listed in [AI_AND_OLLAMA.md](AI_AND_OLLAMA.md#analysis-statuses).

This example assumes a threshold `latencyMs max 1000` is configured and the model answered:

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
    "reviewReasons": ["rule attribute-threshold reported HIGH severity", "AI assessment NEEDS_REVIEW", "AI suggested human review"],
    "createdAt": "2026-10-23T12:00:02.511Z",
    "processingDurationMs": 2156
  }
}
```

The same event with Ollama unreachable (only the `data` fields that differ are shown):

```json
"monitoringStatus": "INCONCLUSIVE",
"aiAnalysis": { "analysisStatus": "UNAVAILABLE", "statusDetail": "Ollama could not be reached", "modelName": "qwen3.5:9b", "analysisDurationMs": 3 },
"overallRiskLevel": "HIGH",
"reviewRequired": true,
"reviewReasons": ["rule attribute-threshold reported HIGH severity", "AI analysis did not complete: UNAVAILABLE"]
```

`analysisDurationMs` and `processingDurationMs` values above are illustrative; they are not measurements.

## GET /api/v1/monitoring/status

`ResponseAPI<MonitoringStatusResponse>`, message `"Monitoring status retrieved"`.

| Field | Source |
|---|---|
| `ruleEngineEnabled` | `monitoring.rules.enabled` |
| `aiAnalysisEnabled`, `aiTrigger`, `aiFailureMode` | `monitoring.ai.enabled`, `.trigger`, `.on-failure` |
| `eventPersistence` | Constant `"NONE"`: raw events are never stored |
| `riseProjection` | `"MINIMIZED_PROJECTION"` when `monitoring.rise.enabled` is true, otherwise `"DISABLED"`. The projection stores only identifiers, enum values, amounts, timestamps and evidence file hashes of Rise events (see [ARCHITECTURE.md](ARCHITECTURE.md#rise-ai-charity-monitoring)) |
| `deduplicationEnabled`, `deduplicationStore`, `deduplicationScope`, `deduplicationRetention`, `maxTrackedEvents`, `trackedEventCount` | Store `"JDBC"` (default) or `"IN_MEMORY"`; scope `"source+eventId, shared by every instance using the monitoring database, survives restarts"` for JDBC; retention as an ISO-8601 duration, for example `"PT24H"` |
| `aiRuntime` | `{runtime: "ollama", state, modelName, detail}`. `state` is one of `AVAILABLE`, `MODEL_NOT_INSTALLED`, `NOT_CONFIGURED`, `UNREACHABLE`, `DISABLED`. |

When AI is enabled, every call to this endpoint makes an **uncached** `GET /api/tags` request to Ollama, bounded by `ollama.status-timeout`. When AI is disabled, Ollama is not contacted. The Ollama base URL is never included.

## GET /api/v1/monitoring/rules

`ResponseAPI<List<RuleResponse>>`. Each item is `{id, description, category (VALIDATION|MONITORING), effect (REJECT|FLAG), active}`. Threshold values are not exposed. Rule details are in [EVENT_LIFECYCLE.md](EVENT_LIFECYCLE.md#deterministic-rules).

## Errors

| HTTP | `status` | `ErrorCode` | Raised by |
|---|---|---|---|
| 400 | 1000 | `INVALID_DATA` | DTO validation; `error` holds the first field message, for example `"eventType must not be blank"`. Also `ConstraintViolationException` from in-process calls, and other Spring 4xx errors that have no dedicated handler. |
| 400 | 1001 | `MALFORMED_REQUEST` | Unreadable JSON, or bad enum or timestamp format |
| 404 | 1002 | `RESOURCE_NOT_FOUND` | Unknown path |
| 405 | 1003 | `METHOD_NOT_ALLOWED` | Wrong method |
| 415 | 1004 | `UNSUPPORTED_MEDIA_TYPE` | Wrong content type |
| 413 | 1005 | `REQUEST_TOO_LARGE` | `Content-Length` over the limit (written by the filter), or a chunked body exceeding it (via the exception handler) |
| 401 | 1006 | `UNAUTHENTICATED` | Missing or wrong service key (written by `ServiceKeyAuthenticationFilter`) |
| 400 | 2000 | `INVALID_EVENT` | Normalizer: non-scalar or overlong attribute, null value, duplicate trimmed key, payload too deep. Rise reader: missing envelope, unsupported `schemaVersion`, envelope `eventId`/`eventType` not matching the request, malformed `occurredAt` or `references` |
| 409 | 2001 | `EVENT_ID_CONFLICT` | Same `source`+`eventId` with a different content fingerprint |
| 409 | 2002 | `EVENT_IN_PROGRESS` | Same event currently being processed |
| 503 | 2003 | `DEDUPLICATION_CAPACITY_EXHAUSTED` | Tracker full of in-progress entries |
| 500 | 9000 | `MONITORING_FAILED` | Unexpected `RuntimeException` during rule evaluation, AI orchestration or result assembly; the deduplication entry is released |
| 500 | 9999 | `INTERNAL_ERROR` | Any other unhandled exception |

```json
{ "timestamp": "2026-10-23T12:00:00.000+00:00", "status": 2001,
  "error": "Event ID was already received with different content: evt-1001",
  "path": "/api/v1/events/monitor", "correlationId": "trace-42" }
```

The exact `timestamp` format follows Spring Boot's default Jackson date serialization. It is unverified.

## Rise AI events and `findings[]`

Implemented 2026-10-25. The canonical cross-module contract (envelope, catalogue, finding format, delivery semantics) is `rise-ai-backend/docs/event-contracts.md`; this section describes only what the monitor accepts and returns.

**Request.** An event whose `source` equals `monitoring.rise.source` (default `rise-ai-backend`) must carry the full Rise envelope (schema version 1) as `payload`. The backend relay builds the request as follows: `eventId`, `eventType`, `occurredAt` and `correlationId` copied from the envelope; `source` = envelope `producer`; `attributes` = `schemaVersion`, `aggregateType`, `actorRole` plus scalar payload fields whose names do not end in `Id`, `Ids`, `Code`, `Hash` or `By` (so identifiers and file hashes never reach the AI prompt); `payload` = the stored envelope, byte-stable across retries.

**Response.** `MonitoringResultResponse` has an additive field `findings` (always present, possibly empty; empty for `REJECTED` results). Each item (`finding.v1`):

| Field | Notes |
|---|---|
| `findingId` | 64 lower-case hex; SHA-256 of `ruleId|subjectType|subjectId|discriminator`. Deterministic, so redelivery yields the same ID |
| `contractVersion` | `"finding.v1"` |
| `detector` | `{type: "RULE", id, version}` |
| `category` | `EVIDENCE_INTEGRITY`, `DISBURSEMENT_PATTERN`, `LEDGER_INTEGRITY`, `ADMINISTRATIVE_OVERRIDE`, `DISPUTE_PATTERN`, `DATA_QUALITY`, `MILESTONE_PROGRESS` |
| `severity` | `INFO`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` (fixed per rule; no numeric score) |
| `subject` | `{type, id}` |
| `references` | `campaignId`, `milestoneId`, `disbursementRequestId`, `donationId`, `evidenceId` when known |
| `sourceEventIds` | The event that produced this occurrence |
| `facts` | `[{name, value, sourceEventId}]`: the exact values that made the rule fire |
| `explanation` | Fixed template; never AI-generated; never claims fraud or intent |
| `recommendedAction` | `REVIEW_EVIDENCE`, `REQUEST_CLARIFICATION`, `CHECK_RECONCILIATION`, `REVIEW_ADMIN_ACTION`, `NONE_INFORMATIONAL` |
| `observedAt` | The source event's `occurredAt` |

Every finding is also added to `ruleFindings` as a `FLAG` with the same severity, so `overallRiskLevel`, `reviewRequired` and `reviewReasons` follow the existing rules unchanged.

Other candidate API ideas are listed in [ROADMAP.md](ROADMAP.md).
