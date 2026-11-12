# Campaign Discovery React Desktop 1440 — Implementation review (2026-11-12)

## Authority and scope
- Design baseline: [PR #33](https://github.com/TronkIshere/rise-ai/pull/33) at `46af8eaa696e25bcde3e1a85df782c46c89cfbd3`, owner-approved 1440px HTML Paper Dossier + integrated landscape. Independent review remains separate.
- This branch is stacked **on PR #33**. It must not merge until both design and React branches pass their independent review gates, and the owner explicitly authorizes merge.
- Scoped changes: public `/campaigns` React route, approved static visual assets and route-scoped CSS, React browser QA. No Homepage, other public pages, Backend, migrations, mobile layout work, or API contract updates.

## React fidelity
- Campaign-specific Header/Footer and journey landscape follow design PR #33 without modifying `HomePage` or global B2 CSS.
- Category filter preserves 8 Backend codes, server-side `q`, `category`, `status=ACTIVE` and 0-indexed `page`, `size=9`; one feature + up to eight Paper Dossier rows.
- Data is read **only from `listActiveCampaigns`**. Never copy the 46 HTML preview fixtures into runtime. Missing data uses truthful fallback; generated concept art is explicitly marked as decorative, never proof of a campaign.
- Funding and financial copy always mark amounts as simulated. CTA navigates to an existing `/campaigns/:slug` route only if a valid slug exists. Loading, API error/retry, empty state, search, filters, page navigation and history preserved.
- Approved decorative SVGs are static checked-in source only. React renders data as text, **never interpolated into SVG strings**. The Paper Dossier valley asset is reused from PR #33.
- Desktop 1440 is the visual acceptance viewport; no new mobile visual scope.

## Acceptance gates
- `npm run verify` including typecheck, lint, build, architecture, unit and release checks.
- Automated Chromium **real running Vite React** at 1440x900, with mocked network responses explicitly marked test-only and captured full-page screenshot plus qa-report.json.
- Separate staging test against **real Backend** required before claiming API/staging acceptance. Do not call mocked browser QA a real Backend integration.
- Inspect screenshot for Section02 category, Section04 featured, Section05 dossier, Section06 landscape/pagination and Footer; compare manually to approved HTML. Independent code reviewer and owner visual React sign-off required before merge.


## Owner feedback — pagination readability refinement (2026-11-12)
- **Scope:** only Campaign Discovery Desktop 1440px pagination text and control group; no landscape redrawing, Footer movement, Homepage, mobile or Backend changes.
- Moved overline/count/pagination controls **30px down** without moving the decorative SVG or main section.
- Increased overline to **14px**, page count to **16px**, numeric/navigation buttons to **16px** with 48px minimum button targets.
- Switched small dark green landscape labels to **warm ivory text on a translucent forest-green pill**, with a restrained amber outline; strengthened disabled button text contrast.
- Added actual computed-style assertions in Chromium 1440 QA for text sizes, shift and color/backdrop, in addition to existing layout/search/filter/empty/API contract checks.
- **Review required:** inspect captured React screenshot for clearance under final dossier and before Footer. A successful CI run alone is not owner visual approval.
