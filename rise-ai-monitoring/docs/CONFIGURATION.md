# Configuration

Configuration is bound from `src/main/resources/application.yml` into two validated `@ConfigurationProperties` classes:

- `configuration.MonitoringProperties` (`monitoring.*`);
- `configuration.OllamaProperties` (`ollama.*`).

Environment variables only work where `application.yml` declares a `${VAR:default}` placeholder; those are listed in the **Env** column. Spring's relaxed binding also accepts names such as `MONITORING_AI_MAXLISTITEMS`, but these are untested.

One secret exists: the service key `MONITORING_SERVICE_KEY`, shared with the backend's `RISE_MONITORING_SERVICE_KEY`. It has no default and must only be supplied through the environment. Ollama has no API key. Do not add real secrets to this file, `application.yml` or `.env.example`.

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

## monitoring.security.*

| Property | Env | Default | Notes |
|---|---|---|---|
| `service-key` | `MONITORING_SERVICE_KEY` | empty | When set, `/api/v1/**` and `/actuator/metrics/**` require `Authorization: Bearer <key>` |
| `rise.ai-notes-enabled` | `MONITORING_RISE_AI_NOTES_ENABLED` | `false` | AI notes on Rise findings; needs `monitoring.ai.enabled=true`. Advisory only; not evaluated against the gate |
| `rise.ai-note-budget` | `MONITORING_RISE_AI_NOTE_BUDGET` | `20s` | Per event: no new AI-note call starts after this time, and each call's timeout is capped at the remaining budget (an in-flight call is cancelled at the budget, or at `OLLAMA_REQUEST_TIMEOUT` if that is shorter); a safety default, not validated |
| `previous-service-key` | `MONITORING_PREVIOUS_SERVICE_KEY` | empty | Optional second accepted key for rotation without downtime; clear it after the backend uses the new key |
| `require-service-key` | `MONITORING_REQUIRE_SERVICE_KEY` | `false` (`true` in the `riseai` profile) | Startup fails with "monitoring.security.service-key must be set…" when true and the key is empty (verified 2026-10-25) |

## monitoring.rise.*

| Property | Env | Default | Used by |
|---|---|---|---|
| `enabled` | `MONITORING_RISE_ENABLED` | `true` | Envelope reader and finding engine |
| `source` | (none) | `rise-ai-backend` | Which `source` values are treated as Rise events |
| `supported-schema-versions` | (none) | `[1]` | Envelope validation |
| `minimal-evidence-approval-amount` | (none) | `50000000` | DB-01 amount threshold (VND, placeholder pending product decision D12) |
| `minimal-evidence-count` | (none) | `1` | DB-01: flag when accepted evidence is at most this |
| `resubmission-window` | (none) | `24h` | DB-02 |
| `max-requests-per-milestone` | (none) | `3` | DB-03 |
| `adjustment-review-amount` | (none) | `10000000` | AO-01: MEDIUM at or above, INFO below |
| `projection-retention` | (none) | `400d` | Daily purge of projection rows (`monitoring.rise.purge-cron`, default `0 15 4 * * *`) |

Thresholds are illustrative placeholders, not validated values, and none of them has been approved by the owner (D12). The `riseai` profile does not override them; the 2026-10-29 pilot values (20,000,000 and 5) briefly became that profile's defaults in an uncommitted change and were reverted on 2026-10-30. To use other values set the environment variable, for example `MONITORING_RISE_MINIMAL_EVIDENCE_APPROVAL_AMOUNT`, and restart.

Startup validation: the amounts must be greater than zero, the durations positive, the counts within their bounds, `supported-schema-versions` non-empty, and the service keys at least 16 characters when `require-service-key` is true (`RiseThresholdValidationTest`).

Payload values the rules read are bounded regardless of configuration: amounts must be plain decimals (at most 18 integer digits and 8 decimals), file hashes 64 hexadecimal characters, identifiers `[A-Za-z0-9._:-]{1,80}`; a value outside those shapes is ignored by the rule that would have used it.

## Database (projection store)

| Setting | Env | Default |
|---|---|---|
| `spring.datasource.url` | `MONITORING_DB_URL` | `jdbc:h2:mem:rise-ai-monitoring;DB_CLOSE_DELAY=-1`; `riseai` profile: `jdbc:h2:file:./data/rise-ai-monitoring` |
| `spring.datasource.username` / `password` | `MONITORING_DB_USERNAME` / `MONITORING_DB_PASSWORD` | `sa` / empty |
| `spring.flyway.locations` | (none) | `classpath:db/migration` |

Only the H2 driver is on the classpath. Using another database requires adding its JDBC driver (and, for MySQL, `flyway-mysql`); that has not been tested. The database must never be the backend's `rise_ai` schema.

## Spring settings in application.yml

| Setting | Value |
|---|---|
| `server.port` | `${SERVER_PORT:8081}` |
| `spring.jackson.mapper.accept-case-insensitive-enums` | `true` |
| `server.error.include-stacktrace` / `include-message` | `never` |
| `management.endpoints.web.exposure.include` | `health,info,metrics` |
| `management.endpoint.health.show-details` | `never` |
| `management.endpoint.health.show-components` | `always` |
| `logging.pattern.level` | Includes MDC `correlationId` and `eventId` |

Profiles:

- `local` (`application-local.yml`): health details always shown; DEBUG logging for `com.riseai.monitoring`.
- `riseai` (`application-riseai.yml`): the profile for running next to the Rise AI backend. Binds to `127.0.0.1` (`SERVER_ADDRESS`), requires a service key, file-backed H2 projection, allow-list of the 20 Rise event types (lower case), `max-event-age: 30d` (so an outbox backlog of up to 30 days is still accepted), AI disabled by default (`MONITORING_AI_ENABLED`), `on-failure: RULES_ONLY`.
- `test` (`src/test/resources/application-test.yml`): a `latencyMs max 1000` threshold, `max-request-bytes: 16384`, `log-results: false`, model `test-model`, 500 ms timeouts, a random in-memory H2 database per Spring context.

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
