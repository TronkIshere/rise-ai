# Testing

## Latest verified results (recorded 2026-10-25)

Environment: Windows 11, JDK 21.0.2 (`JAVA_HOME` set explicitly to JDK 21; the machine default is JDK 20, which fails with "release version 21 not supported"), Maven 3.9.11 via `./mvnw`.

| Command / action | Result |
|---|---|
| **Verification baseline 2026-10-30** (JDK 21.0.2; `0186911` plus uncommitted working-tree changes) `./mvnw -B clean package` | **218 of 218 passed**, exit 0, BUILD SUCCESS, 0 skipped; jar SHA-256 begins `dd3ee98e`. The first run of this tree failed one test (`RiseRuleAuditTest.dp01FlagsADisputeExactlyOnceInEitherDeliveryOrder`, wrong expected amount scale), fixed and rerun |
| Same baseline: backend `mvn -B clean test` (Maven 3.9.11, Testcontainers MySQL) | **149 of 149 passed**, exit 0, 0 skipped |
| Same baseline: simulator `mvn -B clean package` | **43 of 43 passed**, exit 0 |
| Same baseline: Rise suite against the jar built above (`riseai` profile, file H2, AI off, port 8083) | **20 of 20 passed, 8 of 8 rules**, exit 0 |
| Same baseline: restart durability (manual, single instance, **file-backed H2**, not `jdbc:h2:mem:`) | event delivered, replayed with `duplicate: true`, monitor restarted on the same file, replayed again with `duplicate: true` and the stored finding. Multi-instance behaviour was **not** verified |
| Same baseline: Docker Compose (`docker compose -p rise-ai up -d --build`, then `down -v`) | all three services healthy; backend Flyway reached V10 on MySQL 8.4; monitor Flyway applied V1 and V2; no ERROR in either log. No end-to-end traffic was driven through Compose |
| Earlier: `./mvnw -B clean package` (2026-10-30, JDK 21, before the work above; superseded) | **202 of 202 passed**, BUILD SUCCESS. New: `RiseRuleAuditTest` (21), `FindingSchemaContractTest` (8), `RiseEventSchemaContractTest` (9), `RiseThresholdValidationTest` (5). The same jar was then used for the Rise suite |
| Simulator `mvn -B clean package` / Rise suite against the real-tree jar from the row above (SHA-256 begins `601c8266`) | **43/43**; **20/20, 8/8 rules** (2026-10-30, `riseai` profile, AI off, default thresholds 50,000,000 and 3) |
| AI note evaluation (`docs/evaluation/README.md`) | 21 scenarios, 14/14 notes passed, 0 failures, median 68.3 s; not the 200-scenario gate |
| `./mvnw clean test` before any change (baseline, old name) | **133 of 133 passed**, BUILD SUCCESS. First real run of the Spring/MockMvc integration tests and `MonitoringMetricsTest` |
| `./mvnw clean package` after the rename | **133 of 133 passed**; produced `target/rise-ai-monitoring.jar` |
| `./mvnw clean package` after the Rise integration (final) | **144 of 144 passed** (11 new in `RiseMonitoringIntegrationTest`), BUILD SUCCESS |
| `./mvnw -f test-simulator/pom.xml clean package` (final) | **38 of 38 passed** |
| Simulator, 2,000 scenarios, seed 42, AI scenarios excluded, against the running jar with `core-simulator.yml` and `--monitoring.ai.enabled=false` | **2,000/2,000 passed**, 0 request failures. Same result before the rename, after the rename, and after the Rise changes |
| `riseai` profile started without `MONITORING_SERVICE_KEY` | Fails at startup with the configuration message (intended) |
| End-to-end with `rise-ai-backend` (MySQL 8.4, both jars, relay enabled) | See `docs/ai-monitoring/06-progress-report.md` at the repository root: 69 events delivered, 0 failed; EV-01 and LG-01 findings produced and reviewed; backlog drained after a monitor outage; projection survived a restart |
| AI-enabled analysis against a real Ollama model | **Not run** in this session |

### Earlier results (2026-10-24, before the rename)

| Command / action | Result |
|---|---|
| `./mvnw clean test`, `./mvnw clean package` | **Not run successfully.** The wrapper failed to download Maven (`repo.maven.apache.org` unreachable); `mvn` is not installed. |
| `javac --release 21 -Xlint:all` on all main sources | Passed with 0 warnings. Real Lombok, Jackson, Jakarta Validation, Servlet and SLF4J; **Spring and Micrometer replaced by signature-only stubs**. |
| `javac` on all test sources | Passed, with the same caveat. Spring Test, MockMvc and Micrometer were stubs; JUnit 5, AssertJ and Hamcrest were real. |
| JUnit Platform Console, all tests not needing Spring or Micrometer (11 classes) | **109 of 109 passed** |
| Same JUnit run repeated after the `EventMonitoringServiceImpl` refactor (field order, helper methods, `Error` handling in `evaluate`) | **109 of 109 passed** |
| Test simulator: `javac` on main and test sources | Passed with 0 warnings (Spring stubbed as above) |
| Test simulator: JUnit, 6 test classes | **38 of 38 passed** |
| Test simulator: 100,000-scenario plumbing run against `StubCoreServer`, heap capped at 256 MB | Completed: 120,473 requests, 0 transport errors, exit code 0, 1,331 s; memory flat. **The stub always answers "normal", so its accuracy and latency figures say nothing about the Core.** |
| Spring integration tests (4 classes, 22 tests) | **Never executed** |
| `MonitoringMetricsTest` (2 tests) | **Never executed**; it needs real Micrometer |

