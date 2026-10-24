# Architecture — IMPLEMENTED

## Style
Modular monolith (ADR-001). One Spring Boot 3.3 / Java 21 application, one MySQL schema managed by Flyway. Packages under `com.riseai` are organized by business capability.

| Package | Responsibility |
|---|---|
| `common` | Base entities, money/slug/code utilities, state guard, correlation-id filter, response envelopes, error handling |
| `config` | `RiseProperties`, OpenAPI, `DemoDataSeeder` |
| `security` | JWT issuing/verification, filter, security config, refresh tokens, auth API |
| `user` | Users, roles (enum element collection), profile and admin user management |
| `organization` | Organizations and the platform-review workflow |
| `campaign` | Campaign lifecycle, visibility rules, statistics |
| `milestone` | Milestones, sequencing, derived status, dispute hold/restore |
| `evidence` | Evidence metadata submission and review |
| `donation` | Simulated donations and admin-controlled confirmation |
| `refund` | Refund request → approval → ledger recording |
| `escrow` | `EscrowEngine` boundary, `SimulatedEscrowEngine`, ledger, reconciliation queries |
| `disbursement` | Request → review → approval → simulated release |
| `dispute` | Disputes, messages, assignment, resolution, hold policy |
| `audit` | Append-only audit log and status history |
| `admin` | System statistics |

Each module contains Entity, Repository, Service, Controller and a `*Dtos` holder of request/response records. Entities never leave the service layer.

## Dependency direction
```
controller → service → (other module services | repositories) → entities
                    ↘ EscrowEngine (interface) → SimulatedEscrowEngine → escrow repositories
all services → AuditService
```
Rules:
- Controllers hold no business logic; they pass the authenticated `AuthUser` and DTOs to services.
- Financial balance changes happen **only** inside `EscrowEngine` implementations. No other class writes `escrow_accounts` or `escrow_ledger_entries`.
- `EscrowEngine` methods use `Propagation.MANDATORY`: they must run inside the caller's business transaction, so ledger, aggregates, entity states and audit rows commit or roll back together.
- Bean dependency order (no cycles): Audit ← Escrow ← Campaign ← Milestone ← Evidence ← Disbursement ← Dispute; Campaign ← Donation; Escrow ← Refund.
- Package-level cycles exist between `security` and `user` (identity cluster) and between `campaign` and read-only repositories of other modules; services do not form cycles.

## Request flow
1. `CorrelationIdFilter` assigns/propagates `X-Correlation-Id` into MDC.
2. `JwtAuthenticationFilter` verifies the bearer token, loads the user from MySQL, rejects non-ACTIVE users and builds `AuthUser` (roles from DB, not from the token).
3. URL rules (`SecurityConfig`) + `@PreAuthorize` enforce coarse role access.
4. Service enforces resource ownership, state machines and financial rules, opening a transaction.
5. Escrow operations lock rows in a fixed order (business entity → milestone → escrow account), append ledger entries and update aggregates.
6. `AuditService` writes status history and audit rows in the same transaction.
7. `GlobalExceptionHandler` maps failures to `ApiError` without internals.

## Concurrency control
Pessimistic `SELECT … FOR UPDATE` on the business row, milestone and escrow account; `@Version` optimistic locking on mutable entities; unique `idempotency_key` on ledger entries; DB CHECK constraints on balances and milestone amounts; DB triggers forbidding UPDATE/DELETE on ledger, audit and status-history tables.

## Replaceable escrow boundary
`escrow/EscrowEngine` is the only abstraction introduced for future use (ADR-003). Phase 3 adds an Avalanche-backed implementation behind it; see `phase-3-avalanche.md`.
