# Documentation Index

Repository-level index. The original documents live under `rise-ai-backend/` and are unchanged; `rise-ai-backend/docs/README.md` is the backend's own index and reading order.

Status labels used by the backend docs: **IMPLEMENTED** (in code), **DESIGNED** (specified, not coded), **PLANNED** (future phase), **NOT IMPLEMENTED**.

> Phase 0 was verified manually by the developer. Phase 1 (business hardening) is implemented and tested; status and evidence are in `rise-ai-backend/docs/phase-1-business-hardening.md` › Progress.

## Repository layout
| Path | Content |
|---|---|
| `README.md` | Title only (`# rise-ai`) |
| `rise-ai-backend/` | Spring Boot 3.3.5 / Java 21 / Maven backend, the only module in the repository |
| `rise-ai-backend/src/main/java/com/riseai/` | Capability packages: common, config, security, user, organization, campaign, milestone, evidence, donation, refund, escrow, disbursement, dispute, audit, admin, settlement, adjustment |
| `rise-ai-backend/src/main/resources/db/migration/` | Flyway V1–V7 |
| `rise-ai-backend/src/test/java/com/riseai/` | Unit, jqwik property, and Testcontainers MySQL integration tests |
| `rise-ai-backend/docs/api/openapi-v1.json` | Frozen OpenAPI contract, checked by `OpenApiContractTest` |
| `rise-ai-backend/docker-compose.yml` | Local MySQL 8.4 only (no application container) |

## Getting started
| Document | Purpose |
|---|---|
| [rise-ai-backend/README.md](../rise-ai-backend/README.md) | Prerequisites, run locally, tests, layout, reset |
| [docs/README.md](../rise-ai-backend/docs/README.md) | Backend documentation index and reading order |
| [docs/demo-credentials.md](../rise-ai-backend/docs/demo-credentials.md) | Seeded demo accounts (development only) |
| [docs/data-initialization.md](../rise-ai-backend/docs/data-initialization.md) | Demo seeder behaviour, idempotency, safe reset |

## Product and architecture
| Document | Purpose |
|---|---|
| [docs/project-overview.md](../rise-ai-backend/docs/project-overview.md) | Product, roles, Phase 0 scope, limitations |
| [docs/architecture.md](../rise-ai-backend/docs/architecture.md) | Modular monolith, package responsibilities, dependency direction, request flow, concurrency |
| [docs/decisions/README.md](../rise-ai-backend/docs/decisions/README.md) | ADR index |
| [ADR-001](../rise-ai-backend/docs/decisions/ADR-001-architecture.md) | Modular monolith |
| [ADR-002](../rise-ai-backend/docs/decisions/ADR-002-database-and-ledger.md) | MySQL, UUID keys, enum roles, append-only ledger |
| [ADR-003](../rise-ai-backend/docs/decisions/ADR-003-escrow-abstraction.md) | `EscrowEngine` abstraction |
| [ADR-004](../rise-ai-backend/docs/decisions/ADR-004-ai-event-boundary.md) | AI monitoring event boundary (design only) |
| [ADR-005](../rise-ai-backend/docs/decisions/ADR-005-avalanche-integration-boundary.md) | Avalanche integration boundary (design only) |
| [ADR-006](../rise-ai-backend/docs/decisions/ADR-006-settlement-and-dual-control.md) | Settlement policy and dual-controlled ledger changes (Phase 1) |

## API, data and domain rules
| Document | Purpose |
|---|---|
| [docs/api-specification.md](../rise-ai-backend/docs/api-specification.md) | Endpoints under `/api/v1`, envelopes, error codes |
| [docs/api/openapi-v1.json](../rise-ai-backend/docs/api/openapi-v1.json) | Frozen OpenAPI 1.0.0 snapshot |
| [docs/database-design.md](../rise-ai-backend/docs/database-design.md) | Tables, constraints, conventions, migrations |
| [docs/erd.mmd](../rise-ai-backend/docs/erd.mmd) | Mermaid ER diagram (text source) |
| [docs/roles-and-permissions.md](../rise-ai-backend/docs/roles-and-permissions.md) | Role and permission matrix |
| [docs/business-rules.md](../rise-ai-backend/docs/business-rules.md) | Numbered business rules (1–43) |
| [docs/state-machines.md](../rise-ai-backend/docs/state-machines.md) | State transitions for every entity |
| [docs/escrow-lifecycle.md](../rise-ai-backend/docs/escrow-lifecycle.md) | End-to-end simulated money flow |
| [docs/ledger-and-balance-rules.md](../rise-ai-backend/docs/ledger-and-balance-rules.md) | Ledger types, formulas, locking, reconciliation |
| [docs/event-contracts.md](../rise-ai-backend/docs/event-contracts.md) | Future event envelope and payloads (DESIGNED, not emitted) |

## Cross-cutting
| Document | Purpose |
|---|---|
| [docs/security.md](../rise-ai-backend/docs/security.md) | JWT, refresh tokens, authorization, validation, demo limits |
| [docs/audit-and-traceability.md](../rise-ai-backend/docs/audit-and-traceability.md) | Audit stores, audited actions, tracing by correlation id |
| [docs/testing-strategy.md](../rise-ai-backend/docs/testing-strategy.md) | Test files, commands, conventions, gaps |

## Delivery phases
| Document | Status |
|---|---|
| [docs/implementation-plan.md](../rise-ai-backend/docs/implementation-plan.md) | Phase map and next action |
| [docs/phase-0-acceptance-criteria.md](../rise-ai-backend/docs/phase-0-acceptance-criteria.md) | Phase 0 checklist, nothing verified yet |
| [docs/phase-1-business-hardening.md](../rise-ai-backend/docs/phase-1-business-hardening.md) | IMPLEMENTED; progress table with evidence |
| [docs/phase-2-ai-monitoring.md](../rise-ai-backend/docs/phase-2-ai-monitoring.md) | PLANNED |
| [docs/phase-3-avalanche.md](../rise-ai-backend/docs/phase-3-avalanche.md) | PLANNED |
| [docs/phase-4-production-readiness.md](../rise-ai-backend/docs/phase-4-production-readiness.md) | PLANNED |

## Known documentation gaps
These were found during repository discovery on 2026-10-25. The `METHOD_NOT_ALLOWED` and `/actuator/info` omissions were fixed in Phase 1.
- The root `README.md` contains only a title, but `phase-0-acceptance-criteria.md` #19 points to it for setup instructions. The setup instructions are in `rise-ai-backend/README.md`.
- `security.md` describes role checks as `@PreAuthorize` on controllers. Some GET routes fall under the public `PUBLIC_READ_ENDPOINTS` URL prefixes but need authentication, and they enforce it only in the service layer (`AuthUser.require`, `loadOwnerOrAdmin`): `/campaigns/mine`, `/organizations/mine`, `/campaigns/{id}/donations/manage` and `/campaigns/{id}/disputes`.
- There is no `CLAUDE.md`, CI configuration, Maven wrapper (`mvnw`) or application Dockerfile, and no document explains their absence.
