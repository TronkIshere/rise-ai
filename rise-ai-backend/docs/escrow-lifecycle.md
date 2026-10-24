# Escrow Lifecycle — IMPLEMENTED (simulation)

```mermaid
sequenceDiagram
    participant C as Creator
    participant A as Admin
    participant D as Donor
    participant S as Services
    participant E as SimulatedEscrowEngine
    C->>S: create campaign + milestones (DRAFT)
    C->>S: submit (PENDING_REVIEW)
    A->>S: approve (org must be VERIFIED)
    C->>S: publish → ACTIVE
    S->>E: openAccount (ACTIVE)
    D->>S: donate → PENDING (no escrow change)
    A->>S: confirm donation
    S->>E: CONTRIBUTION_CONFIRMED (+confirmed, +available)
    C->>S: start milestone, submit evidence
    A->>S: accept evidence
    C->>S: request disbursement
    S->>E: FUNDS_RESERVED (−available, +reserved)
    A->>S: start review → approve
    A->>S: release
    S->>E: FUNDS_RELEASED (−reserved, +disbursed)
```

## Stages
1. **Campaign creation** — DRAFT with milestones whose planned amounts sum to the target.
2. **Review & publication** — admin approval, then publish opens one escrow account (campaign, VND) and makes milestone #1 READY.
3. **Donations** — PENDING until an admin simulates confirmation; confirmation, ledger entry, aggregates and audit commit in one transaction.
4. **Reservation** — creating a disbursement request immediately reserves the amount so no concurrent request can use it.
5. **Evidence** — metadata with simulated file references (`sim://…`) and optional SHA-256 hash.
6. **Review** — SUBMITTED → UNDER_REVIEW → (NEEDS_MORE_EVIDENCE ↔ UNDER_REVIEW) → APPROVED or REJECTED.
7. **Release** — APPROVED → RELEASED; moves reserved to disbursed. **Simulated: no real transfer.**
8. **Milestone completion** — admin completes; next milestone becomes READY.
9. **Disputes** — hold open requests on the milestone; resolution rejects or restores them.
10. **Refunds** — request → approve → record; draws from available balance only.
11. **Pause** — escrow FROZEN: no contributions, reservations or releases; refunds and reservation cancellations still allowed.
12. **Completion/cancellation** — escrow SETTLEMENT: refunds and cancellations only. **PLANNED (Phase 1):** policy for leftover available balance after completion.

## Balance meanings
| Term | Meaning |
|---|---|
| Planned budget | Sum of milestone planned amounts (intent, not money) |
| Confirmed contributions | Sum of confirmed simulated donations |
| Reserved | Committed to open disbursement requests |
| Disbursed | Released by simulation |
| Refunded | Recorded refunds |
| Available | Unallocated: confirmed − reserved − disbursed − refunded |
| Held | Still inside simulated escrow: confirmed − disbursed − refunded |

## Limitations
Internal MySQL simulation; no on-chain locking; no external payout or refund; evidence is not proof; organization verification is not legal verification.