Those gaps were closed by the 2026-10-25 runs above.

### Rule failure and deduplication error paths (2026-10-30)

`RiseRuleFailureIsolationIntegrationTest` (2 tests: failing rule plus healthy real rule on one event, redelivery after the fix gives the full finding once with no duplicate rows, metric per rule, no exception message in logs), three new cases in `EventMonitoringServiceImplTest` (completion failure releases the registration, evaluation exception releases it, `Error` releases it). Stale in-progress takeover was already covered by `JdbcEventDeduplicatorTest.anInterruptedEventIsTakenOverOnceItsInProgressTimeoutHasPassed`; the in-memory store has no takeover (entries leave only by retention). Full run `sh ./mvnw -B clean test` (JDK 21): **223 of 223 passed**, 0 failures, 0 skipped, BUILD SUCCESS.

### Rise monitoring tests (`controller/RiseMonitoringIntegrationTest`, 11 tests)

Service-key enforcement (API, metrics, health open); EV-01 across campaigns with idempotent redelivery; re-evaluation after lost deduplication adds no projection rows; EV-02; DB-02 and DB-03 including an out-of-order delivery that must not produce a finding; DB-01 thresholds; LG-01 CRITICAL and quiet consistent runs; AO-01 severities; DP-01 only after a release; malformed envelopes (`schemaVersion`, mismatched `eventId`, missing envelope) rejected with 2000; no accusatory terms in any explanation.

The local runs used AssertJ 3.14 and Jackson 2.14. Spring Boot 3.5 manages newer versions, so API drift is possible but unlikely for the APIs used.

## Test infrastructure (`src/test/java/com/riseai/monitoring/testsupport`)

| Class | Purpose |
|---|---|
| `StubOllamaServer` | JDK `HttpServer` on 127.0.0.1 that imitates `/api/chat` and `/api/tags`. Configurable status, body, delay and installed models. Records chat bodies and request paths. `unusedPort()` provides a refused-connection port. |
| `FakeAiEventAnalyzer` | `AiEventAnalyzer` test double: returns a configured `AiAnalysis`, throws, or blocks on latches (for concurrency tests) |
| `RecordingResultPublisher` | Captures published results |
| `MutableClock` | Controllable `Clock` |
| `TestEvents` | Fixtures: `properties()` (with a `latencyMs max 1000` threshold), `request(..)`, `event()`, `completedAnalysis(..)`, `VALID_ASSESSMENT_JSON` |

No test needs Ollama, a model, or internet access.

## Test classes

Counts are `@Test` methods.

### Unit tests (executed, all passing)

| Class | # | Covers |
|---|---|---|
| `normalization.EventNormalizerTest` | 8 | Trimming and lower-casing, `receivedAt` default, correlation precedence, fingerprint stability and sensitivity, non-scalar/overlong attributes, payload depth, JSON-null payload |
| `deduplication.InMemoryEventDeduplicatorTest` | 10 | Replay, conflict, source scoping, release, retention expiry, completed-first eviction, no eviction of in-progress entries, capacity exhaustion, 16-thread concurrent registration, disabled mode, store type |
| `rule.RuleEngineTest` | 19 | Every rule, decisions, triggers, engine/rule disabling, failing rule, duplicate rule IDs |
| `analysis.RiskLevelNormalizerTest` | 2 | Severity → risk mapping; `RiskLevel.highest` |
| `analysis.AiOutputParserTest` | 9 | Valid v1, code fence, sanitizing, malformed/trailing JSON, missing fields, unexpected enums, wrong types, forbidden fields (`eventId`, `riskLevel`, `ruleFindings`), list and blank limits |
| `analysis.AnalysisPromptBuilderTest` | 7 | Role constraints, payload omitted by default, payload opt-in with delimiter escaping, redaction, identifier exclusion, truncation, closed schema |
| `ollama.OllamaClientTest` | 10 | Request format, 404 model not found, 500, unreadable body, missing message, empty output, real timeout (<2 s), refused connection, tags, base-URL validation |
| `ollama.OllamaEventAnalyzerTest` | 14 | Completed v1, configured model and `think`/`num_ctx`, `think` omitted when null, invalid responses, offline, timeout, missing model, server error, blank model makes no request, runtime states, client construction makes no request |
| `result.MonitoringResultAssemblerTest` | 11 | Statuses, **risk identical across AI outcomes**, preserved findings, `UNKNOWN` on missing AI, review policy |
| `service.impl.EventMonitoringServiceImplTest` | 15 | Orchestration with a fake analyzer: AI on/off, unavailable/timeout/exception, rejection, duplicates, conflict, retry after inconclusive, invalid events, publisher failure, **concurrent duplicate submissions**, `eventId` vs `correlationId`, **no Ollama traffic when AI disabled** (real adapter and stub) |
| `configuration.ConfigurationDefaultsTest` | 4 | `application.yml` defaults and placeholders (`qwen3.5:9b`, timeouts), no cloud provider, no model hardcoded in Java, payload excluded by default |

