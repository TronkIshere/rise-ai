# ADR-006 — Settlement Policy and Dual-Controlled Ledger Changes

## Context
Phase 1 (`phase-1-business-hardening.md`, objectives 3 and 5) needs two things:
- a policy for the available balance left after a campaign is COMPLETED or CANCELLED, either a "pro-rata refund batch or admin-controlled reallocation";
- controlled ledger adjustments that require two distinct admins.

## Decision
- **Settlement policy:** pro-rata refund to donors only.
  - The settled amount is min(available balance, Σ refundable of CONFIRMED/PARTIALLY_REFUNDED donations).
  - Each donation's share is proportional to its refundable amount, floored to the cent; leftover cents go to the largest remainders (`settlement/ProRataAllocation`).
- **Settlement ledger entries:** each share is a `SETTLEMENT_REFUND_RECORDED` entry (a new DEBIT type, counted in `total_refunded`). It is written through `EscrowEngine.recordSettlementRefund`, which only accepts accounts in SETTLEMENT.
- **Dual control:** settlements and adjustments are two-step requests. One ADMIN proposes or requests, and a different ADMIN approves. The database also enforces `decided_by <> proposed_by` (settlements) and `decided_by <> requested_by` (adjustments).
- **Adjustment ledger entries:** adjustments use their own types (`ADJUSTMENT_CREDIT`, `ADJUSTMENT_DEBIT`) and aggregates (`total_adjustment_credits`, `total_adjustment_debits`). V3 redefines the balance-identity CHECK to include them.

## Alternatives
- **Admin-controlled reallocation to another campaign.** Rejected for now: it moves donor money to a cause the donor did not choose, and it needs donor-consent rules and cross-account ledger entries.
- **Reusing `REFUND_RECORDED` for settlement.** Rejected: the ledger could no longer tell donor-requested refunds from policy refunds.
- **A single signed `ADJUSTMENT` type.** Rejected: the ledger convention is positive amounts, with the type defining the direction.

## Rationale
Pro-rata refunds return money only to the people who gave it. They are deterministic, auditable per donation, and need no new consent model. Dual control limits single-admin mistakes and misuse on the only paths that change balances without a donor or creator action.

## Consequences
- Leftover funds above the total refundable amount (possible only after positive adjustments) stay in escrow and need an adjustment.
- Reallocation can be added later as a new settlement type behind the same propose/approve workflow.
- Phase 3 (on-chain escrow) must provide equivalents of `recordSettlementRefund` and `recordAdjustment`.
