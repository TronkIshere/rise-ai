# FE-06 — Final E2E, release validation, deployment and handoff

**Status: DRAFT / NOT ACCEPTED.** Scope: frontend files only. FE-00–FE-05 functionality is already accepted on master at `2480b598`. FE-06 is the technical-release gate, **not** a new UI feature. Do not add SaaS styling or redesign: the user specifically requested multi-concept UI/UX review and rework **after FE-06**.

## Verified baseline
- FE-05 PR #6 squash merged to master `2480b598efbba95cafd6c53de60147f9b0bae7ad`.
- FE-05 browser report: 40/40 final cases passed (36 real integration, 4 separately labelled mocked negative cases); security/review and responsive coverage.
- Post-merge master workflows: Frontend `41323547885`, CI `41323547921` (Backend, Monitoring and Images) completed successfully.
- This historical evidence **does not replace FE-06 deployment-specific and cross-module browser checks**.

## FE-06 deliverables
- Reproducible frontend build with `npm ci` and `npm run verify`; release checks validate built `dist`, SPA routing and same-origin Backend proxy config.
- Local/staging Docker/Nginx **frontend-only** compose, defaults to loopback port 5174 and requires trusted `RISE_FRONTEND_BACKEND_UPSTREAM`. No database or payment gateway is started by frontend Compose. A separate Backend and DB must be running.
- Read-only live-smoke script validates SPA deep links, public Backend campaign API, anonymous ADMIN denial. No injected credentials, browser-storage persistence, real-money actions or fabricated monitoring.
- Deployment and rollback runbook, operator/developer handoff and future non-SaaS redesign boundary.
- Independent Playwright/Chromium E2E against disposable MySQL 8.4 + Backend Java 21 + real Monitoring where necessary. No user-interface redesign, Backend/Monitoring source changes or production publishing during this Draft PR.

## CI/static acceptance
- [ ] `npm ci && npm run verify` PASS including architecture, OpenAPI contract, lint, TypeScript, unit regression tests, build **and** FE-06 release check.
- [ ] `docker compose -f rise-ai-frontend/compose.frontend.yml config` succeeds only with `RISE_FRONTEND_BACKEND_UPSTREAM` supplied.
- [ ] `docker compose -f rise-ai-frontend/compose.frontend.yml up -d --build` launches frontend with /healthz HTTP 200, same-origin API proxy, SPA deep links and no leaked build secrets.
- [ ] Frontend push/PR, Backend/Monitoring/Images checks PASS on same final PR head; only `rise-ai-frontend/**` files modified.
- [ ] All publicly deployed URLs and credentials verified against actual infrastructure. **Do not mark a production deployment PASS merely because the Docker staging container works.**

## Real E2E acceptance matrix
Use a **disposable** MySQL 8.4 database, Java 21 Backend, real Monitoring when available, Chromium/Playwright; do not mutate production or use real payments.
- [ ] Public: home, campaign search and detail load via Backend, pagination and milestone information; missing/back-end-down error handling.
- [ ] Auth: login for DONOR / CAMPAIGN_CREATOR / ADMIN, logout, memory-only session on reload, no stale private-query cache.
- [ ] Donor: PENDING simulated contribution, history, cancel, refund and dispute requests, messages, ownership protection; no false success real-payment wording.
- [ ] Creator: organization and campaign draft, submission, milestones, evidence **reference metadata**, disbursement proposal; owner cannot self-approve.
- [ ] Admin: review queues and status transitions, simulated donation/refund/disbursement, dispute evidence and actor conflict, escrow 8-component reconciliation, adjustment CREDIT/DEBIT and independent approval. No actual transfers.
- [ ] Monitoring: real Backend findings and independent human review, full facts/occurrences/history, precision; AI always advisory. Audit search/pagination, failed-only Outbox/Alert retries (PENDING is not SENT).
- [ ] Anonymous/DONOR/CREATOR cannot enter Admin paths or fetch ADMIN APIs; deliberate API requests return 401/403. Do not weaken Backend authorization to make E2E green.
- [ ] 375×900 and 1440×900: public and authenticated pages, admin Escrow, monitoring detail, audit and delivery. `document.documentElement.scrollWidth <= innerWidth`; wide escrow tables scroll internally.
- [ ] Keyboard labels, long/error text escaped, no runtime console errors, loading/empty/network errors; no direct browser calls to Monitoring service or credential exfiltration.
- [ ] Production bundle/preview: browser connected through **same-origin proxy**, deep-linked page refresh returns SPA, all asset requests load; unauthenticated /api/v1/admin monitoring is denied.
- [ ] The Docker build's separate named `backend_contract` build context supplies the authoritative Backend OpenAPI to `npm run verify`; do not duplicate contract snapshots. Test Docker build against the actual monorepo checkout.
- [ ] Backend CORS allowlist explicitly includes the local staging origin (e.g. `http://127.0.0.1:5174`) through its documented Spring configuration, without wildcard origins or disabling security. Confirm an OPTIONS preflight for login succeeds, and actual Chromium login+mutations work through Nginx.
- [ ] Execute `npm run deploy:smoke` against an actual running release with `RISE_FE06_BASE_URL`, `RISE_FE06_TARGET=docker`. Include screenshot, request log and http/DOM evidence.
- [ ] Ensure no existing FreelaX, shared MySQL or Redis Docker resources were stopped, deleted or modified.

## Decision gates
1. Codex report must identify **exact SHA**, environment, real vs mocked test counts, screenshot/trace paths, endpoint statuses, layout measurements and a PASS/FAIL/BLOCKED matrix, plus unambiguous **MERGE READY / NOT READY**.
2. Docker staging requires backend reachability and complete smoke. **External/production deployment remains separately gated** on a selected target, actual routable Backend URL or trusted network proxy, domain/TLS, secrets/CORS and owner approval.
3. Code review + final-head CI PASS + browser/staging acceptance → mark Draft PR Ready → squash merge → verify post-merge `master` Frontend/Backend/Monitoring/Images CI.
4. Capture tested and merged SHAs in `docs/PROGRESS.md` and final handoff. Do not claim release complete before these gates.

## After FE-06
Start the user's requested **non-SaaS, community-centered UI/UX review**: audit existing screens, compare at least three concept directions for discovering campaigns, impact storytelling and transparency, select one, then redesign without changing Backend contracts. This work is **separate** from FE-06.