### Written but not executed

| Class | # | Covers |
|---|---|---|
| `observability.MonitoringMetricsTest` | 2 | Counter and timer names and tags with `SimpleMeterRegistry` |
| `controller.EventMonitoringApiIntegrationTest` | 16 | Full Spring context with the stub server: success, AI completed, invalid output, timeout, missing model, replay, conflict, rule rejection, 400/1000, 400/2000, 400/1001, 413/1005, correlation header, status/storage fields, rules list, health and metrics |
| `controller.OllamaOfflineIntegrationTest` | 3 | Healthy app with Ollama unreachable; `INCONCLUSIVE` with kept findings; rules-only still completes |
| `controller.AiDisabledIntegrationTest` | 1 | `DISABLED`, `COMPLETED_WITHOUT_AI`, no stub requests |
| `controller.StartupIsolationIntegrationTest` | 2 | No Ollama request at startup; configured model ID reaches `/api/chat` |

## Known coverage gaps

These tests do not exist; they are not planned work unless prioritized in [ROADMAP.md](ROADMAP.md):

- No test for `RequestSizeLimitFilter` with a **chunked** body (no `Content-Length`).
- No test that a `java.lang.Error` thrown during `EventMonitoringServiceImpl.evaluate` releases the deduplication entry. The behaviour is implemented but untested.
- No test of property-validation failures at startup (for example a threshold without bounds, or out-of-range values).
- No test of `LoggingMonitoringResultPublisher` output or of `log-results: false`.
- No test that 405/415/404 errors include `correlationId`.
- No test against a real Ollama server or a real model, and no measurement of output quality.
- No load or performance tests.
- No coverage measurement (for example JaCoCo), so untested branches are not known precisely.
- Core unit tests do not pin every threshold boundary; for example, a value exactly equal to a threshold maximum is covered only by the test simulator.
- No AI quality evaluation on labelled, domain-specific events.

## Test simulator

`test-simulator/` contains 6 test classes: `ScenarioGeneratorTest`, `ScenarioEvaluatorTest`, `RunMetricsTest`, `ReportWriterTest`, `CoreClientTest` and `SimulationRunTest`. `StubCoreServer` is its HTTP stand-in for the Core.

These tests cover generation, scoring, metrics and reports. They do **not** test the Core itself; the simulator does that only when run against a live Core, which has not happened yet. See [TEST_SIMULATOR.md](TEST_SIMULATOR.md).

## Tests added after the 202/202 run (executed in the 2026-10-30 verification baseline above)

`JdbcEventDeduplicatorTest`, `RiseFindingEngineFailureTest`, two DP-01 tests in `RiseRuleAuditTest` (monitor); `AlertWebhookClientTest`, `AlertDeliveryIntegrationTest`, `AlertWithoutWebhookIntegrationTest` and `AlertDispatcherFencingIntegrationTest` (backend; the last three need Docker for MySQL), plus two cases in `RiseMonitoringIntegrationTest` (DP-01 dispute-before-release over the API, over-long identifiers). `InMemoryEventDeduplicatorTest`, `EventMonitoringServiceImplTest` and `EventMonitoringApiIntegrationTest` (store type now `JDBC`) and the backend `OutboxRelayIntegrationTest` were adapted to the changed APIs. All of them were executed in the verification baseline: monitor 218/218 and backend 149/149 include them (`JdbcEventDeduplicatorTest` 10, `RiseRuleAuditTest` 23, `RiseFindingEngineFailureTest` 1, `RiseMonitoringIntegrationTest` 16; `AlertDeliveryIntegrationTest` 7, `AlertWithoutWebhookIntegrationTest` 1, `AlertDispatcherFencingIntegrationTest` 3, `AlertWebhookClientTest` 9). The earlier 202/202 and 138/138 totals in the table are historical. The concurrency tests use several deduplicator instances inside one JVM; they do not prove multi-process behaviour, and the alert tests use a local stub webhook, so delivery remains documented as at-least-once.
