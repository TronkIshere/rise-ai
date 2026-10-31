# FE-01 — Public website acceptance (manual + CI)

Status: **IMPLEMENTED IN BRANCH / NOT ACCEPTED** until evidence below.

## Scope and source of truth
- Public routes: `/`, `/campaigns`, `/campaigns/:slug`.
- Backend OpenAPI: `rise-ai-backend/docs/api/openapi-v1.json`.
- Supported calls: `GET /api/v1/campaigns?status=ACTIVE&q=...&category=...&page=...&size=...`,
  `GET /api/v1/campaigns/slug/{slug}`,
  `GET /api/v1/campaigns/{campaignId}/milestones`.
- Neither private Monitoring service nor donation/escrow mutations are called from this UI.
- No per-entity folders; reuse `src/components/ui`, `src/pages`, `src/api` and design tokens.

## Automated gates
1. `npm ci` from `rise-ai-frontend` on Node 22.
2. `npm run verify` — architecture / API contract / lint / TS / basic tests / production build.
3. GitHub Frontend checks and existing Backend/Monitoring workflows pass on the FE-01 branch.
4. Frontend CI is static/API-contract validation, **not** a substitute for a browser E2E run.

## Browser smoke with real seeded Backend
Start MySQL, then Backend (if port 8080 is already used by FreelaX, use
`$env:SERVER_PORT="8082"; mvn spring-boot:run`). Set untracked
`rise-ai-frontend/.env.local` to `VITE_API_BASE_URL=http://localhost:8082`, then `npm run dev`.

- [ ] Home: hero, transparency explanation, 3 featured ACTIVE demo campaigns and no invented real-money claims.
- [ ] Discovery: loads exactly 3 ACTIVE demo campaigns in initial seed; image unavailable fallback is readable.
- [ ] Search: `q` updates URL and results server-side, including no-results state.
- [ ] Categories: filter updates URL; reset clears filters; pagination preserves search/category.
- [ ] Campaign detail: slug link works; cover image or fallback, real funding/netRaised, organization and category shown.
- [ ] Milestones: actual milestones sorted by sequence; separate loading/empty/error states and status labels.
- [ ] Backend disconnected: readable error instead of fake entries; retry works after reconnection.
- [ ] Mobile viewport ~375px and desktop ~1440px: no horizontal overflow; menu keyboard operable.
- [ ] Keyboard navigation and focus indicators visible; no critical Console errors.
- [ ] Existing FE-00 login/workspace route remains functional.
- [ ] No new functionality calls payment, Monitoring 8081, or admin endpoints.

Do **not** label FE-01 PASS solely because TypeScript/build succeeds. Record actual browser findings before squash merge.

## Out of scope
- Real-money payments, payment gateway integration, on-chain proof, evidence binary upload
- Donor action/donation/refund flow (FE-02)
- Creator/Admin/AI review dashboards (FE-03–FE-05)
- Reworking backend or monitoring
