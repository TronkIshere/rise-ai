# Test Simulator

`test-simulator/` is a **separate** Maven project, not a Maven module of the Core. The Core's `pom.xml` does not reference it. It tests a **running** Core through its REST API and scores the responses against expected outcomes defined in advance.

**Full documentation:** [test-simulator/README.md](../test-simulator/README.md).

## Relationship to the Core

| Aspect | Detail |
|---|---|
| Endpoints used | `POST /api/v1/events/monitor`, plus `GET /actuator/health`, `/api/v1/monitoring/status` and `/api/v1/monitoring/rules` for preflight |
| Required Core configuration | [`test-simulator/core-config/core-simulator.yml`](../test-simulator/core-config/core-simulator.yml): two attribute thresholds, an event-type allow-list, and an Ollama timeout below the simulator's |
| Source of expected outcomes | The Core's documented contract ([API_CONTRACT.md](API_CONTRACT.md), [EVENT_LIFECYCLE.md](EVENT_LIFECYCLE.md), [AI_AND_OLLAMA.md](AI_AND_OLLAMA.md)) and that profile, recorded in `test-simulator/.../scenario/CoreContract.java` |
| Core code changes | None. The simulator never calls Core classes. |

## Keeping it in sync

**If you change any of the following in the Core, update `CoreContract.java` and the affected templates in `ScenarioGenerator.java` in the same change:**

- a rule ID or threshold operator;
- a default limit;
- an error code or HTTP status;
- `monitoringStatus`, risk or review semantics;
- the deduplication behaviour.

Otherwise simulator results will report contract drift as Core failures.

## Status

Recorded 2026-10-24.

| Item | Status |
|---|---|
| Unit tests (38) | Pass |
| Maven build | Not run |
| Run against the real Core | **Not performed** |

Record real run results in [TESTING.md](TESTING.md) once they exist.
