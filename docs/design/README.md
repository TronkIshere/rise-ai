# Riser AI — Approved Wireframe Baseline

**Recorded:** 2026-11-05  
**Scope:** Desktop 1440px, public homepage Hero and Campaign Discovery  
**Status:** **Approved for layout and information architecture only**; not approved as high-fidelity UI or React implementation.

This directory is the canonical record of design decisions agreed in the conversation.

## Read first

1. [Design Decision Log](./DESIGN_DECISION_LOG.md) — acceptance status, constraints, and source-of-truth notes.
2. [Discovery Composition v0.3.6](./DISCOVERY_COMPOSITION_V036.md) — approved combination and placement rules.
3. Open the HTML wireframe files below locally in a browser (they are self-contained previews, **not app source**).

## Approved baseline sources

| Component | Approved version | File |
|---|---|---|
| Hero + journey 01–02–03 | v0.2.1 | [HTML](./wireframes/hero-steps-v0.2.1.html) |
| Eight categories, 2 rows × 4 | v0.3.2 | [HTML](./wireframes/categories-8-v0.3.2.html) |
| Primary campaign card A+C | v0.3.4 | [HTML](./wireframes/campaign-card-primary-ac-v0.3.4.html) |
| Two compact campaign cards A+C | v0.3.5 | [HTML](./wireframes/campaign-cards-compact-ac-v0.3.5.html) |
| Combined Discovery placement | v0.3.6 | [Written composition spec](./DISCOVERY_COMPOSITION_V036.md) |

**Important:** v0.3.6 was approved as a composition reference from a generated image, **not** as exact graphic artwork, authoritative textual data, or editable HTML. The PNG is not tracked in this first Git documentation commit. Only its explicitly approved placement rules are preserved here.

## Guardrails

- Do **not** interpret wireframe framing, borders, radii, colors, copy, or illustrative image placeholders as final visual design.
- Eight category IDs are defined in the Backend enum; no confirmed second-level category schema exists.
- The three sample campaigns and funding values are **simulated**. Read actual finance and campaign data from APIs in implementation.
- Funding progress is distinct from execution-milestone progress.
- Do **not** change UI implementation, Backend, contracts, or deployment as part of this documentation work.
- Preserve original local worktrees DB-06 and MEDIA-01; no merge in this PR.

## Source repository links

- [Backend category enum](../../rise-ai-backend/src/main/java/com/riseai/enums/CampaignCategory.java)
- [Frontend campaign contract](../../rise-ai-frontend/src/api/contracts.ts)
- [Campaign category labels](../../rise-ai-frontend/src/lib/campaign-display.ts)
- [Demo seeder](../../rise-ai-backend/src/main/java/com/riseai/configuration/DemoDataSeeder.java)

This folder is **not** an approval to implement React. Move to visual design only after a separate visual review.
