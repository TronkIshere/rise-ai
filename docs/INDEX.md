# Documentation Index

Repository-level index. Each module keeps its own documentation: `rise-ai-backend/docs/README.md` (backend index and reading order) and `rise-ai-monitoring/docs/README.md` (monitoring service). Cross-module material lives in `docs/ai-monitoring/`.

Status labels used by the backend docs: **IMPLEMENTED** (in code), **DESIGNED** (specified, not coded), **PLANNED** (future phase), **NOT IMPLEMENTED**.

> Phase 0 was verified manually by the developer. Phase 1 (business hardening) is implemented and verified. Phase 2 (AI monitoring integration) is implemented as an MVP and verified end to end on 2026-10-25; see `docs/ai-monitoring/06-progress-report.md`. Phase 3 (Avalanche) is implemented as an opt-in engine (`rise.escrow.engine=avalanche`) and was run end to end on the Fuji testnet on 2026-11-10; see `rise-ai-avalanche/README.md`. Phase 4 is partially implemented. The frontend (`rise-ai-frontend/`, stages FE-00 to FE-06) is merged; see `rise-ai-frontend/docs/PROGRESS.md`.

## Repository layout
| Path | Content |
|---|---|
| `README.md` | Project overview, module map, quick start |
| `rise-ai-frontend/` | React 19 / Vite 6 / TypeScript web UI (FE-00 to FE-06 merged). Agent rules: `rise-ai-frontend/AGENTS.md`; progress: `rise-ai-frontend/docs/PROGRESS.md` |
| `rise-ai-avalanche/` | Avalanche gateway (Spring Boot 3.5.16, port 8082) and Foundry contracts (`CampaignEscrow`, `RiseVND`, `RiseAnchor`); local Anvil or Fuji testnet only. See `rise-ai-avalanche/README.md` |
| `docs/design/` | Owner-approved UI design baselines and guardrails (read before any frontend redesign) |
| `docs/frontend/` | Pointer to the frontend docs |
| `docker-compose.yml`, `docs/local-docker-stack.md` | Whole app on one machine (MySQL, monitoring, backend, frontend, local Avalanche chain and gateway); local testing only |
| `rise-ai-backend/` | Spring Boot 3.3.5 / Java 21 / Maven backend |
| `rise-ai-monitoring/` | AI monitoring service (Spring Boot 3.5.15 / Java 21, `com.riseai.monitoring`, port 8081), renamed from `local-ai-event-monitor` on 2026-10-25. Docs index: `rise-ai-monitoring/docs/README.md` |
| `docs/ai-monitoring/` | Cross-module monitoring roadmap, requirements, acceptance checklist and progress report |
| `rise-ai-backend/src/main/java/com/riseai/` | Layered packages (ADR-008): controller, service, service/impl, repository, entity, enums, dto/{request,response,event}, mapper, client, configuration, security, exception, util |
| `rise-ai-backend/src/main/resources/db/migration/` | Flyway V1–V13 (V8 outbox, V9 monitoring findings, V10 alert notifications, V11 AI notes, V12 chain commands, V13 chain anchors) |
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
| [docs/architecture.md](../rise-ai-backend/docs/architecture.md) | Layered package structure, layer responsibilities and dependency rules, request flow, concurrency, monitoring integration |
| [docs/development-guide.md](../rise-ai-backend/docs/development-guide.md) | Where to add controllers, services, repositories, entities, DTOs and tests |
| [docs/decisions/README.md](../rise-ai-backend/docs/decisions/README.md) | ADR index |
| [ADR-001](../rise-ai-backend/docs/decisions/ADR-001-architecture.md) | Modular monolith |
| [ADR-002](../rise-ai-backend/docs/decisions/ADR-002-database-and-ledger.md) | MySQL, UUID keys, enum roles, append-only ledger |
| [ADR-003](../rise-ai-backend/docs/decisions/ADR-003-escrow-abstraction.md) | `EscrowEngine` abstraction |
| [ADR-004](../rise-ai-backend/docs/decisions/ADR-004-ai-event-boundary.md) | AI monitoring event boundary (implemented in Phase 2) |
| [ADR-007](../rise-ai-backend/docs/decisions/ADR-007-monitoring-integration.md) | Monitoring integration mechanics (Phase 2) |
| [ADR-008](../rise-ai-backend/docs/decisions/ADR-008-layered-package-structure.md) | Layered package structure |
| [ADR-005](../rise-ai-backend/docs/decisions/ADR-005-avalanche-integration-boundary.md) | Avalanche integration boundary |
| [ADR-006](../rise-ai-backend/docs/decisions/ADR-006-settlement-and-dual-control.md) | Settlement policy and dual-controlled ledger changes (Phase 1) |
| [ADR-009](../rise-ai-backend/docs/decisions/ADR-009-fuji-testnet-signers.md) | Operator-held keystore signers on Fuji (testnet exception to ADR-005) |

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
| [docs/event-contracts.md](../rise-ai-backend/docs/event-contracts.md) | Event envelope, 20-event catalogue, delivery semantics, finding contract (IMPLEMENTED) |

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
| [docs/phase-0-acceptance-criteria.md](../rise-ai-backend/docs/phase-0-acceptance-criteria.md) | Phase 0 checklist, all 20 rows ticked with evidence (2026-10-30) |
| [docs/phase-1-business-hardening.md](../rise-ai-backend/docs/phase-1-business-hardening.md) | IMPLEMENTED and verified; progress table and closure |
| [docs/phase-2-ai-monitoring.md](../rise-ai-backend/docs/phase-2-ai-monitoring.md) | IMPLEMENTED (MVP) |
| [docs/phase-3-avalanche.md](../rise-ai-backend/docs/phase-3-avalanche.md) | IMPLEMENTED (opt-in, Fuji testnet only); see its status section |
| [docs/phase-4-production-readiness.md](../rise-ai-backend/docs/phase-4-production-readiness.md) | PARTIALLY IMPLEMENTED |

