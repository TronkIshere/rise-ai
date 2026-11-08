# Homepage B2 v1.3.2 — React Desktop 1440px implementation

**Owner approval:** Homepage B2 Development Master v1.3.2, including Milestones K1 + Journey Landscape background, officially approved in conversation on 2026-11-08. Approval covers visual design only; this document records the separately authorized React implementation task.

**Source:** `index.html`, `style.css`, `sections/`, `assets/` under this archived directory; implementation is in `rise-ai-frontend/src/pages/HomePage.tsx`, `B2Campaigns.tsx`, `B2CampaignCards.tsx`, `src/components/ui/B2*.tsx`, `src/styles/homepage-b2.css` and `public/b2-home/`.

**Scope:** Homepage `/` only. Desktop 1440px reference. New Mobile/Tablet design is expressly out of scope. Historical responsive rules from the owner-approved static source are kept, not redesigned.

## Integration choices

- Preserve the approved section order, typography, art, motion and color via a page-scoped visual stylesheet. No cross-route CSS selector is introduced.
- `/` intentionally renders outside the shared `SiteShell` to prevent a duplicated header/footer. All other routes continue to use `SiteShell`, including role guards and auth flows.
- Preserve eight Backend-aligned categories. On selection, the Campaign Discovery section refreshes using `listActiveCampaigns` (`status=ACTIVE`, optional Backend `category`, size 3), rather than a fake local filter.
- Render three real public campaign records when provided. Funding comes from authoritative public `funding.netRaised`/`targetAmount` and is explicitly **simulation**, distinct from milestone delivery. Missing data show an honest placeholder, not fabricated amounts or 0%.
- Story, Milestones and Transparency retain clear **kịch bản demo / concept** labels and do not pretend to be Backend-verified outcomes. Approved illustrations remain illustrative, not campaign evidence.
- Transparency disclosure uses React state, keyboard-accessible native button, `aria-expanded`, `aria-hidden` and `hidden`.
- Keep existing backend/auth/admin/donor flows unchanged; no direct Monitoring calls, money transfer, automatic approvals, additional dependencies or API endpoint inventions.

## Verification & merge gate

- Locally validated source conversion and TSX parser (global TypeScript); used Chromium for approved original/scoped CSS at 1440px, with complete image loads and no horizontal overflow. Scope reference 5298px vs 5281px height on static harness (17px rendering difference; *not* proof of React visual parity).
- **Full `npm run verify` and real React-browser screenshot remain required for PR acceptance.** In the isolated workspace the installed npm dependencies and complete repository are unavailable. Do not claim full React build, tests, screenshot parity, CI PASS or production readiness until those run from a complete checkout with `npm ci`.
- Verify CI `Frontend checks` and do 1440px visual comparison against the approved `review/fullpage_1440.jpg` before any merge. Keep PR Draft while gates are open.
