# RISE AI Backend — Phases 1 and 2

Charitable fundraising backend with milestone-based **simulated** escrow (Spring Boot 3.3, Java 21, MySQL 8, Flyway), plus the event outbox and advisory findings store for the separate `rise-ai-monitoring` service.

> Escrow is an internal MySQL simulation. No real money, payments or blockchain integrations exist, and this backend makes no AI calls. It publishes minimized domain events to `rise-ai-monitoring` (relay disabled by default) and stores the findings that service returns for admin review. Status and evidence: `docs/phase-1-business-hardening.md`, `docs/phase-2-ai-monitoring.md`.

## Prerequisites
Java 21, Maven 3.9+, Docker (for MySQL and integration tests). There is no Maven wrapper in this module. Make sure `JAVA_HOME` points to a JDK 21; with a JDK 20 `JAVA_HOME`, compilation fails with "release version 21 not supported".

## Run locally
```bash
docker compose up -d                 # MySQL 8.4 on localhost:3306 (rise_ai / rise_dev / rise_dev_password)
mvn spring-boot:run                  # Flyway migrates V1–V9, demo data is seeded
```
- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Demo accounts: `docs/demo-credentials.md` (password `Demo@12345`, dev only)

Without Docker, create database `rise_ai` and a user matching `src/main/resources/application.yml`, and start MySQL with `--log-bin-trust-function-creators=1` (needed for the append-only triggers in `V2`, `V4` and `V9`).

Configuration overrides: `RISE_JWT_SECRET` (≥ 32 bytes), `RISE_DEMO_PASSWORD`, `rise.seed.enabled`, `rise.simulation.donation-confirmation-enabled`, `rise.escrow.dispute-auto-hold`, `rise.jobs.enabled` (scheduled jobs, default `true`), `rise.jobs.reconciliation-cron` (default hourly), `rise.jobs.fundraising-close-cron` (default every 5 minutes). Standard Spring variables such as `SPRING_DATASOURCE_URL` also work.

## AI monitoring (Phase 2)
Events are always written to `outbox_events`. Delivery to the monitoring service is off until you enable it:

| Variable / property | Default | Purpose |
|---|---|---|
| `RISE_MONITORING_ENABLED` (`rise.monitoring.enabled`) | `false` | Starts the relay scheduler |
| `RISE_MONITORING_BASE_URL` | `http://localhost:8081` | Monitoring service URL |
| `RISE_MONITORING_SERVICE_KEY` | empty | Bearer key; must equal the monitor's `MONITORING_SERVICE_KEY` |
| `rise.monitoring.relay-interval`, `batch-size`, `lease`, `connect-timeout`, `read-timeout`, `max-attempts`, `initial-backoff`, `max-backoff`, `published-retention` | `PT5S`, `20`, `PT2M`, `PT2S`, `PT90S`, `12`, `PT5S`, `PT15M`, `P30D` | Relay tuning |

Run both services locally (from the repository root):
```bash
# 1. MySQL (see above), then the monitoring service on 127.0.0.1:8081
cd rise-ai-monitoring && ./mvnw -DskipTests package
SPRING_PROFILES_ACTIVE=riseai MONITORING_SERVICE_KEY=change-me java -jar target/rise-ai-monitoring.jar
# 2. The backend with the relay enabled
cd rise-ai-backend
RISE_MONITORING_ENABLED=true RISE_MONITORING_SERVICE_KEY=change-me mvn spring-boot:run
```
Admin endpoints: `/api/v1/admin/monitoring/findings`, `/findings/{id}/review`, `/outbox/stats`, `/outbox?status=FAILED`, `/outbox/{id}/requeue` (see `docs/api-specification.md`). Either service can start first; business operations never wait for the monitor.

## Tests
```bash
mvn test                               # unit + Testcontainers MySQL integration tests (Docker required)
mvn test -DexcludedGroups=integration  # unit and property tests only
```
Testcontainers 1.21.x is pinned in `pom.xml` because the 1.19.x version that Spring Boot 3.3 manages cannot talk to Docker Engine 29+. The API contract is frozen in `docs/api/openapi-v1.json` and checked by `OpenApiContractTest`; regenerate it deliberately with `mvn test -Dtest=OpenApiContractTest -Dopenapi.snapshot.update=true` and review the diff.

## Layout (layered by technical responsibility, ADR-008)
```
src/main/java/com/riseai/
  RiseAiApplication.java   entry point
  controller/              REST controllers (HTTP only, depend on service interfaces)
  service/                 service interfaces (application operations, EscrowEngine, DomainEventPublisher, OutboxRelay)
  service/impl/            implementations: business rules, transactions, audit and event writes
  repository/              Spring Data JPA repositories
  entity/                  JPA entities
  enums/                   domain enums
  dto/request/             API request records (validation)
  dto/response/            API response records, envelopes, query projections
  dto/event/               internal domain event model
  mapper/                  RiseEvents (entity → domain event)
  client/                  MonitoringClient (HTTP to rise-ai-monitoring)
  configuration/           Spring, security, properties, schedulers, seeder
  security/                AuthUser, JwtService, JwtAuthenticationFilter, SecurityErrorHandler
  exception/               ErrorCode, ApplicationException, GlobalExceptionHandler
  util/                    stateless helpers (Money, Codes, Slugs, States, ProRataAllocation, RequestContext)
src/main/resources/db/migration/   V1–V9 (never edit an applied migration)
src/test/java/com/riseai/          integration/ (scenario tests), support/, architecture/, unit tests next to their class's package
docs/api/openapi-v1.json           frozen API contract
docs/                              start with docs/README.md
```
Layer responsibilities and dependency rules: `docs/architecture.md`. Where to add a new controller, service, repository, entity or DTO: `docs/development-guide.md`.

## Reset demo data
`docker compose down -v && docker compose up -d`, then restart the app.