## AI monitoring roadmap and status
| Document | Purpose |
|---|---|
| [ai-monitoring/README.md](ai-monitoring/README.md) | Reading order, status labels, current state, decision register D1–D14 |
| [ai-monitoring/01-module-rename-plan.md](ai-monitoring/01-module-rename-plan.md) | `local-ai-event-monitor` → `rise-ai-monitoring`: naming layers, change order, verification, rollback |
| [ai-monitoring/02-architecture-and-integration.md](ai-monitoring/02-architecture-and-integration.md) | Verified state, target architecture, integration alternatives, envelope and finding contracts, delivery, security |
| [ai-monitoring/03-charity-monitoring-requirements.md](ai-monitoring/03-charity-monitoring-requirements.md) | Charity domain mapping, deterministic rules, AI capabilities, human review, evaluation |
| [ai-monitoring/04-roadmap.md](ai-monitoring/04-roadmap.md) | Sub-phases M0–M10 of backend Phase 2 |
| [ai-monitoring/05-acceptance-checklist.md](ai-monitoring/05-acceptance-checklist.md) | Acceptance criteria with evidence (MVP items ticked) |
| [ai-monitoring/06-progress-report.md](ai-monitoring/06-progress-report.md) | What was implemented, changed files, verification results, gaps, next steps |
| [ai-monitoring/07-operations-runbook.md](ai-monitoring/07-operations-runbook.md) | Monitor down, FAILED outbox rows, key rotation, CRITICAL findings (drilled once on Compose, 2026-10-29) |
| [ai-monitoring/08-d12-threshold-proposal.md](ai-monitoring/08-d12-threshold-proposal.md) | D12 threshold proposal for owner review (not active) |
| [ai-monitoring/09-rule-audit.md](ai-monitoring/09-rule-audit.md) | Line-by-line audit of the eight Rise rules, defects fixed and residual limitations |

## Pending owner decisions
[decisions-pending.md](decisions-pending.md): default proposals (D12 thresholds, AI notes, alert destination, retention, admin 2FA, contract security review). Status: PROPOSED, awaiting project owner confirmation.

## Known documentation gaps
These were found during repository discovery on 2026-10-25. The `METHOD_NOT_ALLOWED` and `/actuator/info` omissions were fixed in Phase 1.
- (Resolved 2026-10-25) The root `README.md` now contains setup instructions.
- `security.md` describes role checks as `@PreAuthorize` on controllers. Some GET routes fall under the public `PUBLIC_READ_ENDPOINTS` URL prefixes but need authentication, and they enforce it only in the service layer (`AuthUser.require`, `loadOwnerOrAdmin`): `/campaigns/mine`, `/organizations/mine`, `/campaigns/{id}/donations/manage` and `/campaigns/{id}/disputes`.
- There is no `CLAUDE.md` or backend Maven wrapper. Dockerfiles, a root `docker-compose.yml` and `.github/workflows/ci.yml` were added on 2026-10-29 (Compose verified). The CI workflows have since run on GitHub (for example run 42773726026 on `4873c60`). The monitoring module has `./mvnw`.
