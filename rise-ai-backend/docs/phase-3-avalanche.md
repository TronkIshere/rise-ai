# Phase 3 — Avalanche Escrow Integration (PLANNED, NOT IMPLEMENTED)

No blockchain code, SDKs, keys or RPC configuration exist in Phase 0. Do not deploy to mainnet or handle real assets without explicit authorization, testing and security review.

## Inputs & prerequisites
Phase 1 complete. Read `architecture.md`, `escrow-lifecycle.md`, `ledger-and-balance-rules.md`, ADR-003, ADR-005. Inspect `service/EscrowEngine` and `SimulatedEscrowEngine`.

## Objectives
Back campaign/milestone escrow with Solidity contracts on Avalanche C-Chain (local network/Fuji first), keeping the backend as orchestrator and metadata store.

## Source-of-truth boundary
| Data | Source of truth |
|---|---|
| Token balances held by escrow, releases, refunds | **Contract state** on-chain |
| Campaign text, milestones, evidence metadata, reviews, users | MySQL |
| Ledger rows | MySQL **mirror** of confirmed chain events (with tx hash, block, log index) |
The backend must never assume MySQL proves an on-chain transfer happened.

## Contract boundary (draft)
`CampaignEscrow` per campaign or a registry contract: `deposit`, `reserve(milestone, amount)`, `cancelReservation`, `release(milestone, beneficiary, amount)`, `refund(donor, amount)`, `pause/unpause`, `openDispute/closeDispute`. Roles: release requires an approver role held by a multisig/KMS key controlled by humans; AI has no role. Events emitted for every state change.

## Integration design
1. Add `AvalancheEscrowEngine implements EscrowEngine` selected by configuration (`rise.escrow.engine=simulated|avalanche`). Keep the simulated engine for tests.
2. Business transactions record intent (`status = PENDING`, idempotency key = request id) and enqueue a chain command via an outbox; they do not call RPC inside the DB transaction.
3. A submitter signs and sends transactions (keys in KMS/HSM, never in config or repo), stores `tx_hash`, `chain_id`, `contract_address`, nonce.
4. A synchronizer follows contract events with confirmation depth N, validates `chain_id` and contract address, upserts ledger mirror rows keyed by `(tx_hash, log_index)` (unique), then marks entries `COMPLETED` and updates aggregates.
5. Reorg handling: only finalize after N confirmations; reconcile periodically against `eth_call` balances.
6. Failure handling: RPC timeouts retried with backoff; reverted tx ⇒ entry `FAILED` and business state rolled back through a compensating transition; stuck tx ⇒ replacement with same nonce.

## Schema additions (V-next)
`ledger.status` gains `PENDING`, `FAILED`; new columns `chain_id`, `contract_address`, `tx_hash`, `block_number`, `log_index`, `confirmations`; table `chain_sync_cursor`.

## Allowed changes
New engine implementation, outbox/command tables, sync worker, additive API fields (`txHash`, `onChainStatus`).

## Must not change
Controllers and entities must not contain blockchain logic; existing state machines stay; no large rewrite of services.

## Limitations to keep stating
On-chain escrow cannot recover funds already sent to an unrelated external wallet or bank account. AI never controls release keys.

## Acceptance criteria
- All escrow integration tests pass against the simulated engine and against a local Avalanche network.
- Duplicate chain events do not double-count (unique `(tx_hash, log_index)`).
- Wrong chain id / contract address events are rejected.
- Reconciliation shows MySQL mirror == contract balances.
- Security review of contracts completed before any public testnet with real users.
