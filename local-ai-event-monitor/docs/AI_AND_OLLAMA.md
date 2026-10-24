# AI Analysis and Ollama Integration

## Role and boundary

The AI is an **optional semantic layer** on top of deterministic rules. This is implemented as follows:

- It is called only when `RuleEngine` returns `REQUIRES_AI_ANALYSIS` **and** `monitoring.ai.enabled=true`. With the default trigger `ON_RULE_FLAG`, that means only events flagged by a MONITORING rule, or events whose type is in `always-analyze-event-types`.
- It receives the normalized event and the rule findings, and returns an advisory assessment.
- It **cannot** change rule findings, `overallRiskLevel`, event identity or source metadata. Its only influence on the result is `reviewRequired` / `reviewReasons`, via `monitoring.policy` (see [EVENT_LIFECYCLE.md](EVENT_LIFECYCLE.md#result-assembly)).
- It has no tools and cannot act. The adapter only reads model text.

## Components

| Class | Responsibility |
|---|---|
| `core.analysis.AiEventAnalyzer` | Interface: `getRuntimeName()`, `analyze(event, ruleFindings)`, `checkRuntimeStatus()` |
| `ollama.OllamaEventAnalyzer` | The only implementation. Builds the request, calls the client, parses the output, maps failures to statuses. |
| `ollama.OllamaClient` | JDK `HttpClient`, HTTP/1.1, no redirects. `chat(OllamaChatRequest)` → `POST {baseUrl}/api/chat`; `listModelNames()` → `GET {baseUrl}/api/tags`. |
| `analysis.AnalysisPromptBuilder` | System prompt, user prompt, data minimization and redaction, JSON schema |
| `analysis.AiOutputParser` | Strict validation of the model's JSON into `AiAssessmentOutput` |

## Request sent to Ollama

`POST /api/chat` with the following body (DTO `OllamaChatRequest`, `null` fields omitted):

```json
{
  "model": "<ollama.model>",
  "messages": [
    { "role": "system", "content": "<constraints and output instructions>" },
    { "role": "user", "content": "<rule findings and event data in delimiters>" }
  ],
  "stream": false,
  "format": { "type": "object", "properties": { "...": "..." }, "required": ["..."], "additionalProperties": false },
  "think": false,
  "options": { "temperature": 0.0, "num_predict": 512 }
}
```

- `think` is omitted when `ollama.think` is unset.
- `options.num_ctx` is added only when `ollama.context-length` is set.
- `format` (JSON-schema structured output) requires Ollama 0.5+. The `think` field requires an Ollama version that supports it. **Neither has been tried against a real Ollama.**

### Prompt data minimization

| Event data | Handling |
|---|---|
| `eventType`, `source`, `occurredAt`, `reportedSeverity`, `attributes` | Sent |
| `payload` | Omitted by default; the model only sees `"payloadOmitted": true`. Sent when `monitoring.ai.include-payload=true`. |
| `eventId`, `correlationId`, `receivedAt` | Never sent |
| Fields whose name contains a `redacted-field-names` token (case-insensitive) | Value replaced by `[REDACTED]`; applied recursively in the payload |

Additional safeguards:

- Event data is wrapped in `<event_data>` delimiters and rule findings in `<rule_findings>`, both as JSON. Every `<` is escaped as `\u003c`.
- Event data over `max-prompt-event-characters` is cut. A completed analysis then gets `statusDetail: "Event data was truncated before analysis"`.

## Output contract: `monitoring-assessment.v1`

The schema version is set by the application (`AiAssessmentOutput.SCHEMA_VERSION`); the model does not supply it.

| Field | Type | Validation in `AiOutputParser` |
|---|---|---|
| `assessment` | `CONSISTENT_WITH_EXPECTED_BEHAVIOR` \| `NEEDS_REVIEW` \| `LIKELY_ANOMALOUS` \| `INSUFFICIENT_EVIDENCE` | Exact enum name |
| `confidence` | `LOW` \| `MEDIUM` \| `HIGH` | Exact enum name. It is the model's self-report, **not a calibrated probability**. |
| `explanation` | string | Required, non-blank after sanitizing, truncated to `max-explanation-length` |
| `possibleCauses` | string[] | ≤ `max-list-items` entries; each sanitized and truncated to `max-item-length`; blank entries dropped |
| `missingEvidence` | string[] | Same as `possibleCauses` |
| `suggestedReview` | boolean | A real JSON boolean |

The parser also enforces the following. Any violation results in `INVALID_RESPONSE`:

- Exactly one JSON object, with no trailing tokens. A surrounding Markdown code fence is tolerated.
- All six fields are required.
- **Any other field is rejected**, for example `eventId`, `riskLevel` or `ruleFindings`.
- Control characters are replaced and whitespace collapsed.

## Analysis statuses

| `AnalysisStatus` | Produced when |
|---|---|
| `COMPLETED` | Valid v1 output |
| `SKIPPED` | Rules decided `REJECT` or `RULES_SUFFICIENT` (set by the orchestrator) |
| `DISABLED` | AI required but `monitoring.ai.enabled=false`. **No network call.** |
| `UNAVAILABLE` | Connection refused or connect timeout (`CONNECTION_FAILED`); HTTP 404 whose body `error` contains "not found" (`MODEL_NOT_FOUND`); blank `ollama.model` (no call made) |
| `TIMEOUT` | No response within `ollama.request-timeout` (`HttpTimeoutException`) |
| `INVALID_RESPONSE` | Blank content (`EMPTY_OUTPUT`), unreadable or message-less response body (`INVALID_RESPONSE`), or any parser violation |
| `FAILED` | Other non-2xx HTTP (`HTTP_ERROR`), interrupted request, or an exception or `null` returned from the analyzer |

For non-`COMPLETED` statuses:

- `statusDetail` holds a fixed application message. For parser violations it holds `"Model output was rejected: <parser reason>"`. Raw model text is never returned.
- All assessment fields are absent.

Failures are logged at WARN, with details at DEBUG only.

## Runtime status

`checkRuntimeStatus()` backs `GET /api/v1/monitoring/status` and returns one of these states:

| State | Meaning |
|---|---|
| `NOT_CONFIGURED` | Blank model |
| `AVAILABLE` | `/api/tags` lists the configured model. A model configured without a tag also matches `<model>:latest`. |
| `MODEL_NOT_INSTALLED` | Ollama is reachable but the model is not listed. `detail` suggests `ollama pull <model>`. |
| `UNREACHABLE` | Any client error |
| `DISABLED` | Set by `MonitoringStatusServiceImpl` when AI is disabled; Ollama is not contacted |

## Behaviour summary

| Situation | Result |
|---|---|
| AI disabled | `DISABLED`; `COMPLETED_WITHOUT_AI`; risk from rules; no Ollama traffic |
| Ollama offline | `UNAVAILABLE` within `connect-timeout`; `INCONCLUSIVE`; rule findings kept; risk `UNKNOWN` if no flags; event retryable |
| Model missing | `UNAVAILABLE` ("not installed"); same handling as offline |
| Slow model | `TIMEOUT` after `request-timeout`; `INCONCLUSIVE` |
| Bad output | `INVALID_RESPONSE`; `INCONCLUSIVE` |
| Startup | No Ollama request of any kind; no model download (tested) |

With `monitoring.ai.on-failure=RULES_ONLY`, failures give `COMPLETED_WITHOUT_AI` instead of `INCONCLUSIVE`; risk and review handling are unchanged.

## Model configuration

The default comes from `application.yml`: `ollama.model: ${OLLAMA_MODEL:qwen3.5:9b}`. No model name is hardcoded in Java; the `model` field of `OllamaProperties` defaults to `null`.

| Fact | Status |
|---|---|
| `qwen3.5:9b` is the configured default | Implemented |
| `qwen3.5:4b` is the documented lower-resource alternative | Documentation only |
| Either tag exists in the Ollama library | **Unverified.** The library was unreachable from the authoring environment. |
| Either model run against this code | **Never tested** |
| Approximate sizes (9b ≈ 6–7 GB, 4b ≈ 3–3.5 GB at about 4-bit quantization) | **Estimates from parameter count**, not published figures |

No benchmark exists, so no claim is made that these models are a good choice. Model selection needs evaluation on representative events, measuring:

- `INVALID_RESPONSE` rate;
- latency against `request-timeout`;
- memory and CPU/GPU use;
- review quality judged by domain experts.

### Running a local model (no cloud inference)

```bash
ollama serve
ollama pull qwen3.5:9b
export OLLAMA_MODEL=qwen3.5:9b
curl -s http://localhost:8080/api/v1/monitoring/status
```

`ollama serve` listens on `http://localhost:11434`. Use any installed tag for `OLLAMA_MODEL`; `ollama list` shows them. The status call should report `aiRuntime.state` = `AVAILABLE`.

The only network endpoint the application uses for AI is `ollama.base-url`. There is no cloud provider and no fallback. If `base-url` points to another host, event data included in prompts is sent there.

## Limitations

- LLM interpretation is advisory. It has no history or baselines, and its output varies with model, tag, quantization, Ollama version and prompt.
- `temperature: 0.0` improves repeatability but does not guarantee identical output.
- Prompt-injection mitigations reduce but cannot eliminate influence from event content.
