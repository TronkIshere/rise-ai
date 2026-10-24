# Testing

## Latest verified results (recorded 2026-10-24)

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

So Spring wiring, MockMvc behaviour, YAML binding, Jackson serialization of responses, and actuator output are **unverified**. Run `./mvnw clean test` and update this table.

The local runs used AssertJ 3.14 and Jackson 2.14. Spring Boot 3.5 manages newer versions, so API drift is possible but unlikely for the APIs used.

## Test infrastructure (`src/test/java/com/aicore/eventmonitor/testsupport`)

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
