# Business Rules — IMPLEMENTED unless marked

## Identity & access
1. Registration grants only DONOR or CAMPAIGN_CREATOR. ADMIN is assigned by seed or by another ADMIN.
2. Users cannot change their own roles or status.
3. Suspended users cannot log in, refresh, or use existing access tokens (user loaded per request).
4. Hidden resources return 404 rather than 403 to avoid existence disclosure.

## Organizations
5. VERIFIED means internal platform review only; responses carry a disclaimer.
6. An admin cannot verify an organization they created.
7. Material changes (name/type) by the owner reset VERIFIED to UNVERIFIED.
8. Organizations referenced by campaigns cannot be deleted.

## Campaigns
9. Campaigns start DRAFT and may be edited only in DRAFT or REJECTED.
10. Currency must be VND (Phase 0). Amounts have at most 2 decimals and are positive.
11. If a budget breakdown is given, it must sum exactly to `targetAmount`.
12. Submission requires ≥ 1 milestone, milestone sum == target, future `endAt`, organization not REJECTED.
13. Approval requires an ADMIN who is not the creator and a VERIFIED organization.
14. Only ACTIVE campaigns inside `[startAt, endAt]` accept donations. Once `endAt` passes, simulated confirmation of a still-PENDING donation is refused (Phase 1).
15. Completion and cancellation of published campaigns require no open disbursement requests and zero reserved funds.

## Milestones
16. Milestone budgets may never exceed the campaign target in total.
17. Milestones become READY one at a time in sequence; the next becomes READY when no milestone is in active work.
18. Only the owner starts a READY milestone; only an ADMIN who is not the creator completes it (from DISBURSED/PARTIALLY_DISBURSED with nothing reserved).

## Donations
19. Creating a donation never changes escrow; only admin-simulated confirmation does.
20. A donation is confirmed at most once (state check + ledger idempotency key).
21. Creators cannot donate to their own campaign; admins cannot confirm their own donation.
22. `Idempotency-Key` replays return the original donation; reuse for different content is rejected.

## Evidence
23. Only the campaign owner submits evidence, only for started milestones of published campaigns.
24. Evidence is metadata; it does not prove legitimate spending.
25. Reviewer may not be the submitter or campaign creator.

## Disbursements
26. One open request per milestone at a time.
27. Request requires: owner, ACTIVE campaign, milestone in IN_PROGRESS/EVIDENCE_SUBMITTED/PARTIALLY_DISBURSED, positive amount ≤ milestone remaining (planned − reserved − released), ≥ 1 non-rejected evidence, sufficient available balance (reserved immediately).
28. Approval requires ≥ 1 ACCEPTED evidence on the milestone, ACTIVE campaign, no open dispute on the milestone.
29. Reviewer/approver/releaser must be ADMIN and neither requester nor campaign creator.
30. Release only from APPROVED; a second release is DUPLICATE_OPERATION; total released never exceeds planned.
31. Rejection or cancellation returns the reservation to available balance.

## Disputes
32. Openers: ADMIN or donors with a confirmed (or refund-affected) donation to the campaign; never the campaign creator.
33. With `rise.escrow.dispute-auto-hold=true`, a dispute on a milestone (or a request) marks the milestone DISPUTED and holds all its open requests; releases and approvals are blocked while any dispute on the milestone is open.
34. Campaign-level disputes (no milestone) never auto-hold; admins may pause the campaign.
35. Resolver must be the assigned ADMIN and not the opener or creator. UPHELD rejects held requests (returning reservations); DISMISSED/WITHDRAWN restores prior states once no open dispute remains.
36. A dispute is never treated as proof of fraud and never alters released funds.

