# ADR-007 — AI Monitoring Integration Mechanics (Phase 2)

> **Status update 2026-10-25:** `FindingBoundaryTest` was replaced by `architecture/LayeredArchitectureTest` during the layered refactor (ADR-008); class paths below are pre-refactor.

## Context
ADR-004 fixed the boundary: versioned events through a transactional outbox, advisory AI output only, no AI access to business data. Phase 2 had to choose the transport, the return path for findings, where human review lives, and how the monitor gets enough history for cross-event charity patterns. The repository already contained a monitoring service (renamed to `rise-ai-monitoring`) that accepts events over HTTP and has no backend access.

## Decision
1. **Transport:** transactional outbox (`outbox_events`, V8) plus a backend-owned relay that POSTs each event to the monitor's existing `POST /api/v1/events/monitor`. No message broker.
2. **Claiming:** `FOR UPDATE SKIP LOCKED` with a lease in a short transaction; HTTP outside any transaction; outcome in a new transaction.
3. **Return path:** the monitor returns `finding.v1` findings synchronously; the relay stores them (V9) in the same transaction that marks the event PUBLISHED. The monitor has no backend credentials and never calls the backend.
4. **Human review in the backend:** ADMIN-only endpoints under `/api/v1/admin/monitoring/**`, reusing JWT auth and `AuditService`. The monitor never receives the HMAC JWT secret.
5. **Service authentication:** a shared static key sent as a Bearer token; the monitor requires it in its `riseai` profile.
6. **History for cross-event rules** lives in the monitor's own minimized projection store, never in the backend schema.
7. **Findings are deterministic, categorical and advisory:** fixed severity per rule, no numeric risk score, no field that a workflow could consume as a decision. A source-scan test (`FindingBoundaryTest`) keeps business packages independent of findings.

## Alternatives
- Message broker (Kafka/RabbitMQ): more infrastructure for one producer and one consumer; deferred.
- Monitor polling a backend event feed: inverts the dependency and needs backend credentials in the monitor; not needed.
- Monitor pushing findings to an authenticated backend ingestion API: needed later for time-based rules (no triggering event); not needed for the event-driven MVP rules.
- Review UI in the monitor: would require sharing the JWT secret or a second identity system.

## Rationale
Reuses the existing HTTP contract and scheduler infrastructure, keeps every financial decision in the backend, gives at-least-once delivery without dual writes, and keeps the business database private.

## Consequences
- Delivery is synchronous per event; the relay read timeout (90 s) must exceed the monitor's AI timeout when AI is enabled.
- Time-based findings (overdue milestones, missing reports) need a push path; not implemented.
- The OpenAPI snapshot gained the admin monitoring endpoints (additive).
- With the relay disabled (default), events accumulate as PENDING; published rows are purged after 30 days only when the relay is enabled.
