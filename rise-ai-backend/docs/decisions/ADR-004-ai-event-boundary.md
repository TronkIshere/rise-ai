# ADR-004 — AI Monitoring Event Boundary

> **Status update 2026-10-25:** implemented in Phase 2. Transport, return path and review placement are recorded in ADR-007.

## Context
An independent AI Monitoring module may analyse activity. It must never become a dependency of financial decisions.

## Decision
Phase 2 integrates through versioned events (`event-contracts.md`) published via a transactional outbox. AI returns advisory risk assessments only. No AI SDKs, brokers or calls in Phase 0.

## Alternatives
Direct DB read access for AI (rejected: coupling, privacy); synchronous AI checks in approval flows (rejected: availability and determinism).

## Rationale
At-least-once delivery without dual-write problems; core keeps working when AI is down.

## Consequences
Phase 2 adds an outbox table and relay. Audit actions already align with event types, simplifying the mapping.
