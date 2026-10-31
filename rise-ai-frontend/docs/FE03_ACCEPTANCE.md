# FE-03 — Creator Experience acceptance checklist

**Status:** implementation on Draft PR; **not merged or accepted** until CI and browser smoke are confirmed.

## Backend contract and permission boundaries
- `CAMPAIGN_CREATOR` routes: `/creator`, `/creator/campaigns/new`, `/creator/campaigns/:id`, `/creator/campaigns/:id/edit`; auth remains memory-only and resets on refresh.
- Organizations: `GET /api/v1/organizations/mine`, `POST /api/v1/organizations`, `PUT /api/v1/organizations/{id}`, `POST /api/v1/organizations/{id}/submit-verification`. This sends a **request**; only Admin can verify.
- Campaigns: mine/create/edit, submit when at least one milestone exists and **sum(plannedAmount) = targetAmount**; publish only after Admin APPROVED. Editing is allowed only for DRAFT/REJECTED.
- Milestones: create/edit in DRAFT/REJECTED; start after ACTIVE + READY. Dates use local datetime converted to UTC instants. No Admin-only completion/review controls.
- Evidence: list/create metadata only; real description/URL/external reference; optional real file SHA-256. **No binary file upload, no invented hash, no paid claims.** Admin decides ACCEPTED/REJECTED.
- Disbursement: create on ACTIVE campaign with started milestone and submitted evidence; key per request preserved on retry; can resubmit when NEEDS_MORE_EVIDENCE, cancel when Backend allows. Approval, ledger release and simulated escrow decisions belong to Admin.
- Backend always validates ownership, status, funding, evidence and role regardless of UI.

## Automation and PR
- [ ] `npm ci && npm run verify` (architecture, contract, lint, typecheck, unit test, build).
- [ ] Frontend CI (push and PR) and Backend/Monitoring CI green on final branch SHA.
- [ ] Only `rise-ai-frontend/` files changed; no Backend edits, schema changes or fake resources.
- [ ] One implementation commit on FE-03 branch, squash into one commit on master after browser acceptance.

## Windows manual browser smoke
Keep MySQL Docker healthy and Backend on 8082; `VITE_API_BASE_URL=http://localhost:8082`. Run Frontend on 5173. Login with development account `creator1@riseai.example` / `Demo@12345` (local demo only).
- [ ] `/creator` loads owned organizations and campaigns, handles empty/loading/errors and pagination.
- [ ] Create organization with actual test metadata; verify UNVERIFIED, update metadata, submit verification => PENDING_REVIEW. Do not label VERIFIED until Backend confirms.
- [ ] Create campaign DRAFT with complete required fields and future end date; use VND simulated budget. Verify owned route access and edit while DRAFT.
- [ ] Create milestone(s), observe budget validation and reject submit when totals do not equal campaign target; accept submit when totals equal (then PENDING_REVIEW).
- [ ] Use a **separate legitimate Admin** workflow to verify organization and approve campaign (FE-04 is not part of this PR). After APPROVED, Creator can publish => ACTIVE.
- [ ] On ACTIVE campaign: start READY milestone, create evidence with a real description or external reference; verify SUBMITTED not ACCEPTED. Request disbursement after evidence, verify SUBMITTED and that no money moved.
- [ ] For a test request marked NEEDS_MORE_EVIDENCE by legitimate Admin action: add evidence and resubmit. Cancel only in Backend-permitted states.
- [ ] DONOR and anonymous users cannot enter Creator routes; Creator cannot invoke any Admin controls; different Creator cannot manage another Creator's campaign.
- [ ] 375px and 1440px browser layouts, keyboard, network failures, auth logout/cache isolation, regression smoke FE-01 and FE-02.
- [ ] No real payment, simulated approvals, fabricated hashes, file upload or external AI execution.

## Gate to merge
Only move Draft PR to Ready and **Squash Merge** after CI plus creator-flow browser PASS is reported. If an Admin-dependent path was not tested, mark it NOT VERIFIED rather than assuming PASS.
