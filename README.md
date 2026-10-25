# rise-ai

Rise AI is a charity funding platform built around **milestone-based escrow**. Donors fund campaigns. Funds are held in a simulated escrow and released milestone by milestone, against submitted evidence and under administrator review. An independent AI monitoring service watches the activity and flags patterns for human review.

> **Simulation only.** No real money moves: payments, escrow, releases and refunds are simulated in MySQL. AI monitoring is advisory; it never approves, blocks or releases funds.

## Modules

| Module | What it does | Stack | Docs |
|---|---|---|---|
| [`rise-ai-backend/`](rise-ai-backend/README.md) | Business logic: users and roles, organizations, campaigns, milestones, evidence, donations, refunds, simulated escrow and ledger, disbursements, disputes, settlements, adjustments, audit. Publishes domain events (transactional outbox) and stores advisory findings for admin review | Spring Boot 3.3.5, Java 21, MySQL 8.4, Flyway | [`rise-ai-backend/docs/README.md`](rise-ai-backend/docs/README.md) |
| [`rise-ai-monitoring/`](rise-ai-monitoring/README.md) | Monitoring service: validates events, applies generic and charity-specific deterministic rules, returns explainable findings; optional local Ollama analysis (off for Rise events by default) | Spring Boot 3.5.15, Java 21, embedded H2 | [`rise-ai-monitoring/docs/README.md`](rise-ai-monitoring/docs/README.md) |

Users: **donors** fund campaigns and can request refunds or open disputes; **campaign creators** run organizations, campaigns and milestones and submit evidence and disbursement requests; **admins** review campaigns, evidence and disbursements, release funds, resolve disputes, and review monitoring findings.

## Code structure

Both Spring Boot modules use a layered package structure under their base package: `controller`, `service` / `service/impl`, `repository`, `dto/request` / `dto/response`, `configuration`, `exception`, `mapper`, and in the backend also `entity`, `enums`, `util`.
- **Backend** (`com.riseai`): see [`rise-ai-backend/docs/architecture.md`](rise-ai-backend/docs/architecture.md) for the layer rules and [`rise-ai-backend/docs/development-guide.md`](rise-ai-backend/docs/development-guide.md) for where new code goes.
- **Monitor** (`com.riseai.monitoring`): adds pipeline packages (rules, analyzers, Ollama adapter, Rise charity rules) as documented in [`rise-ai-monitoring/docs/ARCHITECTURE.md`](rise-ai-monitoring/docs/ARCHITECTURE.md).

The two applications stay separate Maven projects; they share no code and no database.

## How the two services work together

```
backend service (transaction) ──▶ outbox_events ──relay──▶ rise-ai-monitoring ──▶ findings[] ──▶ monitoring_findings ──▶ admin review
```
The backend owns every business and financial decision. The monitor sees only minimized events (identifiers, amounts, types, timestamps), has no access to the backend database, and cannot act. Details: [`rise-ai-backend/docs/event-contracts.md`](rise-ai-backend/docs/event-contracts.md) and [`docs/ai-monitoring/02-architecture-and-integration.md`](docs/ai-monitoring/02-architecture-and-integration.md).

## Quick start (local)

Prerequisites: JDK 21 (set `JAVA_HOME` to it), Docker, Maven 3.9+ for the backend (the monitor ships `./mvnw`).

```bash
# 1. Database for the backend
cd rise-ai-backend && docker compose up -d

# 2. Monitoring service on 127.0.0.1:8081 (pick any shared secret)
cd ../rise-ai-monitoring && ./mvnw -DskipTests package
SPRING_PROFILES_ACTIVE=riseai MONITORING_SERVICE_KEY=change-me java -jar target/rise-ai-monitoring.jar

# 3. Backend on :8080 with event delivery enabled (in another terminal)
cd rise-ai-backend
RISE_MONITORING_ENABLED=true RISE_MONITORING_SERVICE_KEY=change-me mvn spring-boot:run
```
Then log in as `admin@riseai.example` / `Demo@12345` (dev only) and open `GET /api/v1/admin/monitoring/findings`. Swagger UI: http://localhost:8080/swagger-ui.html.

The backend also runs on its own (the relay is off by default); events then wait in the outbox.

## Tests
```bash
cd rise-ai-backend && mvn clean test          # needs Docker (Testcontainers MySQL)
cd rise-ai-monitoring && ./mvnw clean test
```

## Documentation
- [`docs/INDEX.md`](docs/INDEX.md): repository-level index.
- [`docs/ai-monitoring/`](docs/ai-monitoring/README.md): monitoring roadmap, requirements, acceptance checklist and [progress report](docs/ai-monitoring/06-progress-report.md).
