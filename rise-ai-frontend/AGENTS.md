# FE-00 agent instructions — binding for Codex / Antigravity / Gemini

1. This is Rise AI, a charity platform. **No SaaS**, subscription, hotel, fake payment success, on-chain claim, or automatic AI approval.
2. The backend owns money, permissions and state transitions. Only call its `/api/v1` routes through `src/api/client.ts`. Never call Monitoring `:8081` from browser.
3. Keep **layered source structure**: `src/app`, `src/api`, `src/auth`, `src/pages`, `src/components/ui`, `src/components/layout`, `src/lib`, `src/styles`. No folder combining one entity's controller/service/repository/components/pages.
4. Source of truth: `../rise-ai-backend/docs/api/openapi-v1.json`. Never invent paths, request fields, role names or API responses. No duplicated snapshot in frontend.
5. UI colors, spacing and typography come from `src/styles/tokens.css`. Reuse `Button`, `Card`, `Container`. No inline colors, random UI libraries, MUI, Next.js or Redux.
6. Authentication uses memory-only token for FE-00. Refresh/reload may log out; do not silently add localStorage/sessionStorage. Backend controls authorization.
7. Every feature implements loading, empty, error, success states; never show fake fetched content as real.
8. Scope every PR, do not touch Backend/Monitoring. If Backend contract is insufficient, document a blocker instead of modifying it.
9. Run `npm run verify`; attach actual outputs/screenshots, never claim PASS without execution. No direct push or merge to `master`.
10. Follow docs/ARCHITECTURE.md and docs/PROGRESS.md; update progress only with evidence.

## Git history discipline (2026-10-31)
- Prefer one meaningful commit per scoped task, not one commit per styling tweak, test rerun, or documentation update.
- Accumulate related changes, run checks, then commit together; squash merge PRs into `master` after review.
- Never force-push, rewrite collaborators' commits, or commit directly to `master`.
- Progress entries must reflect actual evidence; keep commit history concise without sacrificing traceability.

## FE-01 public website rules
- Campaign discovery must query Backend with `status=ACTIVE`; search `q`, `category` and page are server-side.
- Rendering `funding.netRaised` and milestone released amounts must always label data as **simulation**.
- Use only public Backend responses. Never expose admin findings, disputes or creator-only private data on public pages.
- Never invent customer testimonials, fundraising statistics, named beneficiaries, evidence verification, or paid contributions.
- Leave the donation transaction and refund flows for FE-02. No dead payment buttons.

## FE-02 donor data and simulated money
- A donor's newly created donation is PENDING, **never** show a real payment success or assert it is confirmed. Admin alone confirms or fails a simulated donation.
- Only `DONOR` users can access donor screens, via RequireAuth + RequireRole; backend authorization is always authoritative.
- No real-money checkout, card inputs, bank instructions, automatic refunds, self-approval or AI-triggered transactions.
- Refund requests can be submitted for eligible confirmed donations; only Admin approves/records. A recorded refund is simulated ledger data, not a real transfer.
- Disputes require a confirmed donor contribution per Backend business checks. Admin resolves; donor may send messages/withdraw when allowed.
- Private query-cache records must be account-scoped and cleared at login/logout.
- Use `Idempotency-Key` to prevent duplicated donation/refund submissions on retry.
