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
| REFUND_RESERVED (Phase 1) | NEUTRAL | refund reserved +a | | | | −a | `REFUND_RESERVE:{refundRequestId}` |
| REFUND_RESERVATION_RELEASED (Phase 1) | NEUTRAL | refund reserved −a | | | | +a | `REFUND_RESERVATION_RELEASE:{refundRequestId}` |
| SETTLEMENT_REFUND_RECORDED (Phase 1) | DEBIT | | | | +a | −a | `SETTLEMENT_REFUND:{settlementId}:{donationId}` |
| ADJUSTMENT_CREDIT (Phase 1) | CREDIT | adjustment credits +a | | | | +a | `ADJUSTMENT:{adjustmentId}` |
| ADJUSTMENT_DEBIT (Phase 1) | DEBIT | adjustment debits +a | | | | −a | `ADJUSTMENT:{adjustmentId}` |

Recording an approved (reserved) refund appends `REFUND_RESERVATION_RELEASED` and then `REFUND_RECORDED` in one transaction. Refunds approved before V3 have no reservation and are recorded directly.

Direction convention: effect on the *held* balance (money still inside simulated escrow). CREDIT increases it, DEBIT decreases it, NEUTRAL only re-allocates.
Amounts are always positive; the type determines the sign. Status is always `COMPLETED` in Phase 0 (PLANNED: `PENDING`/`FAILED` for on-chain confirmation in Phase 3).

## Formulas
```
reserved          = Σ FUNDS_RESERVED − Σ RESERVATION_CANCELLED − Σ FUNDS_RELEASED
refund reserved   = Σ REFUND_RESERVED − Σ REFUND_RESERVATION_RELEASED
disbursed         = Σ FUNDS_RELEASED
refunded          = Σ REFUND_RECORDED + Σ SETTLEMENT_REFUND_RECORDED
confirmed         = Σ CONTRIBUTION_CONFIRMED
adjustment credit = Σ ADJUSTMENT_CREDIT
adjustment debit  = Σ ADJUSTMENT_DEBIT
available = confirmed + adjustment credit − adjustment debit − reserved − refund reserved − disbursed − refunded
held      = confirmed + adjustment credit − adjustment debit − disbursed − refunded
```
Milestone aggregates: `reserved_amount + released_amount ≤ planned_amount` (DB CHECK). The available-balance identity above is a DB CHECK (`ck_escrow_balance_identity`, redefined in V3) and is also enforced by `SimulatedEscrowEngine.verifyInvariants`. The formula is implemented once in `EscrowDtos.Balances.fromLedger`, which reconciliation and the property tests share.

## Transaction boundaries
One business operation = one DB transaction containing: entity state change, ledger append, aggregate update, status history and audit row. `EscrowEngine` requires an existing transaction (`MANDATORY`).

## Concurrency & idempotency
- Lock order: business row (donation/request/refund) → milestone → escrow account (`FOR UPDATE`).
- Duplicate protection: state checks under lock + unique `idempotency_key` + unique `transaction_code`.
- `@Version` on accounts and entities; CHECK constraints reject negative or inconsistent aggregates; `SimulatedEscrowEngine.verifyInvariants` aborts the transaction on any mismatch.
- Ledger rows are `@Immutable` in Hibernate and protected by triggers.

## Reconciliation
`GET /api/v1/admin/escrow/campaigns/{id}/reconciliation` and `/admin/escrow/reconciliation` recompute balances from the ledger and compare them with stored aggregates (`consistent: true|false`).

IMPLEMENTED (Phase 1):
- **When it runs:** `ReconciliationService` runs on `rise.jobs.reconciliation-cron` (hourly by default) and on demand via `POST /admin/escrow/reconciliation/runs`. `rise.jobs.enabled=false` turns the schedule off.
- **Run history:** every run is stored in the append-only `reconciliation_runs` table with its status (CONSISTENT, INCONSISTENT or FAILED), counts and findings JSON. `GET /admin/escrow/reconciliation/runs` lists them.
- **Alerting:** each mismatch is logged at ERROR with the prefix `ALERT escrow reconciliation mismatch`, so log-based alerting can match on it.
- **Safety:** the computation runs in one read-only transaction, which gives a consistent InnoDB snapshot. Running it on several instances at once is harmless.

## Corrections
Never update or delete entries. Compensations: RESERVATION_CANCELLED, REFUND_RESERVATION_RELEASED. IMPLEMENTED (Phase 1): `ADJUSTMENT_CREDIT` / `ADJUSTMENT_DEBIT` entries, written only after a second ADMIN approves an adjustment request (`/api/v1/admin/adjustments`); see business rules 50–53.
