# Riser AI — Homepage v0.7.1 / Visual Rhythm & Content Hierarchy Review

**Date:** 2026-11-05  
**Review viewport:** Desktop 1440 × 900, Chromium headless  
**Status:** PROPOSED — PENDING USER VISUAL APPROVAL  
**Source:** `RISER_AI_HOMEPAGE_MASTER_WIREFRAME_V070.html`, SHA-256 `9add4bcff3daf8fa322efbb30d367ceb6a3827bd604635c6b58250581af43ea7`  
**Output:** `RISER_AI_HOMEPAGE_VISUAL_RHYTHM_V071.html`

## Scope contract

- Keep original **Hero + 01–02–03**, **8 category labels**, **Campaign Discovery/Hybrid A+C** and **Footer v0.6.3**. No React, Backend, GitHub PR or deployment changes.
- Only adjust Story/Milestones and Transparency. New CSS rules are scoped to those two sections and desktop 1440px.
- Wireframe placeholders are not authentic imagery; demo data are not API-live evidence.

## Quantified result (Chromium DOM geometry)

| Region | v0.7.0 | v0.7.1 | Difference |
|---|---:|---:|---:|
| Story/Milestones | 2294.6px | 1740.1px | -554.5px (-24.2%) |
| Transparency | 1711.5px | 1160.1px | -551.4px (-32.2%) |
| Full page | 7228px | 6122px | -1106px (-15.3%) |

No horizontal overflow at 1440px; page JavaScript errors: **0**. Four native `<details>` are present; expand/collapse interaction verified. The link from current milestone to Transparency updates `location.hash` to `#transparency`.

## Actual layout edits

1. **Story intro:** preserve the factual problem and simulated nature; move supplementary campaign context under an accessible disclosure. Image remains a wireframe placeholder, not an invented photograph.
2. **Solution:** retain the three approved plan areas, shorten the repeated text and whitespace. No replacement of Campaign Card or hero.
3. **Milestone journey:** keep all three statuses, target amounts and simulation distinctions. Make current step most prominent; the full original description of each milestone remains in a native disclosure.
4. **Current status:** reduce its overlap with the milestone list and introduce an explicit section-to-section link (“Những thông tin này được kiểm tra thế nào?”).
5. **Transparency:** keep Plan → Recorded Information → Review, the accepted invoice example, four important boundary statements and the optional detailed-state explanation. Shorten explanatory copy and reduce vertical whitespace.
6. **Numbering:** only the decorative 01–04 markers on Story's narrative rail become dots; the meaningful 01–03 milestones remain numbered. The Hero journey 01–02–03 is untouched.

## Preservation checks

| Component | Test |
|---|---|
| Hero v0.2.1 | Pixel-identical Chromium component screenshot |
| 8 categories v0.3.2 | Pixel-identical Chromium component screenshot |
| Campaign Discovery + A+C | Pixel-identical Chromium component screenshot |
| Footer v0.6.3 | The original HTML/CSS subtree and its width remain unchanged; its page position shifts with reduced content above (subpixel screenshot rounding prevents asserting pixel-identical crops) |

No final high-fidelity token, font, border, shadow or category accent color is approved by this review. Footer remains provisional.

## Visual review findings / still open

- **P1 (needs owner judgement):** Even after shortening, Story + Transparency remain ~2,900px together. Is this editorial weight appropriate for an exploratory charity homepage?
- **P2:** Original master has three `h1` elements (Hero, Story, Transparency). This standalone wireframe preserves the underlying heading tags to avoid a wider semantic refactor. A production React implementation must validate a coherent document heading hierarchy.
- **P2:** Story and Transparency still both use the educational equipment seed example; acceptable to show cause and traceability, but review whether different examples would improve variety without introducing invented data.
- **P2:** Full descriptions depend on accessible native `<details>`, not a route to verified live evidence. In React, use authorized public API fields and consistent loading/empty/error states.
- **Brand:** Historical samples alternate “Rise AI”/“Riser AI”; canonical brand naming needs confirmation before a visual system approval. No existing header/footer was renamed.

## Decision gate

**v0.7.1 = COMPOSITION REFINEMENT CANDIDATE.** User approval is needed before updating `docs/design/DESIGN_DECISION_LOG.md`, PR #9 or starting High-Fidelity. Preserve v0.7.0 intact as an archived baseline.
