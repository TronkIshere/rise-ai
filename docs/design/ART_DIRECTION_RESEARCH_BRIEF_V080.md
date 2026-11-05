# Riser AI — Art Direction Research Brief v0.8.0

**Prepared:** 2026-11-06  
**Stage:** Research preparation / moodboard direction study — **NOT a selected style, High-Fidelity screen or React implementation**  
**Locked input:** [Homepage Composition Baseline v0.7.1b](./COMPOSITION_BASELINE_V071B.md), Desktop 1440px.

## Design challenge

Build a charity/community narrative that feels **human, credible and visually distinctive**, rather than generic AI-generated SaaS cards. Communicate each campaign's identity and fundraising progress quickly, then link the human story to milestone and evidence accountability. Avoid the past problems: excessive background blocks, huge floating rounded containers, text too small or low contrast, decorative accents without meaning, campaign imagery blending into the background, and content/CTA visually detached from cards.

The purpose of this stage is **to study and compare**, not silently replace the approved layout.

## Initial research lenses (not final concepts)

| Lens | Visual question | Strength to test | Risk / disallowed shortcut |
|---|---|---|---|
| A — Human-centered documentary | Can authentic, consented field photography and editorial headlines introduce real people respectfully? | Emotional immediacy, clear narrative, memorable Hero | Fabricating beneficiaries or replacing provenance with AI-generated realistic "evidence" |
| B — Community signature | Can restrained illustrated motifs and category recognition lines express belonging? | Distinct identity for 8 categories, ties Discovery to individual campaign stories | Painting every section a different pastel/background or repeating identical cards |
| C — Evidence with empathy | Can verification/evidence typography and milestone emphasis convey trust without turning the Homepage into an Admin dashboard? | Readable status and traceability, appropriate data hierarchy | Claiming internal approval is independent proof, confusing funding/settlement/milestones |

**Research outputs required before any visual concept selection:** reference board with provenance; three **distinct** directions compared on the *same* v0.7.1b content and geometry; typography test sheet for Vietnamese; palette+contrast candidates; photography/illustration rights and fallback rules; category recognition line semantics; interface states; and a short recommendation based on visible evidence. No direction is approved here.

## External references — research only, not assets to copy

1. **charity: water — Our Work:** https://www.charitywater.org/our-work  
   Study the relationship between field stories, photos and measurable project proof. The site describes sharing project photos and coordinates; Riser AI must not claim those proof mechanisms unless actually present.
2. **GlobalGiving — Project Reports:** https://www.globalgiving.org/projects/globalgiving-community-aid-fund/reports/  
   Study the distinction between fundraising target/progress and project reports posted by project leaders; these are examples of public reporting, not a data source or Riser AI API contract.
3. **W3C WCAG 2.2:** https://www.w3.org/TR/WCAG22/  
   Design evaluation anchors: normal text contrast >=4.5:1, large text >=3:1, meaningful UI boundaries/graphics >=3:1; never use color as the sole category/status signal.
4. **Google Fonts upstream Be Vietnam Pro description:** https://github.com/google/fonts/blob/main/ofl/bevietnampro/DESCRIPTION.en_us.html  
   Useful for researching Vietnamese text legibility; runtime currently uses a Be Vietnam Pro-first font stack. This does **not** pre-approve the final headline/body pairing or loading strategy.

Reference websites are for critique only. Do not scrape, reuse photos, trade dress, logos, copyrighted texts, or claim the charities endorse Riser AI. Verify licensing/consent for any production media. For sample visuals, label placeholders and avoid photo-realistic synthetic evidence.

## Detailed investigation tasks

### 1. Moodboard and visual storytelling

- Research authentic charity narratives: context image → project actions → funding and beneficiaries → current milestone → verification explanation.
- Collect multiple valid examples and document *what pattern works*, *what fails for Rise AI*, *the URL and observed date*, and *rights limitations*.
- Differentiate visual emphasis of Discovery (find campaigns), Story (human impact) and Transparency (evaluate state). No identical container treatment across all three.

### 2. Typography and Vietnamese content stress

- Test at Desktop 1440px: Hero, category labels, Campaign Card headline/description/money, Story narrative, Milestones and Transparency.
- Test Vietnamese accents, punctuation, numbers, VND formats, 1/2/3+ line titles and short/medium/long copy.
- Do not reduce text below readable sizes merely to meet fixed card height. Validate wrapping and disclosure expanded state.
- Verify typography hierarchy with documented screenshots, not only CSS values.

### 3. Color and category recognition

- Keep existing runtime tokens in `rise-ai-frontend/src/styles/tokens.css` untouched.
- Propose candidate palette only in research files, with ratios and contrast evidence; mark *PENDING_APPROVAL*.
- Test eight category lines for perceptual separation **plus text/icon labels**. No unique-color-only status cues.
- Evaluate foreground/background, progress bars, active filters, links, focus states and dark Footer separately.

### 4. Shape, radius, depth and spacing

- Evaluate curves only where they communicate component hierarchy (especially Footer top corners), not generic giant rounded cards.
- Control background changes; section boundaries should come from narrative rhythm, spacing and image/copy hierarchy before decorative fill.
- Preserve main/compact A+C ratios exactly; do not import visual styles from the original concept B as though already approved.

### 5. Integrity, data, accessibility

- Never represent simulated transactions as real, or internal evidence acceptance as independently verified impact.
- Continue to distinguish fundraising progress, milestone execution, evidence state, and human decision.
- Preserve v0.7.1b's accessible main/heading structure and the Transparency Enter/Space disclosure behavior in any future coded implementation.
- Visual comparison baseline: 1440×900, DPR1, with exact source version and screenshot provenance. Mobile redesign is out of scope.

## Decision gate before High-Fidelity

1. Present **three genuinely differentiated research-backed Art Direction candidates** on the approved v0.7.1b composition; no new section/card structure.
2. For each: rationale, media approach and rights policy, typography tested with Vietnamese, palette/contrast measurements, 8 category line behavior, campaign recognition, Story→Transparency narrative, Footer treatment and identified trade-offs.
3. Review side-by-side at 1440px and explicitly pick/approve *one* direction and its pending tokens, with a new decision log entry.
4. Only then begin a scoped **High-Fidelity Homepage 1440px** mockup. React remains separately gated.

## Governance

No token, palette, typography, media or High-Fidelity work is approved by this brief. This task only creates a research plan and reference inventory. A future research session can add dated findings and screenshots without rewriting the approved Composition Baseline.
