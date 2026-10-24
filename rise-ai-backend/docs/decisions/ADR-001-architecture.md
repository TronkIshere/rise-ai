# ADR-001 — Modular Monolith

## Context
Small team, Phase 0 scope, strong transactional consistency needed between campaign/milestone state and escrow money.

## Decision
Single Spring Boot 3.3 / Java 21 application with capability packages (`campaign`, `escrow`, `disbursement`, …). Each module: entity, repository, service, controller, one `*Dtos` holder of records. No separate mapper classes; records expose `from(...)` factories. Reuse the uploaded project's conventions (`ResponseAPI`, `PageResponse`, `ApplicationException`/`ErrorCode`, Nimbus JWT, UUID ids).

## Alternatives
Microservices (rejected: distributed transactions for money flows, ops overhead); layered packages by technical type (rejected: weaker module boundaries).

## Rationale
One DB transaction can cover business state + ledger + audit. Clear module packages allow later extraction.

## Consequences
Some package-level coupling (services read other modules' repositories). Error responses changed from the uploaded `ResponseAPI`-for-errors style to `ApiError` with string codes, to give clients stable machine-readable codes.
