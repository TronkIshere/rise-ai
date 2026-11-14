# Rise AI — Full functional-page UI batch, Desktop 1440

**Date:** 2026-11-12 · **Scope:** one complete UI batch for the existing deep routes and the three auxiliary account/error routes. **Status:** implementation candidate, **NOT visually accepted / NOT local QA passed**.

## Design direction and provenance

Owner explicitly requested a **single batch** to complete the remaining Frontend pages in the accepted B2 visual language, rather than incremental wireframes or a SaaS dashboard redesign. The source reference is the already accepted **Homepage B2 v1.3.2 and Campaign Discovery Paper Dossier**; this batch continues their forest-green typography, sage landscape surfaces, warm editorial paper, and high-contrast CTAs at 1440px.

Change control:
- **Unchanged/locked:** Homepage B2 v1.3.2, Campaign Discovery HTML/React, Campaign Detail, Contribution, DONOR/CREATOR/ADMIN Dashboard Suite (PR #36). Their route markup, CSS and APIs are untouched. SiteShell navigation/header/footer are preserved as a deliberate compatibility boundary.
- **Changed:** pre-existing deep functional pages only, plus Login, Workspace and NotFound.
- **Approved token authority:** `src/styles/tokens.css` only. The new route-scoped `functional-pages-b2.css` references canonical tokens or `color-mix()` derived from them, without introducing hard-coded runtime HEX colors, imported external fonts, new dependencies or an independent palette.
- **Visual differences:** a clearly scoped functional-page mint paper landscape, folio labels, cream dossier panels, stronger section headings and keyboard focus; login uses a forest landscape editorial split; workspace shows role-aware dossier cards; 404 uses a paper map/compass vignette.
- The source approval for this direction does **not** imply visual acceptance. Actual React screenshots at 1440×900 must be checked before any PASS assertion.

## Affected routes (all existing, no route additions)
| Route | Scope |
|---|---|
| `/login` | Forest editorial split layout; preserved memory-only login and return-to-deep-link after successful authorization. |
| `/workspace` | Role-aware editorial DONOR, CREATOR and ADMIN cards; preserved links and guards; no fabricated metrics. |
| `/donor/disputes/:id` | Donor complaint dossier, messages, human-review information and actions; no API changes. |
| `/creator/campaigns/new` & `/:id/edit` | Campaign authoring form and sections, never self-approval; no request-schema changes. |
| `/creator/campaigns/:id` | Campaign management, milestones, evidence and simulated disbursement panels; no state changes. |
| `/admin/review/:kind/:id` | Review dossier and separate human decision column, with backend-owned actions. |
| `/admin/escrow` | Reconciliation, ledger entries and simulated adjustment/settlement proposals. |
| `/admin/monitoring` | Findings and precision. AI remains advisory, not an approval authority. |
| `/admin/monitoring/findings/:id` | Evidence/finding dossier and human review; no auto-conclusion. |
| `/admin/monitoring/delivery` | Event outbox/alert delivery tracking and manual retry flows. |
| `/admin/audit` | Append-only audit records from Backend and filter controls. |
| `/*` | 404 map-like state and links to Homepage/Discovery. |

## Quality / real Backend gate

**Before declaring PASS**, Codex on the Windows clone must:
1. Check out this PR head, clean worktree, `cd rise-ai-frontend && npm ci && npm run verify`. Capture exact logs: architecture, contract, lint, typecheck, unit tests, build and release check.
2. Run existing PR #34–36 regression suites, including the role Dashboards and Campaign/Contribution when the upstream test harness is available. Preserve prior approved UI.
3. Run **real Chrome/Chromium React Desktop 1440×900 DPR1**. Use `node scripts/qa-functional-pages-1440.mjs` with isolated QA/staging `RISE_QA_BASE_URL`, distinct role accounts and applicable QA record IDs. See below. Script has **NO API interception**; accesses actual Backend through the configured frontend origin.
4. Review each screenshot, console, network and `qa-report.json`. Reject clipping, missing data hidden as zero, unreadable type/contrast, long names pushing controls offscreen, inaccessible keyboard focus or any page not reachable through SPA navigation.
5. Complete meaningful non-production operations: DONOR reply/withdraw dispute where authorized; CREATOR create/edit organization, campaign and evidence/step where authorized; ADMIN read queue and make separate, permitted decision in isolated QA if needed to prove end-to-end. Confirm resulting API/database state and 401/403 cross-role rejections, no self-review, no live money. **Do not modify production state**.
6. Where a QA record or account is unavailable, report **BLOCKED**, not PASS. Do not invent fixtures or claim they were live Backend.
7. Reproduce any actual failures before editing; make scoped fixes, rerun impacted + global suite, group fixes in one commit, upload actual screenshots and report to PR. Only after independent visual approval and all relevant tests PASS can the owner lock the revision.

### Example environment variables

Set `RISE_QA_BASE_URL` (e.g. `http://127.0.0.1:5173` with Vite proxy to isolated Spring Boot), plus `RISE_QA_DONOR_EMAIL/PASSWORD`, `RISE_QA_CREATOR_EMAIL/PASSWORD`, `RISE_QA_ADMIN_EMAIL/PASSWORD` and record IDs `RISE_QA_DISPUTE_ID`, `RISE_QA_CREATOR_CAMPAIGN_ID`, `RISE_QA_REVIEW_KIND`, `RISE_QA_REVIEW_ID`, `RISE_QA_FINDING_ID`. Never commit credentials or print them in logs.

Install browser QA tooling for the current environment as already used in PR #35/#36:
`npm install --no-save --no-package-lock playwright@1.56.1`, `npx playwright install chromium` (WSL Linux system deps if required), then `node scripts/qa-functional-pages-1440.mjs`.

Evidence target: `rise-ai-frontend/qa-evidence/functional-pages/qa-report.json` plus one **real React** screenshot per route. `PASS` requires no errors and no blocked records.

## Integration and project boundary

This is one **stacked** UI batch targeting PR #36 to avoid merge conflicts and overlapping edits. The complete integration chain is now `#33 → #34 → #35 → #36 → this PR`. All must receive independent review, owner approval and comply with branch protections. Use **Merge commits** as expressly requested by owner (repository's older generic FE instructions about squash are superseded by the explicit latest integration instruction), retarget and verify downstream after each upstream integration.

**Do not claim CI green:** GitHub Actions currently fails before runner start due to account-level billing/spend restriction previously documented. Local QA is useful evidence, not an implicit override of required hosted checks.
