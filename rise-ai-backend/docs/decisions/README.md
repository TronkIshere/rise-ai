# Architecture Decision Records

| ADR | Title | Status |
|---|---|---|
| ADR-001 | Modular monolith on Spring Boot | Accepted; packaging by capability superseded by ADR-008 |
| ADR-002 | MySQL schema, UUID keys, enum roles and append-only ledger | Accepted |
| ADR-003 | Escrow engine abstraction | Accepted |
| ADR-004 | AI monitoring event boundary | Accepted; implemented in Phase 2 (2026-10-25) |
| ADR-005 | Avalanche integration boundary | Accepted; implemented as an opt-in engine with a separate gateway service (`rise-ai-avalanche/`), which no ADR records yet. ADR-009 is a testnet exception to its key-custody rule |
| ADR-006 | Settlement policy and dual-controlled ledger changes | Accepted (Phase 1) |
| ADR-007 | AI monitoring integration mechanics: outbox relay over HTTP, synchronous findings, review in the backend | Accepted (Phase 2) |
| ADR-008 | Layered package structure (controller, service/impl, repository, entity, dto, …) | Accepted (2026-10-25) |
| ADR-009 | Operator-held keystore signers on Fuji (testnet exception to ADR-005) | Accepted by the project owner (2026-11-10), AVALANCHE_FUJI only |

Format: Context, Decision, Alternatives, Rationale, Consequences. Add new ADRs as `ADR-00N-title.md`; never rewrite accepted ones — supersede them.
