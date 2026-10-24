# ADR-005 — Avalanche Integration Boundary

## Context
Future custody of funds on Avalanche C-Chain must not let the database be mistaken for proof of transfers.

## Decision
Contract state is authoritative for custody and transfers; MySQL keeps metadata and a mirror ledger keyed by chain identifiers. Blockchain logic lives only in a new `EscrowEngine` implementation plus sync workers; never in controllers or entities. Keys are held in KMS/HSM; release authority is human-controlled (multisig). Nothing is implemented in Phase 0.

## Alternatives
Backend-held hot wallet with full authority (rejected: single point of compromise); on-chain storage of all metadata (rejected: cost, privacy).

## Rationale
Clear source-of-truth split, auditable on-chain movements, minimal service changes.

## Consequences
Asynchronous confirmation states, reorg handling and reconciliation against chain balances are required in Phase 3. Funds already sent to external accounts cannot be recovered by the platform.
