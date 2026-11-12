# Campaign Discovery 1440 — Full-page Paper Dossier integration candidate

**Date:** 2026-11-12. **Status:** DRAFT. Owner visual review and independent approval are REQUIRED before merge.

## Provenance and scope
- Integrated from the ChatGPT Library `index(3)(1).html` (old Story Chapters v0.5 page shell), replacing ONLY outdated Section 05 result rows.
- Section 02 C3.1 category illustrations, Section 04 Organic Artistic Overflow featured campaign, Section 06 Landscape Thread B pagination, Header/Footer remain in place.
- Section 05 adopts the Paper Dossier v1.3.1 art direction from Draft PR #32, including scenic bottom landscape and its upper edge fade. The original PR #32 document is unmodified.
- CSS of the imported dossier section is isolated with Chromium-supported `@scope`; avoid global style collisions.
- The 46 entries are DESIGN FIXTURES ONLY. Current public React/Backend campaign results remain untouched. `size=9` preview contract: **1 lead + up to 8 dossiers per page**, with filtering, search, pagination, empty state, and demo-only CTA.

## Review and QA
- GitHub Actions `Campaign Discovery Full-page HTML 1440 Review` generates an ACTUAL 1440px full-page Chromium screenshot and JSON assertions.
- Inspect transitions between category filters, featured stage, Paper Dossier, pagination/footer; typography, art provenance, contrast and landscape fade at 1440px.
- This HTML is a VISUAL PROTOTYPE, not a completed production `/campaigns` React route; no React/API QA or public deployment is claimed.
- Do not merge without the owner's screenshot sign-off and an independent reviewer. Homepage v1.3.2 and mobile UI are outside scope.
