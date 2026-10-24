# Audit and Traceability — IMPLEMENTED

## Stores
| Store | Content | Mutability |
|---|---|---|
| `audit_logs` | actor, action, entity type/id, previous/new state, correlation id, IP, JSON metadata | append-only (Hibernate `@Immutable` + DB triggers) |
| `status_history` | every state transition with actor and reason | append-only |
| `escrow_ledger_entries` | every money movement with references | append-only |
| `dispute_messages` | dispute conversation | append-only |
| `reconciliation_runs` | reconciliation run results and findings (Phase 1) | append-only (Hibernate `@Immutable` + DB triggers) |

Audit and history rows are written in the same transaction as the business change, so a rolled-back operation leaves no audit trace and a committed one always has one.

## Audited actions
USER_REGISTERED, USER_LOGIN, USER_LOGOUT, REFRESH_TOKEN_REUSE_DETECTED, USER_PROFILE_UPDATED, USER_SUSPENDED, USER_REACTIVATED, USER_ROLES_CHANGED;
ORGANIZATION_CREATED/UPDATED/DELETED/VERIFICATION_REQUESTED/VERIFIED/VERIFICATION_REJECTED/VERIFICATION_RESET;
CAMPAIGN_CREATED/UPDATED/SUBMITTED_FOR_REVIEW/APPROVED/REJECTED/PUBLISHED/PAUSED/RESUMED/COMPLETED/CANCELLED;
MILESTONE_CREATED/UPDATED/DELETED/READY/STARTED/STATUS_CHANGED/COMPLETED/CANCELLED/DISPUTED/DISPUTE_RELEASED;
MILESTONE_EVIDENCE_SUBMITTED, EVIDENCE_ACCEPTED, EVIDENCE_REJECTED;
DONATION_CREATED/CONFIRMED/FAILED/CANCELLED/STATUS_CHANGED;
DISBURSEMENT_REQUESTED/REVIEW_STARTED/EVIDENCE_REQUESTED/RESUBMITTED/APPROVED/REJECTED/CANCELLED/RELEASED/HELD_FOR_DISPUTE/DISPUTE_RELEASED/REJECTED_BY_DISPUTE;
REFUND_REQUESTED/APPROVED/REJECTED/CANCELLED/RECORDED;
DISPUTE_OPENED/ASSIGNED/REASSIGNED/MESSAGE_ADDED/RESOLVED/WITHDRAWN;
ESCROW_ACCOUNT_OPENED, ESCROW_STATUS_CHANGED, LEDGER_<TRANSACTION_TYPE>;
Phase 1: RECONCILIATION_RUN_COMPLETED, SETTLEMENT_PROPOSED/EXECUTED/REJECTED/CANCELLED, ADJUSTMENT_REQUESTED/APPLIED/REJECTED/CANCELLED, CAMPAIGN_FUNDRAISING_CLOSED (system actor). Pending donations failed by fundraising close are recorded as DONATION_FAILED with a null actor.

## Tracing an operation
1. Every response carries `X-Correlation-Id` (client may supply one).
2. `GET /api/v1/admin/audit-logs?…` — filter by `entityType`/`entityId`, `actorId` or `action`; the `correlationId` links all rows written by one request (e.g. a release writes DISBURSEMENT_RELEASED, LEDGER_FUNDS_RELEASED and MILESTONE_STATUS_CHANGED).
3. Entity history endpoints: `/campaigns/{id}/history`, `/milestones/{id}/history`, `/disbursements/{id}/history`, `/disputes/{id}/history`.
4. Ledger entries reference `reference_type` + `reference_id` (DONATION, DISBURSEMENT_REQUEST, REFUND_REQUEST) and carry donation/milestone/disbursement FKs.
5. Logs include the correlation id in every line (`logging.pattern.console`).

Example — trace a released disbursement: `/disbursements/{id}/history` → ledger `?type=FUNDS_RELEASED` filtered by `disbursementId` → audit-logs `entityId={ledgerEntryId}` → reconciliation report.
