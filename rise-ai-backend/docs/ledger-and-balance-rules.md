# Ledger and Balance Rules — IMPLEMENTED

## Source of truth
`escrow_ledger_entries` is the authoritative record. `escrow_accounts` holds aggregates for fast reads, updated in the same transaction and reconcilable at any time.

## Transaction types and effects
| Type | Direction | confirmed | reserved | disbursed | refunded | available | Idempotency key |
|---|---|---|---|---|---|---|---|
| CONTRIBUTION_CONFIRMED | CREDIT | +a | | | | +a | `CONTRIBUTION:{donationId}` |
| FUNDS_RESERVED | NEUTRAL | | +a | | | −a | `RESERVE:{requestId}` |
| RESERVATION_CANCELLED | NEUTRAL | | −a | | | +a | `RESERVATION_CANCEL:{requestId}` |
| FUNDS_RELEASED | DEBIT | | −a | +a | | | `RELEASE:{requestId}` |
| REFUND_RECORDED | DEBIT | | | | +a | −a | `REFUND:{refundRequestId}` |

Direction convention: effect on the *held* balance (money still inside simulated escrow). CREDIT increases it, DEBIT decreases it, NEUTRAL only re-allocates.
Amounts are always positive; the type determines the sign. Status is always `COMPLETED` in Phase 0 (PLANNED: `PENDING`/`FAILED` for on-chain confirmation in Phase 3).

## Formulas
```
reserved  = Σ FUNDS_RESERVED − Σ RESERVATION_CANCELLED − Σ FUNDS_RELEASED
disbursed = Σ FUNDS_RELEASED
refunded  = Σ REFUND_RECORDED
confirmed = Σ CONTRIBUTION_CONFIRMED
available = confirmed − reserved − disbursed − refunded
held      = confirmed − disbursed − refunded
```
Milestone aggregates: `reserved_amount + released_amount ≤ planned_amount` (DB CHECK).

## Transaction boundaries
One business operation = one DB transaction containing: entity state change, ledger append, aggregate update, status history and audit row. `EscrowEngine` requires an existing transaction (`MANDATORY`).

## Concurrency & idempotency
- Lock order: business row (donation/request/refund) → milestone → escrow account (`FOR UPDATE`).
- Duplicate protection: state checks under lock + unique `idempotency_key` + unique `transaction_code`.
- `@Version` on accounts and entities; CHECK constraints reject negative or inconsistent aggregates; `SimulatedEscrowEngine.verifyInvariants` aborts the transaction on any mismatch.
- Ledger rows are `@Immutable` in Hibernate and protected by triggers.

## Reconciliation
`GET /api/v1/admin/escrow/campaigns/{id}/reconciliation` and `/admin/escrow/reconciliation` recompute balances from the ledger and compare them with stored aggregates (`consistent: true|false`). PLANNED (Phase 1): scheduled reconciliation with alerting.

## Corrections
Never update or delete entries. Phase 0 compensations: RESERVATION_CANCELLED. DESIGNED, NOT IMPLEMENTED: admin `ADJUSTMENT` entries with dual control.
