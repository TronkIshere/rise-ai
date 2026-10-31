# FE-04 — Admin Experience acceptance checklist

Status: **DRAFT, not accepted or merged**. Scope: only `rise-ai-frontend/` and existing Spring Boot API. No real-money operations.

## Permission and state invariants
- All `/admin`, `/admin/review/:kind/:id`, `/admin/escrow` routes require an authenticated `ADMIN`. Anonymous, Donor and Creator must not access them. Backend checks permission even if UI is bypassed.
- Organization verification: only PENDING_REVIEW may be accepted/rejected. `VerificationDecisionRequest.notes`; no self-approval or fabricated verification.
- Campaign approval: only PENDING_REVIEW, VERIFIED organization and backend business validation; owners cannot self-approve. Pause/resume/complete use backend transitions, not client-sided status updates.
- Donation: Admin simulation confirmation/failure on PENDING only, separate from Donor's creation. Never label real payment completed.
- Refund: REQUESTED -> APPROVED/REJECTED -> RECORDED; backend prohibits reviewer conflict and ledger inconsistencies. RECORD is simulated ledger-only.
- Disbursement: SUBMITTED -> UNDER_REVIEW -> APPROVED/NEEDS_MORE_EVIDENCE/REJECTED -> RELEASED (if approved). Review evidence from associated milestone, submit metadata approvals; Backend enforces accepted evidence and prohibits self-approval.
- Dispute: OPEN -> assigned to active authorized Admin -> UNDER_REVIEW -> RESOLVED_UPHELD or RESOLVED_DISMISSED by assigned reviewer only. Backend guards conflicts and audit.
- Adjustments/settlements: proposals are not immediate ledger entries. Require independent legitimate approval; all funds are simulated and no bank transaction occurs.
- Reconciliation: display global/per-campaign stored-vs-ledger balances, INCONSISTENT vs CONSISTENT, run history; do not mask deviations. Manual POST run only by deliberate user action.
- FE-05 monitoring findings and audit-log UI are **not** included in FE-04.

## CI and source checks
- [ ] `npm ci && npm run verify` (architecture, source OpenAPI contract, lint, TypeScript, unit tests, Vite build).
- [ ] Frontend push and PR workflows pass on final branch SHA.
- [ ] Backend, Monitoring and Images CI pass on same PR SHA; if any external Maven fetch failure occurs, document evidence and retry, don't change source blindly.
- [ ] Only frontend files changed; no Backend/Monitoring/API schema changes.
- [ ] PR remains Draft until owner browser acceptance, then Squash Merge.

## Windows manual browser smoke
Run local MySQL and Backend 8082 with `RISE_MONITORING_ENABLED=false`; Frontend 5173 with `VITE_API_BASE_URL=http://localhost:8082`. Use local seed account `admin@riseai.example` / `Demo@12345` (development database only).

- [ ] Login as Admin, access /admin and inspect real system stats. Non-Admin and anonymous route guards block access; logout clears private query cache.
- [ ] Change every queue/status filter, paginate; loading/empty/backend-down cases show correct feedback. Detail records show actual IDs/status/campaign relations.
- [ ] Organization PENDING_REVIEW: accept/reject with notes using independent Admin, verify backend result; creator cannot self-approve.
- [ ] Campaign PENDING_REVIEW with VERIFIED org: approve/reject. ACTIVE pause/resume and eligible complete without fake transitions. Recheck FE-03 Creator publish after approval.
- [ ] PENDING simulated donation confirm/fail; no claim of real payment. No approval of own donor contribution.
- [ ] REQUESTED refund approve or reject and separately record APPROVED refund; verify `externalPaymentStatus=NOT_EXECUTED_SIMULATION`.
- [ ] SUBMITTED disbursement start review, inspect associated milestone evidence, approve/reject SUBMITTED evidence with notes, request more evidence, approve, and release approved **simulated** amount only when Backend allows.
- [ ] Dispute OPEN can be assigned to current active Admin and then resolved by assigned reviewer; distinct conflict-of-interest accounts and invalid state must be rejected by Backend.
- [ ] Escrow: global reconciliation loads; select campaign UUID; see account balances and ledger entries; create manual reconciliation run; inspect status and mismatch findings.
- [ ] Propose simulated ledger adjustment and settlement only on eligible state; queue and review using independent Admin, respecting self-approval prohibition. No real transaction.
- [ ] Keyboard accessibility, 375px/1440px layouts, error/loading states, no user-entered HTML interpolation, FE-01/02/03 regression smoke.
- [ ] FE-05 AI findings or audit not mistakenly presented as implemented.

**Demo data caveat:** Some actions require multiple role-separated actors or eligible seed states; don't claim those operations PASS until actually exercised or backend integration evidence is available.

## Acceptance gate
After confirmed browser PASS, verify every final CI job and PR mergeability; mark Ready, Squash Merge into master as a single FE-04 commit; verify CI on the merge commit.
