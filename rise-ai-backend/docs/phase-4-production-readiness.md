# Phase 4 — Production Readiness (PARTIALLY IMPLEMENTED; external operational work open)

## Prerequisites
Phases 1–3 as applicable; legal review of fundraising and payment regulations in target jurisdictions.

## Security
Secrets from a vault (no defaults in `application.yml` for prod profile); rotate JWT secret (consider RS256 with key ids); MFA for admins; account lockout and rate limiting on auth; HTTPS only with HSTS; security headers; dependency and container scanning; penetration test; real email verification; separation of duties for high-value approvals (two admins).

## Observability
Structured JSON logs with correlation id; Micrometer metrics (request latency, error rates, escrow operations, reconciliation mismatches, lock wait times); tracing (OpenTelemetry); alerting on reconciliation failures and outbox backlog.

## Data
Managed MySQL with PITR backups, tested restores, encryption at rest; read replicas for public read endpoints; retention policy for audit logs; GDPR/PDPD-style data subject requests (pseudonymize users without touching ledger integrity).

## Migrations & deployment
Flyway in CI against a production-like snapshot; expand/contract migrations for zero downtime; containerized deployment with health/readiness probes (`/actuator/health`); blue/green or rolling releases; seed disabled (`rise.seed.enabled=false`) and simulation endpoint disabled (`rise.simulation.donation-confirmation-enabled=false`).

## Rate limiting
Per-IP and per-user limits on auth, donation creation and dispute opening; idempotency keys required on financial POSTs.

## Operations
Runbooks: reconciliation mismatch, stuck disbursement, dispute escalation, key compromise; on-call; admin action review reports from audit logs.

## Acceptance criteria
Security review passed; restore drill passed; load test at target traffic with no invariant violations; runbooks reviewed.

## Status of the non-blockchain scope (2026-10-30)

Status values: IMPLEMENTED = code present; VERIFIED = exercised by a named automated test (unit or integration) and, where the row says so, by a local Docker Compose run; OPEN = needs external infrastructure, credentials, an owner decision or a real-world drill. No row is "production-ready"; no row has been verified in a deployed environment. Nothing here claims a deployed environment, a penetration test or a production backup.

