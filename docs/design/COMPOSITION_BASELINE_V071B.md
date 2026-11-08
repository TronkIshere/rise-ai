# Riser AI — Locked Homepage Composition Baseline v0.7.1b

**Approval date:** 2026-11-06  
**Decision authority:** Product owner via explicit conversation instruction to lock Homepage v0.7.1b  
**Status:** **APPROVED — LOW-FIDELITY COMPOSITION (DESKTOP 1440px)**  
**Fidelity boundary:** Wireframe composition and content hierarchy only. NOT approved High-Fidelity, CSS tokens, illustrations or React.

## Source of truth

- **Approved artifact:** [Homepage v0.7.1b HTML](./reviews/v0.7.1/homepage-visual-rhythm-v0.7.1b-semantics.html).
- **QA report:** [Chromium 148 A11Y + pixel gate](./reviews/v0.7.1/SEMANTICS_V071B_REVIEW.md) (tested source commit `72b55fa19646af0b43913e454951cb0f01734a1e`).
- [Owner's Design Decision Log](./DESIGN_DECISION_LOG.md), [UI Guardrails](./UI_GUARDRAILS.md), and [Wireframe Layout Registry](./WIREFRAME_LAYOUT_TOKENS.json).
- [Earlier v0.7.0 master](./wireframes/homepage-master-v0.7.0.html) remains an immutable historical comparison. v0.7.1, v0.7.1a and v0.7.1b originals are all preserved.

## Locked structural/semantic contract

| Component | Accepted rule |
|---|---|
| Page ordering | Hero; 01–02–03; eight first-level categories (4×2); Campaign Discovery; Featured Story; Milestones; Transparency; Footer |
| Campaign cards | Main A+C image/content 37.5%/62.5%; compact A+C top ~38%/~62% and full-width fundraising band |
| Story → Transparency | Preserve condensed narrative, current-milestone emphasis and link to explanation of verification |
| Transparency disclosure | `aria-expanded`, `aria-hidden` and accessibility-tree exposure synchronized; supports Enter and Space |
| Semantics | One page-level `main` landmark and one Hero `h1`; Story and Transparency titles remain accessible `h2`; no duplicate IDs |
| Footer | Full-width desktop composition, upper-rounded form preserved; 56px provisional radius is **not** approved as global runtime styling |
| Page density | User accepted v0.7.1b composition, including documented Story+Transparency footprint (approx. 2,936px / 47.6% in Chromium 148) |
| Content policy | No fabricated real donation/evidence, no mixing fundraising with milestone progress, no claim of independent verification from admin acceptance |

## Re-review triggers

New section/order, card ratios, story milestones, transparency flow, accessible headings/landmarks, footer placement, or a materially different desktop content hierarchy needs separate owner approval and visual/keyboard regressions. All future style explorations must compare against this baseline rather than rebuilding it.

**Still pending:** final brand color palette, category line colors, typography system, photographic style, background treatment, shadows, borders, card radii beyond existing approved structural placeholders, animation, mobile, High-Fidelity visual sign-off and React implementation.

Next document: [Art Direction Research Brief v0.8.0](./ART_DIRECTION_RESEARCH_BRIEF_V080.md).
