# UI Guardrails — 2026-11-05

Read with [Design Decision Log](./DESIGN_DECISION_LOG.md), [Token Registry](./UI_TOKEN_REGISTRY.md) and [layout data](./WIREFRAME_LAYOUT_TOKENS.json). These design rules supplement existing architecture, security and business rules.

## RULE 01 — Scoped changes

Only edit the explicitly requested section. Do not alter adjacent sections for aesthetics or consistency.

## RULE 02 — Preserve approved structures

Preserve approved layout, image/content ratios, order and components. Preserve provisional originals while their review is pending; assembly does not grant approval.

## RULE 03 — Single source of truth

Future React runtime tokens must use rise-ai-frontend/src/styles/tokens.css. Do not create a second palette/theme or import the documentation JSON at runtime.

## RULE 04 — No arbitrary styling

No new arbitrary colors, shadows, gradients, rounded cards, decorative lines or backgrounds without a specific instruction.

## RULE 05 — Typography quality

Do not shrink text or reduce contrast to force a fit. Protect Vietnamese diacritics and check short, medium and long content.

## RULE 06 — Data fidelity

Backend data and permission contracts are authoritative. Do not fabricate amounts, organizations, evidence images, review outcomes or real transactions. Archived seed/demo text is not live data.

## RULE 07 — Funding and milestones

Separate fundraising progress from execution progress. Missing data is not 0%. Keep simulation wording and independent human review.

## RULE 08 — Visual change control

Record original version, affected section, reason, changed tokens and impact on approved components before any authorized future visual change.

## RULE 09 — Desktop scope

Current design review targets Desktop 1440px. Do not expand into Mobile research/redesign. Existing runtime accessibility/responsiveness stays intact.

## RULE 10 — No unapproved implementation

Do not start React implementation, deployment, merge or high-fidelity conversion without separate explicit instruction.

Locked sequence: Hero → 01–02–03 → 8 Categories → Campaign Discovery → Featured Story → Milestones → Transparency → Footer. Do not add/remove sections or replace approved cards. Historical Master v0.7.0 remains **ASSEMBLED — PENDING VISUAL APPROVAL**. **Homepage v0.7.1b is the owner-approved LOW-FIDELITY Desktop 1440px composition baseline**, not a production visual theme or React authorization. See [composition lock](./COMPOSITION_BASELINE_V071B.md).

Change record template: original version; section; requested reason/authorization; token before/after and approval; effect on approved structures; evidence; resulting review status. Preserve first, document second, implement only after explicit approval.
