# Phase 3 — Avalanche Escrow Integration (IMPLEMENTED, opt-in, testnet only)

Do not deploy to mainnet or handle real assets without explicit authorization, testing and security review.

## Status (2026-11-11, from the merged code and recorded runs)

Merged through PRs #17, #18 and #19 (commits `84b36b5`, `0ede0be`, `2f05d8e`, `c1463fb`, `74de1a5`).

| Item | State | Evidence |
|---|---|---|
| Engine selection `rise.escrow.engine=simulated\|avalanche` | IMPLEMENTED; default `simulated` | `application.yml` (`RISE_ESCROW_ENGINE`), `AvalancheEscrowEngine` is `@ConditionalOnProperty(... havingValue = "avalanche")` |
| `AvalancheEscrowEngine` | IMPLEMENTED | Keeps the MySQL ledger and every rule of `SimulatedEscrowEngine`, and queues the matching `CampaignEscrow` call in the same transaction. A release is recorded as RELEASED only after the chain executes it, after the objection window |
| Chain command outbox and relay | IMPLEMENTED | `chain_commands` (V12), `ChainCommandRelay` (runs outside business transactions, strictly in order per campaign) |
| Gateway | IMPLEMENTED as a separate service | `rise-ai-avalanche/` (port 8082), called through `AvalancheGatewayClient` with an internal API key. ADR-005 originally placed chain logic inside the backend; no ADR yet records the separate-service choice |
| Contracts | IMPLEMENTED, tested locally | `CampaignEscrow`, `RiseVND` (simulated VND, 2 decimals), `RiseAnchor` in `rise-ai-avalanche/contracts/src` |
| History anchoring | IMPLEMENTED | `chain_anchors` (V13), `ChainAnchorRelay`; public `GET /api/v1/transparency/anchors` reports CONSISTENT / MISMATCH / NOT_ANCHORED / UNAVAILABLE |
| Public chain view | IMPLEMENTED | `GET /api/v1/transparency/chain`, `GET /api/v1/transparency/campaigns/{campaignId}` (`onChain=false` with the simulated engine) |
| Fuji signing | IMPLEMENTED as a testnet exception | ADR-009: operator-held keystores, Fuji only; no KMS/HSM or multisig |
| Fuji end-to-end, dispute and anchoring runs | RUN once, 2026-11-10 | Transaction links in `rise-ai-avalanche/README.md` |
| Tests | Backend `AvalancheEscrowEngineIntegrationTest` (against a stub gateway), `AvalancheDemoSeedIntegrationTest`, `TransparencyIntegrationTest`; gateway and Foundry suites in CI (`avalanche-gateway`, `avalanche-contracts` jobs) | `.github/workflows/ci.yml` |

**Differences from the design below.** The implemented ledger rows are still written as `COMPLETED` in the business transaction; there are no `chain_id` / `tx_hash` / `log_index` columns on the ledger and no `chain_sync_cursor` table. The chain transaction hash is stored on `chain_commands` and `chain_anchors`. The design sections below are kept as the original plan.

**Acceptance criteria status.** "Security review of contracts completed" is **not met** (no external audit). The other criteria below are not ticked here, because no document records a check against each of them.


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
