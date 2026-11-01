# Rise AI — FE-06 final developer/operator handoff

**Handoff is DRAFT until FE-06 CI, real Chromium E2E, deployment smoke and post-merge checks complete.** Do not confuse documented run commands with evidence they were run.

## Scope / authority
- Repository: `TronkIshere/rise-ai`, branch `master`; FE work confined to `rise-ai-frontend/**`.
- Tech: React 19, Vite 6, TypeScript 5, Tailwind 4, TanStack Query, React Router; Node >=22.12 and `npm ci`.
- API contract source: `rise-ai-backend/docs/api/openapi-v1.json` (111 paths at FE-05 baseline). Browser calls the Spring Boot Backend only.
- Memory-only access tokens: reload may log out; login/logout must purge private query caches. Backend authorizes every sensitive action.
- All donation, refund, escrow and disbursement amounts are **simulated**. AI findings are advisory. The frontend does not transfer money, verify evidence files cryptographically or decide fraud.

## Functional route index
- Public: `/`, `/campaigns`, `/campaigns/:slug`, `/login`.
- DONOR: `/campaigns/:slug/contribute`, `/donor`, `/donor/disputes/:id`.
- CAMPAIGN_CREATOR: `/creator`, `/creator/campaigns/new`, `/creator/campaigns/:id/edit`, `/creator/campaigns/:id`.
- ADMIN: `/admin`, `/admin/review/:kind/:id`, `/admin/escrow`, `/admin/monitoring`, `/admin/monitoring/findings/:id`, `/admin/monitoring/delivery`, `/admin/audit`.
- `/workspace` authenticated; all others use Backend and router guards according to role.

## Key files / responsibilities
- `src/app/router.tsx`: routing and access guards.
- `src/api/client.ts`: centralized API envelope handling and memory-only token.
- `src/api/*.ts`: typed domain calls (never fetch directly from UI).
- `src/auth/`: session and guard logic.
- `src/pages/`, `src/components/`, `src/lib/`: composition and read-only display utilities.
- `src/styles/`: existing tokens/design system (visual redesign scheduled separately).
- `scripts/verify-contract.mjs`, `scripts/*.test.mjs`: architecture, contract and static regression gates.
- `Dockerfile`, `deploy/default.conf.template`, `compose.frontend.yml`: packaged frontend, Backend proxy and local-only staging.
- `docs/FE01_ACCEPTANCE.md` … `docs/FE06_ACCEPTANCE.md`: phased evidence and gates.
- `docs/DEPLOYMENT_RUNBOOK.md`: safe deploy, smoke and rollback.

## Build and test
```bash
cd rise-ai-frontend
npm ci
npm run verify
```
Check both Frontend and root CI on the **same commit**. Real browser tests must run against actual isolated Backend+MySQL, not just mocked API, and produce redacted screenshots/HTTP/traces. Read-only released-instance smoke: `RISE_FE06_BASE_URL=http://127.0.0.1:5174 RISE_FE06_TARGET=docker npm run deploy:smoke` on Linux/macOS or the PowerShell steps in the runbook.

## Operator checklist
- Know the environment owner, Backend URL, allowed origins/TLS, database isolation, Monitoring relay configuration and emergency rollback owner before release.
- Pin the tested Git SHA and verify only frontend files changed in FE-06.
- Do not add user secrets to Git, browser storage, build-time Vite variables, or exported Playwright traces.
- Release remains blocked if Backend API unavailable, unauthorized Admin API returns 200, a simulated transaction is presented as real, or a 375px page has body overflow.
- `git switch master && git pull --ff-only origin master` after approved squash merge; verify again.
- Do not merge a Draft FE-06 PR on source CI alone. Final handoff status updates require evidence links and commit.

## Deferred UI/UX work — explicitly outside FE-06
After FE-06 completion, audit current UI for SaaS-like design drift against `docs/DESIGN_SYSTEM.md`, `AGENTS.md` and user-facing needs. Generate multiple independently recognizable concepts (Community First, Impact Storytelling, Transparency First), compare hierarchy/navigation/legibility/trust/mobile layouts, obtain user selection **before implementation**. Preserve all accepted Backend contracts and business flows. No FE-07 feature scope has been authorized.
