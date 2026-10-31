# FE-02 — Donor Experience acceptance

**Implementation branch, not accepted or merged.** Scope strictly frontend; source of truth is `rise-ai-backend/docs/api/openapi-v1.json`.

## Contracts and state transitions
- `POST /api/v1/campaigns/{campaignId}/donations`: `DONOR` only; creates **PENDING** simulated donation. Uses an idempotency key; no money transfer and no auto-confirm.
- `GET /api/v1/donations/me`, `POST /api/v1/donations/{id}/cancel`: personal paginated list and donor cancellation only while PENDING.
- `POST /api/v1/donations/{donationId}/refunds`, `GET /api/v1/refunds/me`, `POST /api/v1/refunds/{id}/cancel`: only confirmed/partly refunded donations are eligible; Admin approves and records; REQUESTED can be cancelled by requestor. Refund is ledger-only simulation.
- `POST /api/v1/campaigns/{campaignId}/disputes`, `GET /api/v1/disputes/me`, `GET /api/v1/disputes/{id}`, message GET/POST, withdraw POST: requires an eligible confirmed contribution; Admin reviews and resolves.
- Never browse the private Monitoring service from FE; only the main Backend :8082.
- Refresh logs out by design (memory-only auth), no persistent token storage.

## Automated gates
- [ ] `npm ci && npm run verify` on Node 22 locally or CI; contract test and architecture guard green.
- [ ] PR Frontend checks + existing Backend/Monitoring workflow jobs green on the *final* commit (or independently explain any external CI failure).
- [ ] No changes to Backend, Monitoring, deployment or frozen API schema.
- [ ] No invented amounts, paid confirmations, authority or payment methods.

## Browser smoke — local Windows
Start MySQL `docker compose up -d` from Backend; start backend with
`$env:SERVER_PORT="8082"; $env:RISE_MONITORING_ENABLED="false"; mvn spring-boot:run`.
Keep `VITE_API_BASE_URL=http://localhost:8082` in ignored Frontend `.env.local` and `npm run dev`.

- [ ] Anonymous user clicking "Ghi nhận đóng góp mô phỏng" on an ACTIVE accepting campaign is redirected to `/login`, then returned to the intended contribution route.
- [ ] Donor demo `donor1@riseai.example` / `Demo@12345` opens `/donor`. Creator/admin accounts cannot open donor-only pages.
- [ ] Submit simulated donation with valid integer VND amount; API returns PENDING; **do not** claim paid/confirmed. Retry from lost-connection state does not produce duplicate record.
- [ ] Donor's /donor history shows the new record and status; cancel a new PENDING donation and confirm status changes to CANCELLED.
- [ ] Existing seeded CONFIRMED donation: request a valid refund once; status REQUESTED. Admin decision not available in FE-02. Cancel a REQUESTED refund, then verify status update.
- [ ] Dispute: select a campaign with a confirmed donation from the visible history page, submit title/type/details; Backend accepts if eligible; thread loads, supplementary message is sent and recorded; withdraw only if allowed. On errors, show Backend message without fake record.
- [ ] Browser route guards, login/logout cache isolation, 375px/1440px responsiveness, keyboard and Console.
- [ ] Disconnected Backend and zero personal records render recoverable errors/empty states.
- [ ] FE-01 home/catalogue/detail and FE-00 auth smoke remain unaffected.

**Important:** donor/creator/Admin behavior should be independently checked using separate sessions. Do not confirm transactions outside authorized backend admin workflows just to pass FE-02.

## Out of scope
Real payment, real refunds, payment gateway, AI approvals, Admin/Creator pages, binary evidence, Backend/Monitoring edits and production deployment.

## Gate to merge
Mark PR Ready only after browser smoke + CI evidence; squash merge to `master` keeping one FE-02 commit. Any missing checks remain NOT VERIFIED.
