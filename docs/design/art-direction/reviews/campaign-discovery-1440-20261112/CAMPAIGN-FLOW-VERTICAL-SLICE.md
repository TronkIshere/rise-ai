# Campaign Discovery → Detail → Contribution → Backend — G1 vertical slice

**Created:** 2026-11-12. **Status:** IMPLEMENTED AS REVIEW CANDIDATE; not independently reviewed or production/staging accepted.

## Branch dependency
- Base = PR #34 React Campaign Discovery, commit `f6f8f43a1654bfb93b6f2d89bd5e6de4aabfb53d`.
- PR #33 is the design baseline beneath PR #34; **no changes to either prior PR**, no merge into master.
- This PR should target PR #34's branch until #33→#34 are integrated in order. After each upstream merge, retarget the remaining stacked PR and reevaluate diff/CI.

## Implemented in this package
- Public `/campaigns/:slug` detail now presents a B2-inspired editorial layout, category/status, cover, Backend narrative, optional validated gallery (up to six URLs), funding simulation disclosure, verified-organization label, and sorted Backend milestones with loading/empty/error/retry states.
- DONOR-only `/campaigns/:slug/contribute` preserves existing protected routing, explicit no-real-payment disclosure, simulation acknowledgment, minimum amount validation, allowed-method selection, anonymous flag, optional 1,000-character message, request failure/retry with stable `Idempotency-Key`, Backend pending confirmation and cache invalidation. Confirmation stays visible if Backend updates a campaign to no-longer-accepting immediately after the successful request.
- A slug-keyed form prevents an earlier campaign's idempotency/result state leaking into another route.
- Backend calls are the **existing** `getCampaignBySlug`, `listCampaignMilestones`, `createDonation` functions. No new API endpoints, no schema changes, no fake runtime campaigns or donations. Browser QA's fixtures are **test-only**.
- New styles are restricted to `.campaign-flow` and imported through the allowed `src/main.tsx` entry. Homepage B2 v1.3.2, Campaign Discovery design, mobile visual development and unrelated pages remain untouched.

## Contract and security
- Public list: `GET /api/v1/campaigns?status=ACTIVE&page=0&size=9`.
- Detail: `GET /api/v1/campaigns/slug/{slug}`. Milestones: `GET /api/v1/campaigns/{campaignId}/milestones`.
- Donation: `POST /api/v1/campaigns/{campaignId}/donations`, authenticated with `DONOR` role; `Idempotency-Key` stable across identical retries. No actual payment gateway and no persistence of access tokens in localStorage.
- Frontend client validation supplements but does **not** replace Backend validation, authorization, idempotency, state checks, or Admin confirmation.
- UI confirmation reports the **Backend** `PENDING` state; it never claims that funds have been received.
- The visual cover/thumbnail is never presented as independent evidence of completion.

## Test checklist
1. `cd rise-ai-frontend && npm ci && npm run verify`.
2. Install test-only Chromium tooling: `npm install --no-save --no-package-lock playwright@1.56.1` and `npx playwright install chromium` (plus system dependencies when required).
3. `node scripts/qa-campaign-flow-1440.mjs`: real Vite/React route at **1440×900 DPR 1**, request interception **only in the test**. Inspect `qa-evidence/campaign-flow/qa-report.json` and three actual screenshots of Detail, Contribution, Confirmation.
4. Run **separate real Backend QA** against isolated Spring Boot/MySQL with an authorized QA DONOR account; verify ACTIVE published campaign, populated milestones, simulated donation POST `201/200`, `PENDING` state, list update, duplicate/idempotency behavior, invalid auth/roles/amounts and closed-campaign protections. Do not perform writes against production.
5. Independently inspect screenshots for readability, CTA visibility, no overflow, keyboard focus, Page 404/503, donor login redirect and full flow. Keep device scope Desktop 1440.
6. GitHub-hosted Actions are currently **blocked by repository-owner billing** before runner assignment. Local reports cannot turn those checks green; do not claim Actions PASS.

## Merge gates
- Independent reviewer approval for PR #33 and #34, then this PR.
- CI restored, or an explicitly authorized policy-compliant alternative documented by the owner; no bypass of required checks.
- Actual real Backend E2E PASS and owner acceptance of React screenshot.
- Merge with **Merge commit** in dependency order, no force pushes or small standalone commits.
