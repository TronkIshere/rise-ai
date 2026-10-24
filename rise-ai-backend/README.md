# RISE AI Backend — Phase 1

Charitable fundraising backend with milestone-based **simulated** escrow (Spring Boot 3.3, Java 21, MySQL 8, Flyway).

> Escrow is an internal MySQL simulation. No real money, payments, blockchain or AI integrations exist. Phase 1 status, verification evidence and open items: `docs/phase-1-business-hardening.md`.

## Prerequisites
Java 21, Maven 3.9+, Docker (for MySQL and integration tests).

## Run locally
```bash
docker compose up -d                 # MySQL 8.4 on localhost:3306 (rise_ai / rise_dev / rise_dev_password)
mvn spring-boot:run                  # Flyway migrates, demo data is seeded
```
- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Demo accounts: `docs/demo-credentials.md` (password `Demo@12345`, dev only)

Without Docker, create database `rise_ai` and a user matching `src/main/resources/application.yml`, and start MySQL with `--log-bin-trust-function-creators=1` (needed for the append-only triggers in `V2`).

Configuration overrides: `RISE_JWT_SECRET` (≥ 32 bytes), `RISE_DEMO_PASSWORD`, `rise.seed.enabled`, `rise.simulation.donation-confirmation-enabled`, `rise.escrow.dispute-auto-hold`, `rise.jobs.enabled` (scheduled jobs, default `true`), `rise.jobs.reconciliation-cron` (default hourly), `rise.jobs.fundraising-close-cron` (default every 5 minutes).

## Tests
```bash
mvn test                               # unit + Testcontainers MySQL integration tests (Docker required)
mvn test -DexcludedGroups=integration  # unit and property tests only
```
Testcontainers 1.21.x is pinned in `pom.xml` because the 1.19.x version that Spring Boot 3.3 manages cannot talk to Docker Engine 29+. The API contract is frozen in `docs/api/openapi-v1.json` and checked by `OpenApiContractTest`.

## Layout
```
src/main/java/com/riseai/   common, config, security, user, organization, campaign, milestone,
                            evidence, donation, refund, escrow, disbursement, dispute, audit, admin,
                            settlement, adjustment
src/main/resources/db/migration/   V1–V7 (never edit an applied migration)
docs/api/openapi-v1.json           frozen API contract
src/test/java/com/riseai/          unit and integration tests
docs/                              start with docs/README.md
```

## Reset demo data
`docker compose down -v && docker compose up -d`, then restart the app.
