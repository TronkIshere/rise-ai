# Phase 4 — Production Readiness (PLANNED)

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
