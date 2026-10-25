# RISE AI Backend — Documentation Index

Status labels used everywhere: **IMPLEMENTED** (in code), **DESIGNED** (specified, not coded), **PLANNED** (future phase), **NOT IMPLEMENTED** (unavailable).

> Escrow is an **internal MySQL simulation**. No real money, payments or blockchain transactions exist in this codebase, and the backend makes no AI calls: it publishes events to the separate `rise-ai-monitoring` service (Phase 2, relay disabled by default) and stores the advisory findings it returns. Phase 0 was verified manually; Phases 1 and 2 were built and tested with `mvn test` (see the phase documents for evidence).

## Read first (any future agent)
1. `project-overview.md` — goals, users, scope, limitations
2. `architecture.md` — modules, dependency direction, request flow
3. `business-rules.md` + `state-machines.md` — rules that must never be broken
4. `ledger-and-balance-rules.md` — money formulas and invariants
5. `implementation-plan.md` — phase map and what comes next
6. The phase document you are asked to execute

## Index
| Document | Purpose |
|---|---|
| project-overview.md | Product, users, scope, limitations |
| architecture.md | Layered package structure, layer responsibilities and dependency rules, request flow, monitoring integration, escrow boundary |
| development-guide.md | Where to add controllers, services, repositories, entities, DTOs and tests |
| database-design.md | Tables, constraints, indexes, money types |
| erd.mmd | Mermaid ER diagram of the implemented schema |
| api-specification.md | Endpoints, permissions, payloads, error codes |
| roles-and-permissions.md | Role/permission matrix |
| business-rules.md | Non-negotiable rules |
| escrow-lifecycle.md | End-to-end money flow |
| state-machines.md | All states and transitions |
| ledger-and-balance-rules.md | Ledger conventions, formulas, reconciliation |
| security.md | AuthN/AuthZ, hashing, validation, demo limits |
| audit-and-traceability.md | What is audited and how to trace an operation |
| data-initialization.md | Seed data, idempotency, reset |
| testing-strategy.md | Test layout, commands, scenarios |
| implementation-plan.md | Phase dependencies and deliverables |
| phase-0-acceptance-criteria.md | Phase 0 checklist with honest status |
| phase-1-business-hardening.md | Phase 1 scope, progress and verification evidence (IMPLEMENTED) |
| api/openapi-v1.json | Frozen OpenAPI contract (Phase 1, plus additive Phase 2 admin monitoring endpoints), checked by `OpenApiContractTest` |
| phase-2-ai-monitoring.md | AI monitoring integration: scope and status (IMPLEMENTED, MVP) |
| event-contracts.md | Event envelope, catalogue, delivery semantics and finding contract (IMPLEMENTED) |
| decisions/ADR-007-monitoring-integration.md | Integration mechanics decision |
| decisions/ADR-008-layered-package-structure.md | Layered package structure (supersedes ADR-001's packaging) |
| ../../docs/ai-monitoring/ | Cross-module roadmap (M0–M10), acceptance checklist and progress report |
| phase-3-avalanche.md | Future on-chain escrow (PLANNED) |
| phase-4-production-readiness.md | Production hardening (PLANNED) |
| demo-credentials.md | Local demo accounts (DEV ONLY) |
| decisions/ | Architecture Decision Records |
