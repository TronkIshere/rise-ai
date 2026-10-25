# 01 — Module Rename and Compatibility Plan

**Status: EXECUTED 2026-10-25** (the owner chose "Rename now"). The tables in §1 describe the identity before the rename. Deviation from §5: files were moved with a plain `mv` rather than `git mv` so that nothing was staged; git detects the renames at commit time.

Verification against §6 (all run 2026-10-25): V1 133/133 (same as baseline) · V2 `target/rise-ai-monitoring.jar` produced · V3/V4 starts as `RiseAiMonitoringApplication` on 8081, health UP · V5/V6 `riseai.monitoring.events.received` present, old metric name returns 404 · V7 simulator 2,000/2,000 (same as baseline) · V8 backend and monitor ran together during E2E · V9 stale-name sweep: only allowlisted history mentions remain · V10 pending until the commit (no `git mv`) · V11 backend suite unaffected by the rename.

## 1. Current identity (VERIFIED at `aaf374e`)

| Item | Current value | Where |
|---|---|---|
| Directory | `local-ai-event-monitor/` | repository root |
| Maven coordinates | `com.aicore:local-ai-event-monitor:0.1.0-SNAPSHOT` | `local-ai-event-monitor/pom.xml:15-17` |
| Maven `<name>` / `<description>` | `Local AI Event Monitoring Core` / `Domain-agnostic event monitoring module…` | `pom.xml:18-19` |
| Jar name | `<finalName>local-ai-event-monitor</finalName>` | `pom.xml:63` |
| Root Java package | `com.aicore.eventmonitor` (97 Java files reference it; 103 tracked files in total contain `eventmonitor`) | `src/main/java/com/aicore/eventmonitor/` |
| Main class | `LocalAiEventMonitorApplication` | `src/main/java/com/aicore/eventmonitor/LocalAiEventMonitorApplication.java` |
| Spring application name | `local-ai-event-monitor` | `src/main/resources/application.yml:3` |
| Default port | `${SERVER_PORT:8080}` (backend also uses 8080) | `application.yml`, `.env.example` |
| Metric names | `eventmonitor.events.received`, `.events.rejected`, `.events.duplicates`, `.rule.findings`, `.ai.analyses.attempted`, `.ai.analyses`, `.processing` | `observability/MonitoringMetrics.java:23-29` |
| Request attribute | `eventmonitor.correlationId` | `constants/PlatformConstants.java` |
| Logger category in profiles | `com.aicore.eventmonitor: DEBUG` | `application-local.yml:8`, `test-simulator/core-config/core-simulator.yml` |
| Simulator coordinates | `com.aicore:test-simulator`, package `com.aicore.simulator`, name `Local AI Event Monitoring Core Test Simulator` | `test-simulator/pom.xml:15-19` |
| Simulator target URL | `simulator.core-url: ${SIMULATOR_CORE_URL:http://localhost:8080}` | `test-simulator/src/main/resources/application.yml` |
| Prose name | "Local AI Event Monitoring Core", "Monitoring Core", "the Core" | `README.md`, `docs/*.md`, `test-simulator/README.md` |

Not present (VERIFIED by `git ls-files`): Dockerfile, Docker Compose service for either application, CI workflows, deployment manifests, service discovery configuration, shell build scripts. The only Compose file is `rise-ai-backend/docker-compose.yml`, which runs MySQL only.

Cross-references (VERIFIED): no file outside `local-ai-event-monitor/` mentions `local-ai-event-monitor`, `aicore` or `eventmonitor`. The backend has zero references to the monitor. The rename is therefore self-contained, apart from root `docs/INDEX.md` and this planning folder.

## 2. Naming strategy

- **Module identity** (what the deployable *is*) moves to Rise AI: directory, Maven coordinates, Java package, main class, application name, jar, metric prefix, prose name.
- **Domain concepts and contracts** (what the module *does*, and what clients depend on) stay stable: API paths, configuration prefixes, environment variables, error codes, response envelopes, header names, rule IDs, AI output schema version.
- Rationale for `rise-ai-monitoring`: matches the sibling `rise-ai-backend` (directory = artifactId = `spring.application.name`) and the backend groupId `com.riseai` (`rise-ai-backend/pom.xml`). The word "local" describes a deployment property (local Ollama), not identity, and "event-monitor" is narrower than the planned charity monitoring scope.

## 3. Naming layers and treatment