| Area | Item | Status | Evidence / what remains |
|---|---|---|---|
| Security | Security headers (nosniff, frame deny, HSTS, referrer, CSP `frame-ancestors`, Permissions-Policy) | VERIFIED | `HardeningIntegrationTest.responsesCarryHardenedSecurityHeaders`; headers seen on the live Compose backend. HSTS is only emitted over HTTPS |
| Security | Rate limiting: per client IP, fixed window; 20 auth requests and 300 other mutating requests per minute by default (`rise.hardening.*`) | VERIFIED (single instance) | `HardeningIntegrationTest`, `FixedWindowRateLimiterTest` (4); live: 429 from the 20th-plus request. State is in memory, so it is per instance. `X-Forwarded-For` is deliberately not trusted (24 requests with 24 different spoofed values were still limited on the live stack). The consequence: behind a reverse proxy every client shares the proxy's address and therefore one 20-per-minute auth bucket, so a proxied deployment must either configure a trusted forwarded-header strategy for that proxy or raise the limits; that is a deployment decision and is open. Per-account lockout is not implemented |
| Security | Request size limit (1 MB default, `rise.hardening.max-request-body`; chunked bodies counted) | VERIFIED | Declared length: `HardeningIntegrationTest.oversizedRequestBodyIsRejectedBeforeProcessing`. Chunked: `RequestSizeLimitFilterTest` (4) and `GlobalExceptionHandlerTest` (3); live Compose run 2026-10-30: a 3 MB chunked login body returned 413 `REQUEST_TOO_LARGE`. The post-sprint review found it returned 400 `INVALID_REQUEST` before the handler fix (the message converter wrapped the exception); the read was bounded either way |
| Security | Prod profile refuses the development JWT secret, the development demo password when seeding, and a missing monitoring key, and seeding enabled while the simulation path is disabled | VERIFIED | `ProductionSecretsValidatorTest` (5); live Compose run: startup stopped with the explicit messages for the default demo password and for seed-without-simulation |
| Security | Secrets integration point: Docker/Kubernetes secrets mounted at `/run/secrets` are read through `spring.config.import=optional:configtree:/run/secrets/` in the `prod` profile | IMPLEMENTED | Property-level test only (`ProductionProfileConfigurationTest`). No secret manager (Vault or cloud) is integrated or deployed. A file named `RISE_JWT_SECRET` in that directory satisfies `${RISE_JWT_SECRET}`; this was not exercised with real mounted files |
| Security | TLS | IMPLEMENTED, not exercised | `application-tls.yml` (profile `tls`; `RISE_TLS_KEYSTORE`, `RISE_TLS_KEYSTORE_PASSWORD`). No certificate was available, so TLS startup was not run. Alternative: terminate TLS at a reverse proxy |
| Security | Database connection security | OPEN | Compose uses `useSSL=false` on the internal network. A production URL must use `sslMode=VERIFY_CA` or `VERIFY_IDENTITY` with a managed server certificate |
| Security | Admin MFA | OPEN | No integration point exists in code. Needs a product decision (TOTP or an identity provider) |
| Security | Real email verification, penetration test, two-admin approval for high-value actions beyond existing dual control (ADR-006) | OPEN | Not started; the penetration test requires an external party |
| Observability | Liveness and readiness probes (`/actuator/health/liveness`, `/readiness`), public, no details | VERIFIED | `HardeningIntegrationTest.probesArePublicButMetricsRequireAnAdministrator`; Compose healthcheck uses readiness |
| Observability | `/actuator/metrics` exposed to ADMIN only | VERIFIED | Same test; anonymous 401 on the live stack. No Prometheus registry is added; the monitor's metrics are separate |
| Observability | Alert backlog visibility and manual requeue of FAILED alerts: `GET /api/v1/admin/monitoring/alerts`, `/alerts/stats`, `POST /alerts/{id}/requeue` (audited, ADMIN only) | VERIFIED | `AlertAdminIntegrationTest` (5). FAILED alerts are deliberately never retried automatically; an administrator decides |
| Observability | Structured JSON logs, OpenTelemetry tracing, alert rules on backlog | OPEN | Logs are plain text with a correlation id |
| Data | Backup and restore scripts `scripts/backup-mysql.sh`, `scripts/restore-mysql.sh` | VERIFIED once, locally | 2026-10-30: dump of a seeded Compose database (46 KB gzip), checksum verified, restored into a second database in the same MySQL container: 24 tables, 11 Flyway versions, ledger entry and user counts equal to the source; overwriting the source database is refused without `RISE_CONFIRM_OVERWRITE=yes`. This is a logical dump on one host. It is **not** a point-in-time-recovery backup, not encrypted, not off-host, and no scheduled job or retention exists, so the criterion "restore drill passed" is met only for this local mechanism |
| Data | Managed MySQL, PITR, encryption at rest, retention policy, data-subject requests | OPEN | External infrastructure and owner decisions |
| Deployment | Compose: `restart: unless-stopped`, graceful stop period, `cap_drop: ALL`, `no-new-privileges`, backend readiness healthcheck | VERIFIED locally | Stack built and run on 2026-10-30 (twice); backend reported `healthy` under those options; `docker stop` completed in 2 s with the log line "Graceful shutdown complete" (exit 143, the JVM's SIGTERM status). Compose is a local deployment shape, not a production platform |
| Deployment | Production-like start with seeding off, a secrets manager, and an orchestrator | OPEN | Not performed |
| Testing | Load-test script `scripts/load_test.py` (read paths; error rate and p95 gate) | VERIFIED locally | 1,000 requests, concurrency 20, warm: 0 errors, p95 0.13–0.15 s, about 220 requests/s; the first cold run had p95 2.8 s and missed the 1 s gate. Read paths only, single laptop, not the target traffic profile; financial-write load with invariant checks is covered only by `ConcurrencyIntegrationTest` |
| Security | Dependency and container vulnerability scanning | OPEN | No scanner configuration was added or run |
| Operations | Runbooks | PARTIAL | `docs/ai-monitoring/07-operations-runbook.md` covers monitor, outbox, alerts, backup and restore; reconciliation mismatch, stuck disbursement and key compromise runbooks are not written; none has been reviewed |