## Refunds
37. Refund requested ≠ approved ≠ recorded ≠ externally paid. Phase 0 never pays externally (`external_payment_status = NOT_EXECUTED_SIMULATION`).
38. One open refund per donation; amount ≤ donation amount − refunded.
39. Refunds draw only from unreserved available balance. Since Phase 1, approval reserves the amount (`REFUND_RESERVED`); rejecting an approved refund returns it (`REFUND_RESERVATION_RELEASED`); recording releases the reservation and records the refund in the same transaction. Disbursement requests can therefore never consume funds promised to an approved refund.
40. Recording is idempotent per refund request.

## Financial invariants (enforced in code and DB)
41. No negative balances; `available = confirmed − reserved − disbursed − refunded`.
42. Ledger is append-only; corrections are compensating entries (`RESERVATION_CANCELLED`, `REFUND_RESERVATION_RELEASED`) or dual-controlled adjustments (rules 50–53).
43. All money uses `BigDecimal` / `DECIMAL(19,2)`.

## Phase 1 additions — IMPLEMENTED
### Reconciliation
44. A scheduled job (`rise.jobs.reconciliation-cron`, hourly by default) and an ADMIN-triggered run recompute every escrow account from the ledger and compare it with stored aggregates. Each run is stored append-only in `reconciliation_runs` with its findings; every mismatch is logged at ERROR with the prefix `ALERT`. A run never modifies balances.

### Settlement of leftover balance (ADR-006)
45. Leftover available balance is settled only for COMPLETED or CANCELLED campaigns whose escrow is in SETTLEMENT, with zero disbursement and refund reservations and no donation in REFUND_PENDING.
46. The only settlement policy is a pro-rata refund to donors: amount = min(available balance, Σ refundable of CONFIRMED/PARTIALLY_REFUNDED donations); each donation receives its share of the amount in proportion to its refundable amount, rounded down to the cent, with remaining cents assigned by largest remainder. No donation can receive more than its refundable amount. Admin-controlled reallocation to another campaign is NOT IMPLEMENTED.
47. Dual control: one ADMIN proposes, a different ADMIN approves (which executes) or rejects; the proposer can only cancel. Neither may be the campaign creator. One open proposal per campaign.
48. Approval recomputes the amount under lock; if it differs from the proposal the approval fails with `CONCURRENT_MODIFICATION` and a new proposal is required.
49. Execution writes one `SETTLEMENT_REFUND_RECORDED` ledger entry per donation and updates the donation to PARTIALLY_REFUNDED or REFUNDED. No external payment is executed.

### Ledger adjustments
50. `ADJUSTMENT_CREDIT` / `ADJUSTMENT_DEBIT` entries are created only through an adjustment request with a mandatory reason.
51. Dual control: the requesting ADMIN cannot approve or reject; a different ADMIN decides; the requester can only cancel. Neither may be the campaign creator.
52. A debit may not exceed the available balance at approval time. Adjustments are allowed in any escrow status.
53. Adjustments are tracked in separate aggregates (`total_adjustment_credits`, `total_adjustment_debits`); contributions, disbursements and refunds are never edited.

### Idempotency
54. `POST /milestones/{id}/disbursements` and `POST /donations/{id}/refunds` accept an optional `Idempotency-Key` header (1–100 chars), unique per requesting user. A replay returns the original request; reusing the key for a different milestone/donation or amount fails with `DUPLICATE_OPERATION`. The key is checked after the milestone/donation row lock, so concurrent replays also return the original.

### Campaign end date
55. A scheduled job (`rise.jobs.fundraising-close-cron`, every 5 minutes by default) closes fundraising for ACTIVE/PAUSED campaigns whose `endAt` has passed: it sets `fundraisingClosedAt`, fails remaining PENDING donations (system actor) and audits `CAMPAIGN_FUNDRAISING_CLOSED`. It is idempotent and does not change the campaign status: completion stays an ADMIN decision because milestone work continues after fundraising ends.
56. Campaigns whose `endAt` has passed cannot be submitted, approved or published.
57. Campaign responses expose `fundraisingClosedAt` and `acceptingDonations`.