Classification: **R** = rename required, **RR** = rename recommended, **P** = preserve for compatibility, **N/A** = not applicable.

| # | Layer | Current | Proposed | Class | Reason |
|---|---|---|---|---|---|
| 1 | Directory | `local-ai-event-monitor/` | `rise-ai-monitoring/` | R | Module identity; must match artifactId |
| 2 | Maven groupId | `com.aicore` | `com.riseai` | R | Same organization namespace as the backend |
| 3 | Maven artifactId | `local-ai-event-monitor` | `rise-ai-monitoring` | R | Module identity |
| 4 | Maven name / description | `Local AI Event Monitoring Core` / "Domain-agnostic…" | `Rise AI Monitoring` / "Rise AI charity monitoring: deterministic rules and local AI analysis" | R | User-visible identity |
| 5 | Jar `finalName` | `local-ai-event-monitor` | `rise-ai-monitoring` | R | Startup commands reference the jar |
| 6 | Maven parent | `spring-boot-starter-parent:3.5.15` | unchanged | P | Version alignment with the backend (3.3.5) is a separate decision, not part of a rename |
| 7 | Multi-module aggregator POM | none exists | none | N/A | Each module builds on its own; adding an aggregator is new architecture, out of scope |
| 8 | Java root package | `com.aicore.eventmonitor` | `com.riseai.monitoring` (D1) | RR | Ownership clarity. Separate deployable from the backend, so no class collision, but the backend must then not create its own `com.riseai.monitoring` package (use `com.riseai.outbox` and `com.riseai.finding` instead, see roadmap M3 and M6) |
| 9 | Sub-package names | `controller`, `core.*`, `rule`, `ollama`, … | unchanged | P | Describe responsibilities, not identity |
| 10 | Main class | `LocalAiEventMonitorApplication` | `RiseAiMonitoringApplication` | RR | Identity; referenced by tests and IDE run configs |
| 11 | `spring.application.name` | `local-ai-event-monitor` | `rise-ai-monitoring` | R | Service identity in logs and actuator |
| 12 | Default port | `8080` | `8081` (D11) | R | Duplicate local service identity with the backend; both cannot start together |
| 13 | API paths | `/api/v1/events/monitor`, `/api/v1/monitoring/status`, `/api/v1/monitoring/rules` | unchanged | P | Contract; "monitoring" is a domain word, not the old name |
| 14 | Error codes, `ResponseAPI` / `ErrorResponse` shapes | as in `docs/API_CONTRACT.md` | unchanged | P | Contract consumed by the simulator and, later, the backend relay |
| 15 | Header `X-Correlation-Id` | same in both modules | unchanged | P | Shared cross-module identifier |
| 16 | MDC keys `correlationId`, `eventId` | unchanged | unchanged | P | Log format compatibility; backend uses the same `correlationId` key |
| 17 | Configuration prefixes `monitoring.*`, `ollama.*` | unchanged | unchanged | P | Domain names; renaming breaks every environment variable and profile |
| 18 | Environment variables `MONITORING_*`, `OLLAMA_*`, `SERVER_PORT` | unchanged | unchanged (only the `SERVER_PORT` default value changes) | P | Operator-facing contract |
| 19 | Metric names `eventmonitor.*` | `eventmonitor.events.received` … | `riseai.monitoring.events.received` … | RR | Identity embedded in an observability contract. No dashboards or alerts exist yet (VERIFIED: no monitoring config in the repository), so renaming now is cheap; later it would break dashboards |
| 20 | Metric tag keys (`stage`, `outcome`, `status`, `rule`, `effect`) | unchanged | unchanged | P | Domain |
| 21 | Request attribute `eventmonitor.correlationId` | | `riseai.monitoring.correlationId` | RR | Internal only; follows the package rename |
| 22 | Logger categories in profiles | `com.aicore.eventmonitor` | `com.riseai.monitoring` | R | Silently stops matching after the package rename |
| 23 | Rule IDs (`timestamp-validity`, `attribute-threshold`, …) | unchanged | unchanged | P | Appear in API responses and metric tags |
| 24 | AI output `schemaVersion` `monitoring-assessment.v1` | unchanged | unchanged | P | Output contract |
| 25 | System prompt text ("generic event monitoring system") | unchanged in M1 | changed in M8 | P (in M1) | Behavioural change; belongs to domain adaptation, not to the rename |
| 26 | Health endpoint `/actuator/health` | unchanged | unchanged | P | Standard |
| 27 | Simulator directory `test-simulator/` | unchanged | unchanged | P | Already neutral |
| 28 | Simulator groupId / artifactId / finalName | `com.aicore` / `test-simulator` / `test-simulator` | `com.riseai` / `rise-ai-monitoring-simulator` / `rise-ai-monitoring-simulator` | RR | Avoid a generic jar name inside the Rise namespace |
| 29 | Simulator Java package | `com.aicore.simulator` | `com.riseai.monitoring.simulator` | RR | Consistency with row 8 |
| 30 | Simulator property `simulator.core-url`, env `SIMULATOR_CORE_URL` | default `http://localhost:8080` | key unchanged, default `http://localhost:8081` | P (key) / R (default) | Key is operator-facing; default must follow row 12 |
| 31 | Simulator `CoreContract` class name, "Core" wording | `CoreContract`, "Monitoring Core" | class name unchanged; prose → "Rise AI Monitoring" | P (class) / RR (prose) | Class rename is churn without benefit |
| 32 | `.mvn/wrapper`, `mvnw`, `mvnw.cmd` | | unchanged | N/A | Name-neutral |
| 33 | `.gitignore` | | unchanged | N/A | Name-neutral |
| 34 | Docker image, Compose service, container names | none for either app | none | N/A | Planned in roadmap M10; must use `rise-ai-monitoring` when created |
| 35 | CI/CD, deployment manifests, service discovery | none | none | N/A | Same as 34 |
| 36 | Module README and `docs/*.md` | "Local AI Event Monitoring Core", package paths, jar name | "Rise AI Monitoring", new paths | R | Startup instructions otherwise break (`java -jar target/local-ai-event-monitor.jar` in `README.md:586`, `docs/DEVELOPMENT.md:20`) |
| 37 | Root `docs/INDEX.md` | says the backend is "the only module" (already stale) | lists both modules | R | Stale and wrong |
| 38 | Backend references | none | none | N/A | Backend integration code is written after M1 and uses the new name from the start |
| 39 | Historical documents (`docs/DECISIONS.md` decision texts) | mention old name | keep the text; add one line "Module renamed to rise-ai-monitoring on <date>" | P | Decision records are history |

