# Phase 2 — AI Monitoring Integration (IMPLEMENTED as MVP, 2026-10-25)

**Status.** The MVP is implemented and verified end to end: transactional outbox (V8), relay with retry and dead-lettering, 20 event types, findings storage and admin review (V9), and eight deterministic charity rules in `rise-ai-monitoring`. Evidence: `../../docs/ai-monitoring/06-progress-report.md`. Contract: `event-contracts.md`. Decisions: ADR-004, ADR-007. Not implemented (see the roadmap): AI-assisted analysis adapted to charity data (M8), time-based rules, key rotation/mTLS, alert delivery, Dockerfiles/CI (Phase 4).

The original design below is kept for reference; where it differs from the implementation, `event-contracts.md` and ADR-007 are canonical.

> **Detailed plan (2026-10-25):** `docs/ai-monitoring/` at the repository root breaks this phase into sub-phases M0–M10, with contracts, decisions requiring approval, and an acceptance checklist. It keeps every boundary in this document (outbox, at-least-once delivery, advisory-only AI, no AI writes to business tables). Where they differ, the detailed plan refines this document. Main refinements: findings are returned in the monitor's synchronous response and stored by the backend relay (instead of an ingestion API) for the MVP; the relay uses claim-and-lease rather than holding `FOR UPDATE SKIP LOCKED` locks across the HTTP call; the envelope gains optional `actorRole` and `references`.

## Inputs & prerequisites
Phase 1 complete. Read `README.md`, `architecture.md`, `event-contracts.md`, ADR-004.

## Objectives
Publish versioned business events from the core backend to an **independent** AI Monitoring module that produces risk signals for human reviewers.

## Design
1. **Transactional outbox.** New table `outbox_events(id, event_id UNIQUE, event_type, schema_version, aggregate_type, aggregate_id, payload JSON, occurred_at, status, attempts, next_attempt_at, last_error, published_at)`. Services insert outbox rows in the same transaction as the business change (good insertion point: next to `AuditService.transition` calls). Rollback ⇒ no event; commit ⇒ event guaranteed.
2. **Relay.** A scheduled publisher reads `PENDING` rows with `FOR UPDATE SKIP LOCKED`, sends them to the broker/HTTP endpoint, marks `PUBLISHED`; failures increment `attempts` with exponential backoff; after N attempts → `FAILED` and alert.
3. **Delivery semantics.** At-least-once. Consumers deduplicate by `eventId`.
4. **Schema evolution.** `schemaVersion` per event type; additive changes only within a major version; breaking changes create a new version published in parallel during migration.
5. **Observability.** Metrics: outbox backlog, publish latency, failures; correlation id copied into the envelope.
6. **Unavailability.** Core operations never wait for AI. If monitoring is down, events accumulate in the outbox. Business policy option (to be decided): require human review for releases above a threshold when no risk assessment exists — implemented as a deterministic rule, not an AI decision.

## Risk signals (consumed back, optional)
AI Monitoring may return `risk_assessments(id, aggregate_type, aggregate_id, score, level, explanation, references JSON, model_or_rule_version, created_at)` through an authenticated ingestion API. Assessments are **advisory**: displayed to admins, attached to review screens.

## AI permission boundary
AI must not: write business tables, update balances, append ledger entries, approve/reject/release disbursements, resolve disputes, hold keys, or bypass deterministic rules. It has no DB credentials for the core schema.

## Allowed changes
New outbox table/migration, publisher component, event DTOs, risk ingestion endpoint (ADMIN-visible).

## Must not change
Escrow engine semantics, ledger, state machines, authorization rules.

## Sequence
outbox schema → event builders per service → relay with retry → contract tests → risk ingestion (optional) → admin UI fields.

## Acceptance criteria
- Every event in `event-contracts.md` is emitted exactly once per committed operation and never for rolled-back ones (integration test).
- Relay retries and dead-letters; duplicates are harmless.
- Core endpoints behave identically with the monitoring endpoint down.
