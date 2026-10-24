# Development

## Prerequisites

| Tool | Requirement | Verified? |
|---|---|---|
| JDK | 21 | Source compiled with OpenJDK 21.0.x `javac` in the authoring environment |
| Maven | 3.9+, or `./mvnw` (downloads 3.9.11) | **No.** Maven Central was unreachable, so neither Maven nor the wrapper has run. |
| Spring Boot | 3.5.15 (parent POM) | Declared, but never resolved by Maven |
| Ollama | Optional at runtime; 0.5+ for `format` schemas | **No.** It has never been run against this code. |
| Network | Maven Central for the first build | n/a |

## Build, run, package

These are standard commands. **None has been executed for this project**, for the reasons above.

```bash
./mvnw clean test
./mvnw clean package
java -jar target/local-ai-event-monitor.jar
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
MONITORING_AI_ENABLED=false ./mvnw spring-boot:run
```

The last command runs in rules-only mode, so no traffic goes to Ollama.

The application starts and serves rules-only results without Ollama. AI-dependent events become `INCONCLUSIVE` (see [AI_AND_OLLAMA.md](AI_AND_OLLAMA.md#behaviour-summary)).

## Tests without Ollama

All tests are designed to run without Ollama, a model, or internet access (beyond Maven dependency download):

- `StubOllamaServer` (a JDK `HttpServer` on 127.0.0.1) imitates `/api/chat` and `/api/tags`.
- A closed local port simulates a refused connection.

Details and the latest verified results are in [TESTING.md](TESTING.md).

The test simulator's unit tests also need no Ollama and no running Core:

```bash
./mvnw -f test-simulator/pom.xml clean test
```

Run them with the Core's tests whenever you change the simulator. See [TEST_SIMULATOR.md](TEST_SIMULATOR.md).

## Manual smoke test (after starting the app)

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/api/v1/monitoring/status
curl -s http://localhost:8080/api/v1/monitoring/rules
curl -s -X POST http://localhost:8080/api/v1/events/monitor -H 'Content-Type: application/json' \
  -d '{"eventId":"evt-1","eventType":"request.completed","source":"edge-gateway","occurredAt":"2026-10-24T10:00:00Z","attributes":{"latencyMs":120}}'
```

Adjust `occurredAt` to within `max-event-age` and `max-clock-skew` of the current time, or `timestamp-validity` rejects the event.

## Code conventions

These are inherited from the reference examples and must be preserved:

- **Packages:** layer packages (`controller`, `service` + `service.impl`, `dto.request.*`, `dto.response.*`, `exception`, `configuration`, `constants`).
- **Lombok:**
  - DTOs: `@Getter @Setter @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`;
  - services and components: `@Slf4j @Service/@Component @RequiredArgsConstructor @FieldDefaults(level = PRIVATE, makeFinal = true)`;
  - controllers: `private final` fields with `@RequiredArgsConstructor`.
- **Responses:** controllers return `ResponseAPI.<T>builder().code(200).message(...).data(...)`.
- **Errors:** add new error cases to `ErrorCode` (code, message with `%s`, `HttpStatus`) and throw `ApplicationException`.
- **Injection:** constructor injection only.
- **Comments:** none in Java source; documentation lives in Markdown.
- **Imports:** non-`java.*` imports first, a blank line, then `java.*` imports.
- **Constructor parameter order:** `@RequiredArgsConstructor` generates parameters in field-declaration order. Reordering fields changes the constructor, so tests that construct a class directly (for example `EventMonitoringServiceImplTest`) must be updated in the same change.

## IDE setup (IntelliJ IDEA)

1. **File → Open** the repository root (the folder with `pom.xml`) as a Maven project.
2. **Project Structure → Project:** set SDK and language level to 21.
3. **Settings → Build, Execution, Deployment → Compiler → Annotation Processors:** enable annotation processing (required for Lombok).
4. **Maven tool window → +:** add `test-simulator/pom.xml`. It is a separate project, so it is not imported automatically.
5. **Reload All Maven Projects**, then run tests from the gutter icons or with **Run 'All Tests'** on `src/test/java`.

If IDE results differ from Maven, enable **Settings → Build Tools → Maven → Runner → Delegate IDE build/run actions to Maven**.

## Test simulator

Commands, scenario categories, metrics and report formats: [test-simulator/README.md](../test-simulator/README.md).

1. Start the Core from the repository root with the simulator profile:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.config.additional-location=file:./test-simulator/core-config/core-simulator.yml
   ```
2. Run a 100-scenario smoke test from `test-simulator/`:
   ```bash
   ../mvnw spring-boot:run -Dspring-boot.run.arguments="--simulator.scenario-count=100"
   ```

**Neither command has been executed.**

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Startup fails: `ollama.base-url must be an absolute http or https URL` | Fix `OLLAMA_BASE_URL` |
| Startup fails on property validation | See [CONFIGURATION.md](CONFIGURATION.md) for ranges; thresholds need `min` or `max` |
| `./mvnw` cannot download Maven | A proxy or firewall blocks Maven Central; configure a mirror or use a local Maven |
| Every event `REJECTED` by `timestamp-validity` | Clock skew beyond `max-clock-skew`, or `occurredAt` older than `max-event-age` |
| 400 / 1000 | DTO validation; `error` names the first failing field |
| 400 / 2000 | Attribute not string/number/boolean, value too long, or payload too deep |
| 409 / 2001 | Same `source`+`eventId` re-sent with different content |
| 409 / 2002 | Same event still processing; retry later |
| 503 / 2003 | Tracker full of in-flight events; retry, or raise `max-tracked-events` |
| `aiRuntime.state: UNREACHABLE` | Ollama not running or wrong URL; check `curl http://localhost:11434/api/tags` |
| `MODEL_NOT_INSTALLED` / `UNAVAILABLE` "not installed" | `ollama pull <model>` or change `OLLAMA_MODEL` |
| Frequent `TIMEOUT` | Raise `OLLAMA_REQUEST_TIMEOUT`, use a smaller model, lower `max-output-tokens` |
| Frequent `INVALID_RESPONSE` | Check `statusDetail`; ensure Ollama 0.5+; try another model |
| `FAILED` on every AI call | Possibly an Ollama version rejecting `think`; try `OLLAMA_THINK=` (empty). **Unverified.** |
