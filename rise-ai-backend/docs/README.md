# RISE AI Backend — Documentation Index

Status labels used everywhere: **IMPLEMENTED** (in code), **DESIGNED** (specified, not coded), **PLANNED** (future phase), **NOT IMPLEMENTED** (unavailable).

> Phase 0 escrow is an **internal MySQL simulation**. No real money, payments, blockchain transactions or AI calls exist in this codebase. Nothing in this repository has been compiled, tested or run by the AI agent that generated it; see `phase-0-acceptance-criteria.md`.

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
| architecture.md | Modular monolith layout, request flow, escrow boundary |
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
| phase-1-business-hardening.md | Next phase |
| phase-2-ai-monitoring.md | Future AI monitoring design (PLANNED) |
| event-contracts.md | Future event envelope and payloads (DESIGNED) |
| phase-3-avalanche.md | Future on-chain escrow (PLANNED) |
| phase-4-production-readiness.md | Production hardening (PLANNED) |
| demo-credentials.md | Local demo accounts (DEV ONLY) |
| decisions/ | Architecture Decision Records |