## 4. Why the rename comes before integration work

- Nothing persistent depends on the old name today: no database, no dashboards, no deployed instances, no backend references. After M3–M6 the backend relay config, monitor database schema, metric dashboards and docs would all embed the name. Renaming first is the cheapest point.
- It cannot come before M0. The monitor has never been built with Maven, so there is no baseline to prove "the renamed module still starts". M0 establishes that baseline.

## 5. Dependency-ordered change list (M1)

Execute on a dedicated branch (for example `chore/rename-rise-ai-monitoring`) as **one commit or one squash-merged PR**, so the rename is atomic in history.

1. **Precondition.** M0 is complete. The baseline test counts for `./mvnw clean test` (monitor) and the simulator are recorded in `05-acceptance-checklist.md`.
2. **Move the directory with history:** `git mv local-ai-event-monitor rise-ai-monitoring`.
3. **Move Java sources** (main and test), keeping history:
   - `src/main/java/com/aicore/eventmonitor` → `src/main/java/com/riseai/monitoring`
   - `src/test/java/com/aicore/eventmonitor` → `src/test/java/com/riseai/monitoring`
   - `test-simulator/src/{main,test}/java/com/aicore/simulator` → `test-simulator/src/{main,test}/java/com/riseai/monitoring/simulator`
4. **Rewrite package and import statements.** Replace `com.aicore.eventmonitor` with `com.riseai.monitoring` first, then `com.aicore.simulator` with `com.riseai.monitoring.simulator`. Order matters: no other `com.aicore.*` prefix exists (VERIFIED), so after both replacements `git grep com.aicore` must return nothing.
5. **Rename the main class** to `RiseAiMonitoringApplication` (file and class), and update its references in tests.
6. **POMs:** monitor groupId, artifactId, name, description, finalName (rows 2–5); simulator groupId, artifactId, name, description, finalName (row 28).
7. **Configuration:**
   - `application.yml`: `spring.application.name: rise-ai-monitoring`, `server.port: ${SERVER_PORT:8081}`.
   - `application-local.yml` and `test-simulator/core-config/core-simulator.yml`: logger category `com.riseai.monitoring`.
   - `.env.example`: `SERVER_PORT=8081`.
   - Simulator `application.yml`: default `SIMULATOR_CORE_URL` `http://localhost:8081`.
   - `src/test/resources/application-test.yml`: no change needed (VERIFIED: contains no package, port or old-name references).
