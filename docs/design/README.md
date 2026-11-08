# Riser AI — Design Baseline Governance

> **Current composition baseline:** [Homepage v0.7.1b](./reviews/v0.7.1/homepage-visual-rhythm-v0.7.1b-semantics.html) — **OWNER APPROVED (LOW-FIDELITY, DESKTOP 1440px)** on 2026-11-06. [Approval scope](./COMPOSITION_BASELINE_V071B.md) · [Art Direction research brief](./ART_DIRECTION_RESEARCH_BRIEF_V080.md). Archived Master v0.7.0 remains an assembled reference, not current baseline.

**Recorded:** 2026-11-05  
**Scope:** Desktop 1440px, public homepage baseline and assembled Master
**Status:** Current v0.7.1b composition approved for low-fidelity Desktop 1440px only. Historical v0.7.0 Master is **ASSEMBLED — PENDING VISUAL APPROVAL**; no high-fidelity styling or React approval.

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

## Governance and Master v0.7.0

- [UI Guardrails](./UI_GUARDRAILS.md) — ten mandatory scope and preservation rules.
- [UI Token Registry](./UI_TOKEN_REGISTRY.md) — existing runtime values versus approved wireframe structure and pending visual choices.
- [Layout Tokens JSON](./WIREFRAME_LAYOUT_TOKENS.json) — design documentation only; never a runtime theme.
- [Master Review](./MASTER_WIREFRAME_V070_REVIEW.md) — pending visual approval and review checklist.
- [Source Manifest](./SOURCE_MANIFEST.json) — byte counts, original package entries and SHA-256 provenance.

| Additional source | Approval | Preserved HTML |
|---|---|---|
| Featured Story + Milestones v0.4.2 | Provisionally approved | [HTML](./wireframes/story-milestones-v0.4.2.html) |
| Transparency v0.5.0 | Provisionally approved | [HTML](./wireframes/transparency-v0.5.0.html) |
| Footer Desktop v0.6.3 | Provisionally approved | [HTML](./wireframes/footer-desktop-v0.6.3.html) |
| Homepage Master v0.7.0 | ASSEMBLED — PENDING VISUAL APPROVAL | [HTML](./wireframes/homepage-master-v0.7.0.html) |

Locked order: Hero → 01–02–03 → 8 Categories → Campaign Discovery → Featured Story → Milestones → Transparency → Footer. The previously approved structures remain locked; importing the remaining sources grants no new visual approval. No required HTML is missing; v0.3.6 remains its written composition spec, not reconstructed HTML.
