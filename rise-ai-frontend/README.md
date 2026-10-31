# Rise AI — Frontend (FE-00)

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
Home (featured ACTIVE campaigns), public campaign discovery with query/category filters and pagination, public campaign detail with milestone timeline and simulated funding figures, login and an authenticated **placeholder** workspace. No donor payment, evidence upload, admin approval or AI-action screens are claimed as finished. Donations/escrow are simulated.

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