8. **Code identifiers:** metric name constants in `MonitoringMetrics` (row 19); `PlatformConstants.CORRELATION_ID_ATTRIBUTE` (row 21). Update tests that assert metric names.
9. **Documentation:** module `README.md`, `docs/*.md`, `test-simulator/README.md`, `docs/TEST_SIMULATOR.md`, root `docs/INDEX.md`, and this planning folder (replace `local-ai-event-monitor/` paths). Add the one-line history note to the monitor's `docs/DECISIONS.md`.
10. **Stale-name sweep.** These commands must return only lines in an explicit allowlist (this file's §1 table and the DECISIONS history note):
    - `git grep -nE "aicore|eventmonitor|local-ai-event-monitor|LocalAiEventMonitor"`
    - `git grep -nE "Monitoring Core|Local AI Event"`
11. **Verify** (section 6), then commit.

Not part of M1: the prompt text, rule behaviour, new endpoints, dependency or Spring Boot version changes, the backend.

## 6. Verification (acceptance criteria for M1)

All commands are run from `rise-ai-monitoring/` unless stated otherwise. Record the actual output in `05-acceptance-checklist.md`.

| # | Check | Pass condition |
|---|---|---|
| V1 | `./mvnw clean test` | Same test count and zero failures compared with the M0 baseline |
| V2 | `./mvnw clean package` | Produces `target/rise-ai-monitoring.jar`; no `local-ai-event-monitor.jar` |
| V3 | `java -jar target/rise-ai-monitoring.jar` with `MONITORING_AI_ENABLED=false` | Starts; log shows application name `rise-ai-monitoring` and port 8081 |
| V4 | `GET http://localhost:8081/actuator/health` | `{"status":"UP"}` |
| V5 | `POST /api/v1/events/monitor` with the example from `docs/API_CONTRACT.md` | HTTP 200, `monitoringStatus` present |
| V6 | `GET /actuator/metrics/riseai.monitoring.events.received` after V5 | Count ≥ 1. `GET /actuator/metrics/eventmonitor.events.received` returns 404 |
| V7 | Simulator (`test-simulator`, default URL) against the running module, AI disabled | Same pass/fail summary as the M0 baseline run |
| V8 | Backend started at the same time (`rise-ai-backend`, `mvn spring-boot:run`) | Both run; no port conflict |
| V9 | Stale-name sweep (step 10) | Only allowlisted lines |
| V10 | `git log --follow rise-ai-monitoring/src/main/java/com/riseai/monitoring/service/impl/EventMonitoringServiceImpl.java` | Shows history from before the rename |
| V11 | Backend `mvn clean test` | Unchanged from its own baseline (the rename must not touch the backend) |

## 7. Compatibility risks

| Risk | Impact | Mitigation |
|---|---|---|
| Missed package reference in a string (for example `@ComponentScan`, logger names in YAML, `@SpringBootTest(classes=…)`) | Context fails to start, or debug logging is silently lost | V1, V3 and the stale-name sweep; YAML logger categories are explicit in step 7 |
| Metric rename breaks consumers | None exist today | Rename now (row 19); record new names in the monitor's `CONFIGURATION.md` |
| Port change breaks muscle memory and simulator defaults | Simulator hits the backend instead | Step 7 updates the simulator default; V7 |
| IDE run configurations under `.idea/` (gitignored) point at the old main class | Local inconvenience only | Mention in the PR description |
| Partial rename merged | Broken build or duplicate identities | Single atomic commit; V1–V11 gate the merge |
| Windows path case handling during `git mv` | Unlikely; all names change, not only case | Use `git mv`, check `git status` before commit |

## 8. Rollback

- Before merge: abandon the branch.
- After merge: `git revert <rename-commit>`. This is complete because the module has no persistent state, no deployments and no external consumers at M1 time. Rollback must happen before M3/M4 add backend relay configuration or a monitor database; after that, a reverse rename becomes a new forward change.
- Configuration migration for anyone running locally: set `SERVER_PORT=8080` to keep the old port temporarily. No other variable changes.
