# Test Simulator for Rise AI Monitoring

A small, standalone Spring Boot command-line tool. It generates a reproducible set of test scenarios, sends them to a **running** Rise AI Monitoring through its real REST API, compares each response with an expected outcome defined before the request is sent, and writes detailed reports.

It answers three questions:

1. Which situations were tested?
2. What percentage did the Core handle correctly, per category and overall?
3. Exactly which scenarios failed, and what did the Core return?

The simulator never reads Core internals or calls Core classes. It talks to the Core only over HTTP.

> **Verification status.**
>
> | Item | Status |
> |---|---|
> | Compilation | Passes with `javac` on JDK 21. Spring classes were replaced by signature-only stubs, so Spring wiring is unverified. |
> | Unit tests | 38 of 38 pass. They do not need Spring. |
> | Maven build (`mvn` / `./mvnw`) | Not run. Maven Central was unreachable when this was built. |
> | Run against the real Core | **Never done.** |
>
> See [Limitations](#limitations).

## Contents

1. [Prerequisites](#prerequisites)
2. [1. Start the Rise AI Monitoring](#1-start-the-monitoring-core)
3. [2. Run the simulator](#2-run-the-simulator)
4. [Configuration](#configuration)
5. [Scenario categories](#scenario-categories)
6. [How expected outcomes are defined](#how-expected-outcomes-are-defined)
7. [Scoring](#scoring)
8. [Metrics](#metrics)
9. [Reports](#reports)
10. [Exit codes](#exit-codes)
11. [Limitations](#limitations)
12. [Project layout](#project-layout)

## Prerequisites

- **Java 21.**
- **Maven:** the repository's wrapper `../mvnw` works from this directory, because it locates `.mvn/` next to the script. Alternatively, a local Maven 3.9+.
- **Network access to Maven Central** for the first build.
- **A running Rise AI Monitoring** started with the simulator profile (next section).
- **Ollama** is optional. Without it, AI-dependent scenarios exercise the Core's offline behaviour.

## 1. Start the Rise AI Monitoring

The Core's default configuration has **no attribute thresholds and no event-type allow-list**, so those rules can never fire. The simulator's expected outcomes assume the profile in [`core-config/core-simulator.yml`](core-config/core-simulator.yml), which sets:

| Setting | Value | Why |
|---|---|---|
| `monitoring.rules.allowed-event-types` | `request.completed`, `job.completed`, `disk.status`, `service.heartbeat`, `deployment.finished` | Lets the `supported-event-type` rule be tested |
| Threshold on `latencyMs` | `max 1000`, severity `HIGH` | Lets the `attribute-threshold` rule be tested |
| Threshold on `freeDiskPercent` | `min 10`, severity `MEDIUM` | Lets the `attribute-threshold` rule be tested with a minimum |
| `monitoring.delivery.log-results` | `false` | Keeps the Core console readable during long runs |
| `ollama.request-timeout` | `20s` | Must stay below the simulator's `request-timeout` (default 30 s) |

Start the Core from the **repository root** with that file as an additional configuration location:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.config.additional-location=file:./test-simulator/core-config/core-simulator.yml
```

Windows:

```bat
mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--spring.config.additional-location=file:./test-simulator/core-config/core-simulator.yml
```

For a fast, purely deterministic run that skips the model, add `--monitoring.ai.enabled=false`:

```bash
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--spring.config.additional-location=file:./test-simulator/core-config/core-simulator.yml --monitoring.ai.enabled=false"
```

### Preflight checks

Before generating any workload, the simulator checks that:

| Check | Requirement |
|---|---|
| `GET /actuator/health` | Reachable and `UP` |
| `GET /api/v1/monitoring/status` | Rules enabled, `aiTrigger = ON_RULE_FLAG`, deduplication enabled |
| `GET /api/v1/monitoring/rules` | All six rules are active |
| Calibration probes (4 events) | `latencyMs=1001` is flagged; `latencyMs=1000` is not; `freeDiskPercent=9` is flagged; an unlisted event type is rejected |

The probes only confirm that the Core runs with the expected configuration. They are not used as ground truth.

- If the Core is **unreachable**, the simulator stops and prints the start command above.
- If any **check fails** and `simulator.fail-fast=true` (the default), it stops without sending the workload.

## 2. Run the simulator

Run everything below from the `test-simulator/` directory.

**Smoke test (100 scenarios):**

```bash
../mvnw spring-boot:run -Dspring-boot.run.arguments="--simulator.scenario-count=100"
```

**Full run (100,000 scenarios):**

```bash
../mvnw spring-boot:run -Dspring-boot.run.arguments="--simulator.scenario-count=100000 --simulator.seed=42 --simulator.concurrency=4 --simulator.core-url=http://localhost:8081"
```

Windows: replace `../mvnw` with `..\mvnw.cmd`.

**Common variations:**

| Goal | Add |
|---|---|
| Different seed | `--simulator.seed=7` |
| More parallel requests | `--simulator.concurrency=8` |
| Longer per-request timeout | `--simulator.request-timeout=60s` |
| Exclude AI-dependent categories D and J | `--simulator.include-ai-scenarios=false` (their share is redistributed; the total stays exact) |
| Different Core address | `--simulator.core-url=http://host:port`, or the `SIMULATOR_CORE_URL` environment variable |
| Continue despite failed preflight checks | `--simulator.fail-fast=false` (affected scenarios will be reported as `FAIL`) |

**As a jar:**

```bash
../mvnw clean package
java -jar target/rise-ai-monitoring-simulator.jar --simulator.scenario-count=100
```

Reports go to `reports/` relative to the current directory; see [Reports](#reports).

**Expected duration:**

- Each flagged event (about 30% of scenarios) makes the Core call the local model.
- With a 9B model on modest hardware, a 100,000-scenario run can take many hours.
- Start with the smoke test. For rule-only behaviour, run the Core with AI disabled.

## Configuration

`src/main/resources/application.yml`; all keys can be overridden on the command line.

| Property | Default | Meaning |
|---|---|---|
| `simulator.core-url` | `http://localhost:8081` (env `SIMULATOR_CORE_URL`) | Base URL of the running Core |
| `simulator.scenario-count` | `100000` | Exact number of scenarios (1 – 1,000,000) |
| `simulator.seed` | `42` | Seed for the category plan and all generated values |
| `simulator.concurrency` | `4` | Worker threads (1 – 64). Each worker runs one scenario at a time. |
| `simulator.request-timeout` | `30s` | Per-request timeout. Connect timeout is the smaller of this and 5 s. |
| `simulator.report-directory` | `reports` | Parent directory of run reports |
| `simulator.include-ai-scenarios` | `true` | Include categories D and J |
| `simulator.fail-fast` | `true` | Stop when preflight checks fail. Never stops because of individual scenario failures. |
| `simulator.distribution.<CATEGORY>` | see below | Relative weight per category, for example `--simulator.distribution.NORMAL=40`. Binding of these map keys is unverified in a running Spring context. |

There are no retries. Every request is sent exactly once.

## Scenario categories

Default weights, and the planned counts for 100,000 scenarios:

| Code | Category | Weight | Count | Templates |
|---|---|---|---|---|
| A | Normal events | 25 | 25,000 | Normal events of each allowed type; severity absent or INFO/LOW/MEDIUM; optional `correlationId` |
| B | Deterministic rule violations | 15 | 15,000 | See below |
| C | Boundary values | 15 | 15,000 | See below |
| D | AI context interpretation | 5 | 5,000 | See below |
| E | Invalid or malformed events | 10 | 10,000 | See below |
| F | Duplicate events | 8 | 8,000 | See below |
| G | Missing and optional fields | 7 | 7,000 | Required fields only; all fields; empty attributes; null, array or string payload; lower-case severity; body `correlationId` |
| H | Event sequences | 5 | 5,000 | See below |
| I | Large and unusual valid payloads | 5 | 5,000 | Payload up to about 58 KB, 40–50 attributes, nesting 5–15, Unicode text, long attribute values |
| J | AI failure resilience | 5 | 5,000 | Flagged events (latency or CRITICAL severity) checked against the AI failure contract |

**B: rule violations.**

- Flagged: `latencyMs` > 1000; `freeDiskPercent` < 10; reported severity HIGH or CRITICAL; non-numeric `latencyMs`.
- Rejected: unlisted event type; `occurredAt` 1 h in the future; `occurredAt` 8 days old.

**C: boundary values.** Each value is a separate template, so every threshold operator is pinned down:

| Rule | Values tested |
|---|---|
| `latencyMs` vs max 1000 (`>`) | 999, 1000, 1001, 1000.0, 1000.001, 0 |
| `freeDiskPercent` vs min 10 (`<`) | 10, 9.999, 0, −1 |
| Reported severity (`>=` HIGH) | MEDIUM, HIGH |
| Clock skew 5 m | `occurredAt` = `receivedAt` + 5 m, and + 5 m 1 s |
| Event age 7 d | `occurredAt` = `receivedAt` − 7 d, and − 7 d 1 s |
| Payload size | 65,536 and 65,537 bytes |
| Attribute count | 50 and 51 |
| Attribute value length | 1,000 and 1,001 characters |
| Payload depth | 20 and 21 |
| `eventId` length | 128 and 129 |
| `correlationId` length | 64 and 65 |

**D: AI context interpretation.**

- 70% of these scenarios are an unambiguous multi-signal failure (very high latency, high error rate, repeated failures, `status=FAILED`). They are scored against the AI rubric.
- 30% are elevated latency during a declared maintenance window. These are exploratory and **unscored**.
- Cross-event context ("individually normal, collectively unusual") is **not generated**, because the Core analyses each event independently.

**E: invalid or malformed events.**

- Missing `eventId`, `eventType`, `source` or `occurredAt`.
- Blank `eventId`, or `eventId` containing a space.
- Invalid timestamp; severity `SEVERE`.
- Truncated JSON; attributes sent as an array.
- Nested attribute value; null attribute value.
- 65-character `correlationId`; attribute name containing a space.
- Valid JSON sent as `text/plain`.

**F: duplicate events.**

- Exact resend.
- Resend with a new `receivedAt` and `correlationId`.
- Same ID with different content.
- Same ID from another source.
- A rejected event sent twice.

**H: event sequences.** Three to five ordered events share a `correlationId`:

- failures, then recovery;
- normal, normal, then HIGH severity;
- retries, then a 30-second request.

Each event is expected to be evaluated on its own.

The total is exact for any `scenario-count`; weights are distributed by largest remainder. The category order is a seeded shuffle, and the run prints the planned counts before it starts.

## How expected outcomes are defined

**Expectations come only from the contract, never from Core responses.** Every expectation is fixed when the scenario is generated, before any request is sent. It comes from:

- the Core's documented API and rule contract (`docs/API_CONTRACT.md`, `docs/EVENT_LIFECYCLE.md`, `docs/AI_AND_OLLAMA.md`);
- the default limits in the Core's `application.yml`;
- the simulator Core profile.

These values are written down in `scenario/CoreContract.java`. The generator has no access to the HTTP client, and nothing is inferred from Core responses. Each scenario records its `expectedOutcomeSource`.

**What can be checked per request:**

- HTTP status, and the application error code for error responses;
- `monitoringStatus`, `ruleDecision`, required rule IDs or "no findings", `overallRiskLevel`;
- `duplicate`, `reviewRequired`, `aiAnalysis.analysisStatus`;
- echoed `eventId` and `correlationId`;
- for replays, the same `createdAt` as the original.

**Fields that depend on the AI are not checked for rule categories.** For example, `monitoringStatus` on a flagged event in category B is `COMPLETED` or `INCONCLUSIVE` depending on the model, so B only checks the rule-level fields.

**AI resilience (J) is a contract applied to whatever AI status the Core reports:**

| Observed `aiAnalysis.analysisStatus` | Required result |
|---|---|
| `COMPLETED` | `monitoringStatus` is `COMPLETED` |
| `DISABLED` | `monitoringStatus` is `COMPLETED_WITHOUT_AI` |
| `UNAVAILABLE`, `TIMEOUT`, `INVALID_RESPONSE`, `FAILED` | `monitoringStatus` is `INCONCLUSIVE` (or `COMPLETED_WITHOUT_AI` if the Core reports `aiFailureMode=RULES_ONLY`) and `reviewRequired` is true |
| Anything else (for example `SKIPPED`) | Fails |

In every case, the rule finding and the rule-derived risk must be present.

**AI rubric (D).** The rubric applies only when the analysis is `COMPLETED`; otherwise the scenario is `UNSCORED`. The assessment must then be `NEEDS_REVIEW` or `LIKELY_ANOMALOUS`. AI prose is never compared.

## Scoring

| Status | Meaning |
|---|---|
| `PASS` | Every expected field matched |
| `FAIL` | At least one expected field differed; the reason lists every mismatch |
| `UNSCORED` | No defensible comparison: an exploratory scenario, or the AI rubric could not be applied because the AI did not complete. Never counted as correct. |
| `ERROR` | No HTTP response (timeout, refused connection, I/O error), an unreadable body, or a simulator failure |

An HTTP 4xx or 5xx response is a **response**: it is `PASS` or `FAIL` depending on the expectation, not `ERROR`. A failed step stops the remaining steps of that scenario.

## Metrics

**Accuracy**

- **Overall accuracy:** passed / (passed + failed), printed with the exact numerator and denominator, for example `8,450 / 10,000 = 84.50%`. Unscored and errored scenarios are excluded from the denominator and reported separately.
- **Per category:** generated, completed, scored, passed, failed, unscored, errors, accuracy, evaluation coverage (scored / generated), and the top failure reasons.

**Binary classification (per labeled event)**

- An event counts as **anomalous** when the Core returns HTTP 200, the result is neither `REJECTED` nor a replayed duplicate, and `reviewRequired` is true or `overallRiskLevel` is MEDIUM, HIGH or CRITICAL.
- Labels are set at generation time for valid events: normal events are labeled normal, flagged events anomalous. Rejected, invalid and replay steps are unlabeled.
- Reported: TP, TN, FP, FN, precision, recall, F1. A zero denominator shows `N/A`.

**System metrics** (never merged into one number)

| Metric | Definition |
|---|---|
| API handling pass rate | Categories E, F, G |
| Deterministic rule accuracy | Categories B, C |
| AI classification accuracy | Rubric-scored D scenarios |
| Monitoring decision accuracy | (TP + TN) / labeled |
| HTTP response rate | Responses / requests attempted |
| 2xx share | 2xx responses / responses |
| Request failure rate | (Timeouts + connection failures + I/O errors) / requests attempted |
| Evaluation coverage | Scored / generated |
| AI availability, timeout, invalid-response, unavailable and failed rates | Each over responses where the Core attempted AI analysis (status not `SKIPPED` or `DISABLED`) |

**Performance**

- Latency average, p50, p95, p99 (nearest rank) and max, over requests that received any HTTP response.
- Throughput: responses received per second, and scenarios per second, over wall-clock run time. Timeouts and connection failures are excluded from both.

## Reports

Written to `reports/<yyyyMMdd-HHmmss>-seed<seed>/`. Per-scenario CSVs are appended as results arrive, so 100,000 results are never held in memory.

| File | Contents |
|---|---|
| `summary.json` | Run info, distribution, counts, overall and per-category accuracy, confusion matrix, system metrics, request counts, AI status counts, latency, throughput, top failure reasons |
| `category-summary.csv` | One row per category |
| `results.csv` | One row per scenario: category, template, description, status, HTTP statuses, final monitoring/AI/risk/rules, latency, expected, actual, failure reason |
| `failures.csv` | `FAIL` and `ERROR` only, with source of expectation, seed, run tag, regeneration hint, and request bodies truncated to 1,000 characters |
| `unscored.csv` | `UNSCORED` scenarios with the reason |
| `run-config.json` | Configuration, Core facts from the status endpoint (AI enabled, runtime state, model name, failure mode), calibration results, the assumed Core profile, run tag and reference time |

**Reproducing a scenario.**

- These are identical for the same seed, scenario count and distribution: category, template, values and expectations.
- These differ between runs: event IDs contain a run tag, so the Core's deduplication ledger cannot replay results from an earlier run; and timestamps are relative to the run's reference time.

The Core version is not exposed by its API, so it is recorded as such.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Run completed. Failures may still be present; read the report. |
| 2 | Core unreachable; nothing was sent |
| 3 | Core configuration does not match the simulator profile and `fail-fast` is on; only the 4 calibration probes were sent |

## Limitations

- **Requires the simulator Core profile.** Without it, threshold and event-type expectations are wrong by design; the preflight calibration detects this.
- **Cross-event behaviour is not simulated.** The Core has no cross-event correlation, so sequences only verify per-event independence.
- **AI faults cannot be injected.** AI timeouts, invalid output and unavailability are scored only when they actually occur. To exercise the offline path, start the Core with `OLLAMA_BASE_URL` pointing at an unused port.
- **The AI rubric is deliberately narrow.** It covers one unambiguous template; ambiguous cases are unscored. It is not a measure of overall model quality.
- **Duplicate scenarios use events that rules alone resolve.** The Core does not store `INCONCLUSIVE` results, so a flagged duplicate's outcome would depend on AI availability.
- **Clocks must agree.** Events carry an explicit `receivedAt` two minutes before run start. If the Core's clock is more than about 7 minutes behind the simulator's, `timestamp-validity` rejects events. Scenarios without `receivedAt` assume the run finishes within 7 days.
- **Core deduplication memory.** The Core's default capacity of 10,000 tracked events is enough, because each duplicate is resent immediately after its original.
- **Verification.**
  - Unit tests ran with Spring stubbed. Maven builds, Spring property binding and the Spring entry point (`SimulatorApplication`, `SimulatorRunner`) are unverified.
  - No run against the real Core has been performed.
  - A 100,000-scenario plumbing run against a test stub that returns a fixed "normal" response was used only to check streaming, memory and report generation. Its accuracy numbers are meaningless.

## Project layout

```
test-simulator/
  pom.xml                         standalone project, same Spring Boot 3.5.15 / Java 21 parent as the Core
  core-config/core-simulator.yml  Core configuration assumed by the expected outcomes
  src/main/java/com/riseai/monitoring/simulator/
    SimulatorApplication          entry point; exit code from SimulatorRunner
    configuration/                SimulatorProperties (simulator.*)
    scenario/                     Category, CoreContract, ExpectedOutcome, Outcomes, Scenario, ScenarioStep,
                                  EventFactory, ScenarioGenerator
    client/                       CoreClient (JDK HttpClient), CoreResponse, TransportOutcome
    evaluation/                   ScenarioEvaluator, ScenarioResult, StepResult, ScenarioStatus
    report/                       RunMetrics, CategoryStats, ReportWriter, Csv, Ratios
    runner/                       SimulatorRunner (Spring), SimulationRun, CorePreflight, CoreFacts, ScenarioExecutor
  src/test/java/com/riseai/monitoring/simulator/
    testsupport/StubCoreServer    JDK HttpServer stand-in for client and run tests; not the real Core
    scenario/, evaluation/, report/, client/, runner/   unit tests
```

Dependencies: `spring-boot-starter`, `spring-boot-starter-json`, `spring-boot-starter-validation`, the configuration processor, Lombok, and `spring-boot-starter-test`. Spring Web is not needed, because the simulator only makes HTTP calls and does not serve them; it uses the JDK `HttpClient`.
