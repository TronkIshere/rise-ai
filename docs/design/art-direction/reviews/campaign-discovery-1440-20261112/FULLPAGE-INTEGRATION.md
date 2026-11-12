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

## Landscape continuity refinement v1.1 (2026-11-12)
Owner raised a visual seam issue. Adjust **only** the transitions between (1) featured → paper dossiers, (2) paper bottom valley → Landscape Thread B pagination, (3) pagination → forest footer. The top scene gets a two-ended gradient alpha mask, the bottom photo gains a two-ended alpha mask and shared sage tone, and pagination/footer surfaces now meet without a disconnected cream strip. No component sizes, text, cards, data logic or Homepage changed.

Chromium QA adds computed-style mask checks and actual layout seam-gap measurements; preserve latest screenshot artifact for owner visual approval. This is a design proposal pending review, not a signed-off visual master.

## Journey Landscape Thread v1.2 — candidate for owner review
Owner suggests a single pattern from the bottom of the small campaign dossiers to the beginning of Footer. The existing valley illustration is retained as a source; a softly lit river trail now carries through Section 06 and ends at the Footer wave. Pagination hill colors are reduced to support—not compete with—the trail. Cards, content, data, and functionality are unmodified. This is a design proposal, **not approved** until real 1440px screenshot review and independent review.
