# Rise AI — Frontend (FE-00 through FE-05 accepted; FE-06 release candidate)

A lean, **non-SaaS**, charity transparency web application. React + TypeScript + Vite + Tailwind CSS 4.
This UI only calls the Spring Boot **Backend** (`/api/v1`); it never calls AI Monitoring directly.

## Local development (from repository root)
```bash
cd rise-ai-frontend
npm ci
npm run verify
npm run dev
```
Start Backend on port 8080 first. Frontend runs on http://localhost:5173. If Backend is down, public campaign pages show an explicit error rather than invented data.

The committed `package-lock.json` makes installs reproducible via `npm ci` in local development and GitHub Actions. The release gate is `npm run verify` and GitHub CI, plus separate manual browser smoke with a running Backend.

## What exists
Implemented through FE-05: public campaign discovery and timeline, authenticated Donor/Creator experiences, Admin review and simulated escrow, independent human review of Monitoring findings, read-only Audit, Outbox/Alert delivery diagnostics. Financial values and transactions remain **simulation only**; no real payment gateway, evidence binary upload or autonomous AI decision is implemented. FE-06 adds release packaging, smoke and handoff, not a new product module.

## Contracts
Backend is the sole contract source: `../rise-ai-backend/docs/api/openapi-v1.json`.
`npm run contract:generate` generates optional full type declarations (ignored until reviewed).
`npm run contract:check` validates the FE-00 subset and required fields; it does **not** prove E2E compatibility.

## Architecture
Read [AGENTS.md](AGENTS.md), [Architecture](docs/ARCHITECTURE.md), [Design system](docs/DESIGN_SYSTEM.md), [Progress](docs/PROGRESS.md) before coding.
Never copy hotel-specific components, MUI, session storage or business workflows from the separate Advanced-Web-Programming reference.

## FE-01 — Public website & campaign discovery
- Home: mission/process narrative + latest ACTIVE campaigns from Backend.
- Discovery: search (`q`), category and pagination, all server-side; only `ACTIVE` campaigns requested.
- Detail: slug, organization, funding summary, long-form story and public milestone timeline.
- Pages always show explicit loading, empty and API error states; no static fake campaigns or invented transactions.
- **No donation form in FE-01**: that requires separate FE-02 business acceptance. The current app does not transfer money.
- See [FE-01 acceptance checklist](docs/FE01_ACCEPTANCE.md) for real browser testing.
- When running Backend at 8082 (8080 is used by another local app), put `VITE_API_BASE_URL=http://localhost:8082` in untracked `.env.local`, then restart Vite. The normal development proxy still points to 8080.

## FE-02 — Donor Experience (simulation only)
- `/campaigns/:slug/contribute`: restricted to logged-in `DONOR` role; create a **PENDING** simulated donation with an idempotency key. No real payments or automatic confirmation.
- `/donor`: private paginated donation/refund/dispute history; cancel `PENDING` donations, request refunds for `CONFIRMED` / `PARTIALLY_REFUNDED` donations, cancel only `REQUESTED` refunds; open an eligible dispute.
- `/donor/disputes/:id`: private dispute thread and supplementary messages / withdraw while eligible.
- Auth remains memory-only; logout and login clear the TanStack Query cache to prevent personal records leaking between accounts.
- Backend enforces ownership and permissions on every request. The UI does **not** confirm, approve, or transfer funds.
- [FE-02 acceptance checklist](docs/FE02_ACCEPTANCE.md) documents real backend/browser checks that must pass before merge.

## FE-03 — Creator Experience (frontend-only)
- `/creator`: private Creator organizations (create/edit, submit verification) and owned campaign listings.
- `/creator/campaigns/new`, `/creator/campaigns/:id/edit`: complete OpenAPI-defined campaign draft form. Existing budgets are preserved on edit; all amounts are simulated VND.
- `/creator/campaigns/:id`: owner-only management; milestone budget and deadline editing, submit for Admin review, publish only after APPROVED. Evidence is **reference metadata** (description, real HTTPS/sim URL, optional actual SHA-256) — **no file upload**, no fabricated hash.
- Disbursement requests are created with idempotency keys only when Backend state allows. Admin solely reviews, accepts evidence and records simulated disbursement.
- [FE-03 acceptance](docs/FE03_ACCEPTANCE.md) explains the prerequisites and browser verification. FE-00 memory-only auth and FE-02 donor routes remain unchanged.

## FE-04 — Admin Experience (simulation only)
- `/admin`: Admin-only statistics and paginated, status-filtered review queues for organization verification, campaigns, donations, refunds, disbursements, disputes and ledger adjustment/settlement proposals.
- `/admin/review/:kind/:id`: Backend-sourced detail plus state-aware actions. Explicit confirmation + reason when required. Disputes must be assigned to a legitimate active Admin before resolution; ownership and conflict checks are authoritative on Backend.
- Disbursement evidence is reviewed against the related milestone; approve or reject evidence metadata. **Never treat URL/hash as independent verification**.
- `/admin/escrow`: ledger account summary, global/per-campaign reconciliation and run history, view ledger entries and request simulated adjustment/settlement. Approving and recording stay separate Backend-controlled operations.
- No actual money transfer, no auto-approve, no source edits to Backend/Monitoring. AI findings/audit were added in the separately accepted FE-05.
- FE-04 was accepted and squash-merged through PR #5; see [historical FE-04 acceptance evidence](docs/FE04_ACCEPTANCE.md).

## FE-05 — AI Monitoring & Audit Experience (Admin-only)
- `/admin/monitoring`: paginated, filterable findings, severity, category, campaign and review-status filters; rule precision from Backend. **Advisory only:** no automatic accusation, campaign block or financial action.
- `/admin/monitoring/findings/:id`: detail, facts, occurrences, source IDs, AI notes, review history and human decisions. Backend enforces independent Admin reviewer; note and confirmation required.
- `/admin/audit`: read-only Backend audit history with actor/entity/action filters, pagination and text-only metadata.
- `/admin/monitoring/delivery`: outbox/alert delivery stats and statuses, with deliberate FAILED-only requeue. Requeue is **not** delivery success.
- Browser calls the **Backend only**, never direct Monitoring endpoints. [FE-05 acceptance](docs/FE05_ACCEPTANCE.md) is the merge gate.
- No new SaaS dashboard design; distinct UI/UX refinement and multiple concept proposals are scheduled **after FE-06**.

## FE-06 — Final E2E, staging deployment & handoff (Draft)

- Release-quality SPA build: `npm run verify` (now includes `npm run release:check`), Backend OpenAPI checks and 375px regression expectations.
- Local-only Docker/Nginx staging with **same-origin** `/api/v1` reverse proxy, health endpoint and deep-link fallback; no direct Monitoring calls or real payments.
- Read-only deployed-instance smoke: `npm run deploy:smoke` with an explicit `RISE_FE06_BASE_URL` and (for container) `RISE_FE06_TARGET=docker`.
- See [FE-06 acceptance](docs/FE06_ACCEPTANCE.md), [deployment and rollback](docs/DEPLOYMENT_RUNBOOK.md), [developer/operator handoff](docs/FINAL_HANDOFF.md).
- **Production release is not automatic**: an actual approved hosting target, reachable Backend, TLS and verification are required. Do not treat a localhost container or CI build as a public deployment.
- The user-approved non-SaaS UI/UX audit, multiple concept comparison and visual rework starts **after FE-06**, not inside it.
