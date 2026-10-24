# State Machines — IMPLEMENTED

Every transition below is enforced in a service method, recorded in `status_history` and `audit_logs`. No generic status-update endpoint exists.

## Campaign
| From | To | Actor | Endpoint |
|---|---|---|---|
| — | DRAFT | creator | POST /campaigns |
| DRAFT, REJECTED | PENDING_REVIEW | owner | /submit |
| PENDING_REVIEW | APPROVED / REJECTED | admin ≠ creator | /approve, /reject |
| APPROVED | ACTIVE | owner or admin | /publish |
| ACTIVE | PAUSED | admin | /pause |
| PAUSED | ACTIVE | admin | /resume |
| ACTIVE, PAUSED | COMPLETED | admin | /complete |
| DRAFT, PENDING_REVIEW, APPROVED, REJECTED | CANCELLED | owner or admin | /cancel |
| ACTIVE, PAUSED | CANCELLED | admin | /cancel |
`review_status`: NOT_SUBMITTED → PENDING → APPROVED/REJECTED (resubmission returns to PENDING).

## Donation
| From | To | Trigger |
|---|---|---|
| — | PENDING | donor create |
| PENDING | CONFIRMED | admin confirm (ledger CONTRIBUTION_CONFIRMED) |
| PENDING | FAILED | admin fail |
| PENDING | CANCELLED | donor cancel |
| CONFIRMED, PARTIALLY_REFUNDED | REFUND_PENDING | refund requested |
| REFUND_PENDING | CONFIRMED / PARTIALLY_REFUNDED | refund rejected or cancelled |
| REFUND_PENDING | PARTIALLY_REFUNDED / REFUNDED | refund recorded |

## Milestone
| From | To | Trigger |
|---|---|---|
| — | PLANNED | create |
| PLANNED | READY | campaign published (first) or previous milestone closed |
| READY | IN_PROGRESS | owner start |
| IN_PROGRESS, EVIDENCE_SUBMITTED, UNDER_REVIEW, PARTIALLY_DISBURSED, DISBURSED | derived | see below |
| READY…DISBURSED | DISPUTED | dispute opened (auto-hold) |
| DISPUTED | prior / derived | last open dispute closed |
| DISBURSED, PARTIALLY_DISBURSED | COMPLETED | admin complete |
| PLANNED, READY, IN_PROGRESS, EVIDENCE_SUBMITTED | CANCELLED | admin cancel or campaign cancel |

Derived status (recomputed after evidence, request, review and release events): open request → UNDER_REVIEW; released ≥ planned → DISBURSED; released > 0 → PARTIALLY_DISBURSED; non-rejected evidence → EVIDENCE_SUBMITTED; else IN_PROGRESS.
Adaptation: the suggested milestone `APPROVED` state is not used; approval lives on the disbursement request.

## Disbursement request
| From | To | Actor |
|---|---|---|
| — | SUBMITTED (funds reserved) | owner |
| SUBMITTED | UNDER_REVIEW | admin |
| UNDER_REVIEW | NEEDS_MORE_EVIDENCE | admin |
| NEEDS_MORE_EVIDENCE | UNDER_REVIEW | owner resubmit |
| UNDER_REVIEW | APPROVED | admin |
| SUBMITTED, UNDER_REVIEW, NEEDS_MORE_EVIDENCE, APPROVED | REJECTED (reservation returned) | admin |
| SUBMITTED, UNDER_REVIEW, NEEDS_MORE_EVIDENCE | CANCELLED (reservation returned) | owner |
| APPROVED | RELEASED | admin |
| SUBMITTED…APPROVED | DISPUTED | dispute hold |
| DISPUTED | pre-dispute status / REJECTED | dispute closed |
Adaptation: no DRAFT state; requests are submitted on creation.

## Dispute
| From | To | Actor |
|---|---|---|
| — | OPEN | eligible donor or admin |
| OPEN | UNDER_REVIEW | admin assigns reviewer |
| UNDER_REVIEW | RESOLVED_UPHELD / RESOLVED_DISMISSED | assigned reviewer |
| OPEN, UNDER_REVIEW | WITHDRAWN | opener |

## Other
Organization verification: UNVERIFIED/REJECTED → PENDING_REVIEW → VERIFIED/REJECTED; VERIFIED → UNVERIFIED on material change.
Evidence: SUBMITTED → ACCEPTED/REJECTED.
Refund: REQUESTED → APPROVED (funds reserved, Phase 1) → RECORDED (reservation released and refund recorded); REQUESTED/APPROVED → REJECTED (reservation released if held); REQUESTED → CANCELLED.
Escrow account: ACTIVE ↔ FROZEN (pause/resume) → SETTLEMENT (complete/cancel).
User: ACTIVE ↔ SUSPENDED.

## Phase 1 additions
Donation: the fundraising-close job (system actor) also performs PENDING → FAILED once the campaign end date has passed. That job does not change the campaign status; it only sets `fundraisingClosedAt`.

Settlement request:

| From | To | Action | Actor |
|---|---|---|---|
| — | PROPOSED | propose | ADMIN (not campaign creator) |
| PROPOSED | EXECUTED | approve (executes) | different ADMIN (not proposer, not creator) |
| PROPOSED | REJECTED | reject | different ADMIN |
| PROPOSED | CANCELLED | cancel | proposer |

Escrow adjustment:

| From | To | Action | Actor |
|---|---|---|---|
| — | REQUESTED | request | ADMIN (not campaign creator) |
| REQUESTED | APPLIED | approve (appends ledger entry) | different ADMIN (not requester, not creator) |
| REQUESTED | REJECTED | reject | different ADMIN |
| REQUESTED | CANCELLED | cancel | requester |

Reconciliation runs are immutable records, written once with status CONSISTENT, INCONSISTENT or FAILED.
