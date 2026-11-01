# FE-05 — AI Monitoring & Audit Experience acceptance

**Status: ACCEPTED and MERGED** — PR [#6](https://github.com/TronkIshere/rise-ai/pull/6), squash commit `2480b598efbba95cafd6c53de60147f9b0bae7ad`. Codex browser 40/40 final PASS (36 real integration, 4 explicitly mocked negatives); post-merge Frontend/Backend/Monitoring/Images CI PASS. Checklist retained as historical test plan, not a fresh list of missing fixes. Scope: **`rise-ai-frontend/**` only**. Do not modify Backend, Monitoring, FE-04 logic or redesign UI; a separate UI/UX redesign is scheduled **after FE-06**.

## Scope and safety invariants

- `/admin/monitoring`: ADMIN-only Backend-sourced monitoring findings with status, severity, category and campaign filters; paginated, no mock alerts. Rule precision displays a denominator caveat (only confirmed vs dismissed decisions).
- `/admin/monitoring/findings/:id`: actual finding detail including full `facts`, rule identifiers, occurrences with event/actor/time/AI note, review history and existing review note. Render provided text as **text only**; no generated HTML or invented reasoning.
- Manual Admin review posts `ReviewFindingRequest {status,note}` to Backend. Allowed transitions: OPEN→ACKNOWLEDGED/CONCERN_CONFIRMED/DISMISSED; ACKNOWLEDGED→CONCERN_CONFIRMED/DISMISSED. Terminal statuses cannot be reviewed again. Backend blocks reviewer when actor was involved or owns the campaign; no automatic action on funds or campaigns. Require nonblank notes and an explicit acknowledgement. If detail/history load fails, no decision.
- `/admin/audit`: admin-only read-only search/pagination via existing Backend audit endpoint, filter by actorId, entityType, entityId, action. No deletion/export or inferred events. Treat metadata as untrusted content and render as escaped plain text.
- `/admin/monitoring/delivery`: Backend outbox and alert statistics; status-filtered, paginated records. For FAILED entries only, explicit manual requeue with nonblank reason and confirmation; do not claim queuing means delivered. Display relayEnabled/webhookConfigured and raw error information. Backend owns transitions and audit trail.
- All FE-05 APIs are via existing Spring Boot Backend `/api/v1/admin/**`. **Never** call the Monitoring microservice from the browser, add a simulated LLM, calculate or fabricate a risk score, or present an AI finding as conclusive misconduct.
- Security: ADMIN role gate on every route; authenticated API calls through existing memory-only token client; Backend enforces real authority. A DONOR/CREATOR/anonymous user must not access any FE-05 page or monitoring API.

## Contract and static gates
- [ ] `npm ci && npm run verify`: architecture, Backend OpenAPI (all 12 FE-05 endpoints and DTO fields), lint, TypeScript, unit tests, production build.
- [ ] FE-only changed files, no Backend/Monitoring code or schema modification.
- [ ] Frontend push + PR CI PASS on final feature SHA.
- [ ] Backend/Monitoring/Images CI PASS on same SHA.
- [ ] PR remains Draft until independent browser E2E confirms acceptance; no merge merely on CI green.

## Real browser E2E (Playwright + disposable MySQL 8.4 and Java 21 Backend)
- [ ] Verify environment: clean target SHA, disposable DB, Backend health HTTP 200, Frontend Vite linked to Backend, Chromium actually launches. Use port 18082 when 8082 is occupied and disclose the deviation.
- [ ] ADMIN route access to all four paths, including direct navigation/reload and logout; anonymous, DONOR and CREATOR blocked on UI and Backend.
- [ ] Findings: empty, loading, network failure, retry/refetch, valid filters, invalid campaign UUID, pagination (seed at least 11 findings or mark case BLOCKED), severity labels, source IDs, counts.
- [ ] Finding detail: all facts; occurrences; status history; review note and reviewer; unusual user-generated HTML-like strings rendered safely; no secret token/network leak.
- [ ] Two legitimate independent Admin actors where needed: verify supported OPEN→ACKNOWLEDGED→CONCERN_CONFIRMED and OPEN→DISMISSED; note must be supplied; repeat terminal transition rejected. Attempt conflicted reviewer on their own campaign/activity and verify Backend denies. Backend updates reflected in list, detail and history; do not invent permission overrides.
- [ ] Message/history network failure injection: review cannot be submitted when history cannot be fetched. Distinguish any API-mocked cases explicitly from real integration coverage.
- [ ] Rule precision: no falsely invented percentage when no final decisions; denominator explained; ratio matches Backend.
- [ ] Audit: filters by actor/action/entity, pagination (11+ records), metadata escaped; verify finding decisions/requeue generate correct Backend events; read-only.
- [ ] Outbox/Alert: stats and real status filters; relay-enabled/webhook-configured status; failed entries manually requeued with valid reason -> Backend PENDING. Invalid state retry blocked in Backend; absence of failed fixtures = BLOCKED rather than fabricated PASS. No claim messages were delivered merely because queued.
- [ ] 375×900 / 1440×900: all new pages fit document width (`documentElement.scrollWidth <= innerWidth`); long UUIDs and JSON scroll internally/wrap. Keyboard focus, labels and error states.
- [ ] Regression smoke of FE-00–FE-04, especially FE-04 escrow internal scrolling 375px and Admin permissions.
- [ ] No real payments/auto-blocking/auto-approval; Admin Monitoring acts on **advisory findings only**.

## Acceptance report requirements
Report exact commit, browser/backend/DB versions, PASS/FAIL/BLOCKED matrix, screenshots, viewport metrics, redacted HTTP responses, and distinct labels for real Backend integration versus mocked failure injections. If cloud Codex lacks Docker/browser, mark BLOCKED and provide precise environment gap. **No source changes or merge by Codex.**

**Gate:** after owner accepts independent E2E + final SHA CI PASS, mark PR ready, squash merge to `master`, and verify **post-merge master CI**. FE-06 follows; the multi-concept community-centered non-SaaS UI/UX redesign starts **after FE-06**, not during FE-05.
