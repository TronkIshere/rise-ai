# Implementation Plan

| Phase | Status | Depends on | Deliverables | Exit criteria |
|---|---|---|---|---|
| 0 Core backend | IMPLEMENTED; verified manually by the developer (2026-10-25) | — | Schema, auth, CRUD, simulated escrow, seed, tests, docs | `phase-0-acceptance-criteria.md` items verified locally |
| 1 Business hardening | IMPLEMENTED (2026-10-25); see the progress table in `phase-1-business-hardening.md` | 0 verified locally | See `phase-1-business-hardening.md` | Invariant tests, reconciliation job, stable API |
| 2 AI monitoring integration | MVP IMPLEMENTED (2026-10-25): M0–M7, M10 MVP E2E and most M9 MVP items; M8 (AI-assisted analysis) and production items PENDING. Sub-phases in `../../docs/ai-monitoring/04-roadmap.md` | 1 | Transactional outbox, event publisher, contracts, findings and review (see roadmap) | `phase-2-ai-monitoring.md` criteria and `../../docs/ai-monitoring/05-acceptance-checklist.md` |
| 3 Avalanche escrow | PLANNED | 1 (2 optional) | Contracts, adapter behind `EscrowEngine`, sync | `phase-3-avalanche.md` criteria |
| 4 Production readiness | PLANNED | 1–3 as applicable | Ops, security, observability | `phase-4-production-readiness.md` |

## Phase 0 steps (as executed)
1. Repository review — uploaded files were a partial snapshot of an unrelated project (WiTech). Conventions reused: `ResponseAPI`/`PageResponse` envelopes, `ApplicationException`+`ErrorCode`, Nimbus JWT, BCrypt, UUID ids, stateless filter. Not reused: MatBao/company/invitation/Redis/OTP code. Fixed patterns: error handler leaking root causes, plaintext refresh-token storage, whitelisted `/users/**`.
2. Foundation: pom, `application.yml`, Docker Compose, security, error handling, OpenAPI, correlation ids.
3. Database: V1 schema, V2 append-only triggers, entities, repositories.
4. Auth & authorization.
5. CRUD/query APIs for all modules.
6. Simulated escrow engine, disbursement, disputes, refunds, audit.
7. Seeder via real services.
8. Unit and integration test files.
9. Documentation and ADRs.

## Next action for any agent
Phases 1 and 2 (MVP) are complete; see their documents for evidence. Remaining Phase 2 work is listed in `../../docs/ai-monitoring/06-progress-report.md` › Next steps. Do not start Phase 3 (Avalanche) without an explicit request; it depends only on Phase 1.
