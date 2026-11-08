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

## Verification & merge gate — updated 2026-11-08

**Owner visual sign-off: PASS** for Homepage B2 v1.3.2, React Desktop **1440×900 / DPR 1**, against the approved standalone HTML Master. This explicitly follows correction of the Hero font fallback and restoration of the approved 3-line headline / CTA layout. Scope includes all previously approved sections, including Milestones K1 + Journey Landscape and Footer v1.3.2. This is visual acceptance only; no new Mobile/Tablet development.

- React visual baseline commit: `2750ed773b80a45abb068c408bdd9da1f2dc1f99` on Draft PR #12.
- [Chromium actual React 1440px visual QA](https://github.com/TronkIshere/rise-ai/actions/runs/42458193873): **PASS**. [React screenshot, HTML screenshot and machine QA JSON](https://github.com/TronkIshere/rise-ai/actions/runs/42458193873/artifacts/13609055199) (time-limited artifact). Automated checks cover structure, 8 categories, campaign fixture cards, keyboard disclosure and category filter, image/resource loading, overflow, JS errors, API empty/error fixtures, Hero title height and CTA/Journey alignment.
- [Frontend checks](https://github.com/TronkIshere/rise-ai/actions/runs/42458197440) and [whole-repository CI](https://github.com/TronkIshere/rise-ai/actions/runs/42458197415): **PASS** for the same implementation commit.
- **Not yet accepted:** live Backend campaign data and full-stack integration. The React browser test intercepts the API with a test-only fixture; funding numbers in the fixture are not live balances. Technical and visual PASS are **not** production deployment permission.
- **Merge gate:** PR #12 remains Draft pending separate code review and stacked dependencies PR #9–#11 (also Draft at this sign-off). No direct merge to `master` or deploy on the strength of visual approval alone.
