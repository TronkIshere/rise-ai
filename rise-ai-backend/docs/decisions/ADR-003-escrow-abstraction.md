# ADR-003 — Escrow Engine Abstraction

## Context
Phase 3 will replace simulated escrow with Avalanche contracts. Business services must not change much.

## Decision
`escrow.EscrowEngine` interface with operations `openAccount`, `changeStatus`, `recordContribution`, `reserve`, `cancelReservation`, `release`, `recordRefund`. Phase 0 implementation `SimulatedEscrowEngine` uses MySQL, requires an existing transaction (`Propagation.MANDATORY`), locks the account row, enforces idempotency keys and invariants, and audits each ledger entry. It is the only writer of escrow tables.

## Alternatives
No abstraction (rejected: Phase 3 would touch every service); ports/adapters for every module (rejected: overengineering).

## Rationale
One seam, at the exact place where the future implementation differs.

## Consequences
The on-chain engine will be asynchronous; the interface will need pending/confirmed semantics (Phase 3 extends it rather than changing callers' rules). Reads (`EscrowQueryService`) stay MySQL-based mirrors.
