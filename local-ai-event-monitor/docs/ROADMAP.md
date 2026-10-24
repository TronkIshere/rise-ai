# Roadmap

What is done is summarized in [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md#current-implementation-status). Everything below is **not implemented** unless explicitly stated.

## Priority 1: verification (blocks confidence in everything else)

1. Run `./mvnw clean test` and `./mvnw clean package` with Maven Central access. Fix any failures, especially:
   - Spring wiring, including `@Validated` service proxies;
   - YAML binding of `qwen3.5:9b` (a colon inside the placeholder default) and of the threshold lists;
   - MockMvc and JSON-path assertions;
   - `MonitoringMetricsTest`.

   Update [TESTING.md](TESTING.md) afterwards.
2. Run the application and the smoke test in [DEVELOPMENT.md](DEVELOPMENT.md#manual-smoke-test-after-starting-the-app).
3. Run the test simulator against the real Core ([TEST_SIMULATOR.md](TEST_SIMULATOR.md)):
   1. a 100-scenario smoke test;
   2. 100,000 scenarios with `monitoring.ai.enabled=false`;
   3. a run with AI enabled.

   Triage `failures.csv` before trusting any accuracy figure.
4. Install Ollama and verify against a real model:
   - the `qwen3.5:9b` / `qwen3.5:4b` tags exist;
   - `format` schema acceptance;
   - the `think` field;
   - `INVALID_RESPONSE` rate and latency.

   Record the results in [AI_AND_OLLAMA.md](AI_AND_OLLAMA.md#model-configuration).

## Priority 2: known defects and technical debt

| Item | Impact | Notes |
|---|---|---|
| Status endpoint calls Ollama `/api/tags` on every request | Load on Ollama; slow status responses when it hangs (bounded by `status-timeout`) | Consider a short cache |
| No test for `Error` release in `EventMonitoringServiceImpl.evaluate` | Regression risk | Add a unit test with a rule that throws an `Error` |
| No coverage measurement | Untested branches are unknown | Add the JaCoCo Maven plugin (build-time only) |
| Core boundary tests are example-based | Exact threshold edges are unpinned in the Core's own tests | Convert to JUnit `@ParameterizedTest` cases (for example 999 / 1000 / 1000.0 / 1001) |
| No tests for chunked oversize bodies, startup validation failures, publisher logging | Unverified behaviour | See [TESTING.md](TESTING.md#known-coverage-gaps) |
| `OLLAMA_THINK=` (empty) assumed to bind to `null` | Workaround for old Ollama versions may not work | Verify; if it does not work, consider a new explicit configuration switch (proposal only, not implemented) |
| Root `README.md` duplicates parts of `docs/` | Risk of drift | Keep `docs/` canonical; shorten the root README over time |

## Priority 3: capabilities with a concrete need

Add these only when a real requirement exists:

- **Authentication** (decision P1 in [DECISIONS.md](DECISIONS.md)) before any exposure beyond a trusted network.
- **Durable or shared idempotency:** a new `EventDeduplicator` implementation, needed only for multi-instance deployment or restart-safe deduplication.
- **Result persistence or forwarding:** a `MonitoringResultPublisher` implementation, needed only when audit or history is required.
- **Model evaluation harness:** a labelled event set and a script measuring assessment quality, schema adherence and latency per model tag.

## Possible future improvements (no commitment)

- Asynchronous processing mode.
- Packaging as a library or Spring Boot starter.
- Per-event-type model selection.
- An OpenAPI specification generated from the controllers.

Avoid introducing a database, message broker or distributed deployment without a concrete requirement ([DECISIONS.md](DECISIONS.md) D2).


## Resolved

- A `java.lang.Error` during processing left the deduplication entry in progress until retention ended. Fixed: `EventMonitoringServiceImpl.evaluate` now releases the entry for `ApplicationException | Error` and for any other `RuntimeException`. There is no dedicated test yet (see Priority 2).