# Configuration

Configuration is bound from `src/main/resources/application.yml` into two validated `@ConfigurationProperties` classes:

- `configuration.MonitoringProperties` (`monitoring.*`);
- `configuration.OllamaProperties` (`ollama.*`).

Environment variables only work where `application.yml` declares a `${VAR:default}` placeholder; those are listed in the **Env** column. Spring's relaxed binding also accepts names such as `MONITORING_AI_MAXLISTITEMS`, but these are untested.

No secrets are required: Ollama has no API key, and there are no credentials of any kind. Do not add real secrets to this file, `application.yml` or `.env.example`.

## ollama.*

| Property | Env | Default | Validation / notes |
|---|---|---|---|
| `ollama.base-url` | `OLLAMA_BASE_URL` | `http://localhost:11434` | Must be absolute http(s) with a host; otherwise `OllamaClient` throws at startup |
| `ollama.model` | `OLLAMA_MODEL` | `qwen3.5:9b` (yml) / `null` (Java) | Blank means AI calls return `UNAVAILABLE` and status reports `NOT_CONFIGURED` |
| `ollama.connect-timeout` | `OLLAMA_CONNECT_TIMEOUT` | `2s` | `HttpClient` connect timeout |
| `ollama.request-timeout` | `OLLAMA_REQUEST_TIMEOUT` | `60s` | Per-request timeout for `/api/chat` → `TIMEOUT` |
| `ollama.status-timeout` | (none) | `3s` | Timeout for `/api/tags` (status endpoint) |
| `ollama.temperature` | (none) | `0.0` | 0.0–2.0 |
| `ollama.max-output-tokens` | (none) | `512` | ≥16; sent as `num_predict` |
| `ollama.think` | `OLLAMA_THINK` | `false` | `Boolean`. When `null` the field is omitted. **Unverified:** whether an empty `OLLAMA_THINK=` binds to `null`. |
| `ollama.context-length` | (none) | unset | ≥512; sent as `num_ctx` when set |

## monitoring.ai.*

| Property | Env | Default |
|---|---|---|
| `enabled` | `MONITORING_AI_ENABLED` | `true` |
| `trigger` (`ON_RULE_FLAG` \| `ALWAYS`) | `MONITORING_AI_TRIGGER` | `ON_RULE_FLAG` |
| `always-analyze-event-types` | (none) | `[]` |
| `on-failure` (`INCONCLUSIVE` \| `RULES_ONLY`) | `MONITORING_AI_ON_FAILURE` | `INCONCLUSIVE` |
| `include-payload` | `MONITORING_AI_INCLUDE_PAYLOAD` | `false` |
| `redacted-field-names` | (none) | `[password, secret, token, authorization, apikey, credential]` |
| `max-prompt-event-characters` | (none) | `8000` (≥500) |
| `max-list-items` | (none) | `5` (1–20) |
| `max-item-length` | (none) | `300` (≥20) |
| `max-explanation-length` | (none) | `1000` (≥50) |

## monitoring.policy.*

| Property | Default |
|---|---|
| `review-rule-severity` | `HIGH` |
| `review-ai-assessments` | `[NEEDS_REVIEW, LIKELY_ANOMALOUS, INSUFFICIENT_EVIDENCE]` |
| `review-when-ai-suggests` | `true` |

These are illustrative defaults, not validated thresholds.

## monitoring.rules.*

| Property | Env | Default |
|---|---|---|
| `enabled` (MONITORING rules only) | `MONITORING_RULES_ENABLED` | `true` |
| `disabled-rules` | (none) | `[]` (MONITORING rule IDs; VALIDATION rules ignore it) |
| `allowed-event-types` | (none) | `[]` (all) |
| `required-attributes` | (none) | `[]` |
| `max-clock-skew` | (none) | `5m` |
| `max-event-age` | (none) | `7d` |
| `flag-reported-severity-at` | (none) | `HIGH` |
| `thresholds[]` | (none) | `[]`; each item has `attribute` (required), `min`, `max` (at least one), `event-types`, `severity` (default `HIGH`) |

## monitoring.limits.*

| Property | Env | Default |
|---|---|---|
| `max-request-bytes` | `MONITORING_MAX_REQUEST_BYTES` | `262144` (≥1024) |
| `max-payload-bytes` | `MONITORING_MAX_PAYLOAD_BYTES` | `65536` |
| `max-payload-depth` | (none) | `20` (1–100) |
| `max-attributes` | (none) | `50` (0–200) |
| `max-attribute-value-length` | (none) | `1000` (1–10000) |

## monitoring.deduplication.* and monitoring.delivery.*

| Property | Env | Default |
|---|---|---|
| `deduplication.enabled` | `MONITORING_DEDUPLICATION_ENABLED` | `true` |
| `deduplication.retention` | `MONITORING_DEDUPLICATION_RETENTION` | `24h` |
| `deduplication.max-tracked-events` | (none) | `10000` |
| `delivery.log-results` | (none) | `true` |

## Spring settings in application.yml

| Setting | Value |
|---|---|
| `server.port` | `${SERVER_PORT:8080}` |
| `spring.jackson.mapper.accept-case-insensitive-enums` | `true` |
| `server.error.include-stacktrace` / `include-message` | `never` |
| `management.endpoints.web.exposure.include` | `health,info,metrics` |
| `management.endpoint.health.show-details` | `never` |
| `management.endpoint.health.show-components` | `always` |
| `logging.pattern.level` | Includes MDC `correlationId` and `eventId` |

Profiles:

- `local` (`application-local.yml`): health details always shown; DEBUG logging for `com.aicore.eventmonitor`.
- `test` (`src/test/resources/application-test.yml`): a `latencyMs max 1000` threshold, `max-request-bytes: 16384`, `log-results: false`, model `test-model`, 500 ms timeouts.

## Startup validation

Implemented through `@Validated` and constraint annotations:

- range constraints listed above;
- thresholds need `min` or `max` (`@AssertTrue isBoundDefined`);
- base URL check in `OllamaClient`;
- unique rule IDs (`RuleEngine` constructor).

**Unverified in a running container:** Hibernate Validator evaluating `@AssertTrue` on the nested list items.

## Safe example

```yaml
ollama:
  base-url: http://localhost:11434
  model: qwen3.5:4b
  request-timeout: 120s
monitoring:
  ai:
    enabled: true
    trigger: ON_RULE_FLAG
    include-payload: false
  rules:
    allowed-event-types: [request.completed, job.completed]
    thresholds:
      - attribute: latencyMs
        max: 1000
        severity: HIGH
  deduplication:
    retention: 12h
```

Environment variables: see `.env.example`. **Spring Boot does not load `.env` files automatically**; export the variables or configure them in your IDE or runtime.
