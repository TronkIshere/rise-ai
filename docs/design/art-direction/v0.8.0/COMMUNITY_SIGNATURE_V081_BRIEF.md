# Riser AI — Community Signature v0.8.1 | Identity Refinement Brief

**Date:** 2026-11-06  
**Selected direction:** B — **Community Signature / Dấu ấn kết nối**  
**Stage:** focused Art Direction refinement; **NOT** full Homepage High-Fidelity or React  
**Canonical composition:** [v0.7.1b](../../COMPOSITION_BASELINE_V071B.md), **Desktop 1440px**.

## Creative intent

**One recognizable community-first design language, not another green SaaS template.** The website should feel like a credible collective movement: bold but not loud; clear enough to navigate donations and project evidence; warm without sugary pastel backgrounds.

Working idea **Rise Path — Connected Moments of Impact** is a *research framing*, not an approved logo, tagline or universal decorative element. Its line/node grammar must correspond to real sequence, category relationships, journey steps or milestone states; no arbitrary network decoration or invented network size.

### Five expression rules

1. **Typography establishes character.** Test Be Vietnam Pro using the **actual loaded font**, not browser fallback; explore weight, width, leading, Vietnamese marks and expressive scale for hero headline and category headings. Candidate font choices/weights are not approved yet.
2. **Connected line motifs carry information.** Place semantic connection marks only at real 01–02–03 steps, active/future milestones, navigation through the story, and where categories have labels. Avoid dotted-node wallpaper.
3. **High-contrast editorial rhythm.** Use intentional canvas/ink hierarchy, restrained color moments, sharp edges where helpful and purposeful depth. Avoid arbitrary pastel boxes, repeated giant radius, soft generic glassmorphism and ornamental backgrounds.
4. **Campaigns remain human.** Visual support must prioritize real consented/project-owned media, documented provenance and a credible image-missing placeholder; never invent beneficiary photos or present AI generated humanitarian imagery as evidence. Preserve A+C proportions exactly.
5. **Trust is observable.** Graphically separate simulated fundraising amounts, execution milestones, human review states and missing evidence. Never imply independently verified impact when the Backend only stores internal statuses.

## Proposed signature vocabulary to study (none is yet finalized)

| Element | Expression question | Preserve |
|---|---|---|
| Hero + 01–02–03 | Can large type and a single community path make the invitation immediate, without overpowering the primary CTA? | Hero v0.2.1 composition and 3-step strip |
| Eight categories | Can category line marks + readable label create eight recognizable choices without color-only encoding? | 8 backend enums in 4×2 grid |
| Discovery primary card | Can a distinctive border/typographic motif strengthen campaign recognition without hiding net raised / target / %? | Image 37.5%, details 62.5% |
| Compact cards | Can the same family identity adapt to smaller cards without repeating full-size decorative frames? | Top 38/62 approximate, funding lower full-width |
| Story / Milestones | Can connected paths emphasize current step and human narrative without conflating funding/fulfillment? | v0.7.1b story rhythm, milestone details |
| Transparency | Can state icons and a clear explanation trail communicate **internal review**, with disclosure accessible by keyboard? | 1 main / 1 h1 semantics, disclosure ARIA, no false evidence claims |
| Footer | Can the deep-grounded final section feel like a confident conclusion rather than a floating promotional card? | full-width, top-only provisional 56px round |

## Concrete deliverables for v0.8.1 (review artifacts only)

1. **One distinctive B moodboard / brand grammar** with original sketches and links to external references. Cite sources and permission limits; do not copy branding from charity sites.
2. **Vietnamese actual-font study** at 1440×900, DPR1. Compare Hero, campaign card, category labels, Story, Transparency, financial values and long/short titles. Record exact font build/family loaded (verify computed font, font loading), fallback behavior and screenshots.
3. **Two B micro-directions** (e.g. bold editorial path vs community graphic system) using **identical v0.7.1b geometry**. They may vary illustration/line grammar, font emphasis and surfaces **without altering section/card structure**. No generic all-over rounded cards.
4. **Candidate-only color/accessibility sheet:** brand ink, warm canvas, one energetic accent, eight semantically labelled category-lines; calculate sampled contrast for normal and large text, buttons, links, focus, progress and meaningful UI borders. WCAG 2.2 thresholds 4.5:1, 3:1 as applicable; sample compliance is not whole-site compliance.
5. **Image/provenance rules:** authenticated rights/consent workflow, no AI-realistic 'evidence', predictable placeholder; approve assets individually.
6. **Visually traceable review**: side-by-side crops at 1440px, target vs baseline annotations, textual notes on data truth/ARIA, and owner scorecard for selecting the **final B variant**.

### QA and stop conditions

- All work in documentation/research branch only; **do not update** `rise-ai-frontend/src/styles/tokens.css`, React, Backend, Monitoring, deployment, API contract or approved v0.7.1b HTML.
- No mobile expansion in this phase. No PR merge or high-fidelity start without a new owner instruction.
- Preserve approved category/card ratios, all sections, mock-data labels, Story→Transparency transition and accessibility.
- Maintain Vietnamese diacritics and readable copy; do not shrink text to fit a style tile.
- **No false signoff:** if actual font is absent, report BLOCKED rather than passing typography; if images lack rights evidence, use explicit placeholders; if design variants look like one color swap, the exploration fails.

## Owner gates

**Selected today:** overall **B Community Signature** direction.  
**Pending:** choose final **B visual sub-direction**, approve its actual font/palette/category accents and image approach.  
**After that:** request separate authorization for a Desktop 1440px High-Fidelity Homepage artifact; React implementation remains independently gated.

## Traceability

- [Art Direction v0.8.0 sources](./README.md)
- [Original three style tiles](./STYLE_TILES_V080.html)
- [Owner selection](./DECISION_SCORECARD.md)
- [Design Decision Log](../../DESIGN_DECISION_LOG.md)
