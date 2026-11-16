# Rise AI — Dashboard Suite: DONOR / CREATOR / ADMIN (Desktop 1440)

**Date:** 2026-11-12. **Status:** IMPLEMENTATION CANDIDATE / NOT QA PASSED. **Scope:** dashboards only, no mobile UI development.

## Dependency
- Based on PR #35 at `94e286baa507c977073bbd47d6796456c3589670` (which depends on PR #34 → PR #33).
- Keep this work stacked on PR #35 until its ancestors are integrated. Reviewer must verify only the resulting diff after retargeting.
- Homepage B2 v1.3.2, public Campaign Discovery, Campaign Detail, Contribution, Backend and API contracts are **unchanged**.

## Baseline audit
- Existing `/donor` already had API-backed paginated donations/refunds/disputes, donor cancellation, refund requests and complaint creation, but lacked a concise status overview. Dispute creation only found eligible campaigns on whichever donation-history page happened to be selected; users on the disputes tab had no pagination affordance for locating eligible records on other pages.
- Existing `/creator` already had organization CRUD/verification requests and paginated campaign management, but lacked a clear organization/campaign overview and robust edit/form state reset.
- Existing `/admin` already had statistics, queue navigation, status filters and links to privileged workflows. The statistics card had no retry path and refreshing the queue did not refresh global statistics.
- Existing role guards remain **frontend affordances only**; Backend must independently enforce authorization.

## Included changes (grouped dashboard-only package)
- A scoped B2-aligned sage/forest/cream dashboard system with illustrated gradient landscape, paper cards, clean summary metrics, navigation states, readable controls, and consistent hierarchy for the 1440px baseline.
- DONOR: total count directly from Backend `totalElements` (otherwise `—`), **per-page** count of PENDING and counted donation statuses explicitly labeled, section/ARIA tab relationships; dispute campaign selector now lets users page through their own donations without leaving Disputes; idempotency key map retains a refund payload's key across an A → B → A edit/retry while the outcome remains uncertain.
- CREATOR: Backend total organization and campaign counts (otherwise `—`), current-page verified count; trimmed outgoing organization form data; reset stale mutation errors when reopening the form and prevent dismissing during submission.
- ADMIN: visual treatment of backend statistics/queues, stats-specific retry, one refresh control for both stats and selected queue, pressed-state semantics on queue selectors.
- No fake runtime records, extra packages, renamed route, API endpoint additions, database migrations, or new role permissions.

## Existing Backend endpoints, unmodified
DONOR: `GET /api/v1/donations/me`, `GET /api/v1/refunds/me`, `GET /api/v1/disputes/me`, `GET /api/v1/campaigns/{id}`, `POST /api/v1/donations/{donationId}/refunds`, `POST /api/v1/campaigns/{campaignId}/disputes`.
CREATOR: `GET /api/v1/organizations/mine`, `POST /api/v1/organizations`, `PUT /api/v1/organizations/{id}`, `POST /api/v1/organizations/{id}/submit-verification`, `GET /api/v1/campaigns/mine`.
ADMIN: `GET /api/v1/admin/statistics`, `GET /api/v1/organizations`, `GET /api/v1/admin/donations` and remaining queue endpoints as defined in the API contract. Admin approval actions live on existing `/admin/review/:kind/:id` pages and are not silently performed on a dashboard list.

## QA gates (do not claim PASS until executed)
1. On the exact new HEAD: `cd rise-ai-frontend && npm ci && npm run verify`. Check architecture, contract, lint, TS, unit, build/release.
2. Browser: `npm install --no-save --no-package-lock playwright@1.56.1`; `npx playwright install chromium`; `node scripts/qa-dashboard-suite-1440.mjs`. Review result JSON and actual **three** 1440x900 DPR1 screenshots in `qa-evidence/dashboard-suite/` (DONOR, CREATOR, ADMIN). Fixture-only API responses must never be passed off as real Backend integration.
3. Run isolated **real Spring Boot/MySQL QA** with three separate demo role identities. DONOR: paginated history, other-page dispute eligibility, refund submit/retry with stable idempotency key, 401/403/422 checks. CREATOR: organization create/update, request verification (pending, not automatically verified), campaign pagination. ADMIN: statistics, status-filtered queues, 401/403 across roles, review screen only allows authorized actions, ensure requester cannot self-approve and simulated funds stay simulated. No production writes.
4. Regression test Homepage B2 v1.3.2, Campaign Discovery `/campaigns`, Detail, Contribution, and role redirects; verify no cross-page CSS leakage and DevTools console/network.
5. Reviewer manually verifies actual 1440px screenshots, keyboard focus, long/sparse/error states and Backend-specific numbers. Flag mismatches; do not fabricate screenshots.
6. GitHub Actions currently fail before runner allocation due a previously confirmed billing/spending block. Re-run the same commit once owner restores runner access; never call this FAIL a proven code regression without actual logs.

## Integration policy
PR chain: `#33 Design → #34 React Discovery → #35 Detail/Contribution → dashboard suite PR`. Merge with **Merge commit** only after independent APPROVED and necessary CI/owner gates. No force push; do not merge automatically.

## Limitations / follow-up
- Dashboard summary uses API `totalElements` and current-page records; it is **not** a cross-page financial aggregate. In particular, pending/verified counts reflect only the page visible on DONOR and CREATOR.
- Dispute eligibility browsing is explicit page-by-page; it does not aggregate all confirmed donor records in a single call, nor can frontend eligibility replace Backend authorization.
- Visual rendering and real Backend QA are pending until Codex or another reviewer runs the acceptance suite on a configured local QA environment.
