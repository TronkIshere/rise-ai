# UI Token Registry — 2026-11-05

This registry records provenance and approval; it does not define a second runtime theme. Runtime token authority remains [tokens.css](../../rise-ai-frontend/src/styles/tokens.css). Wireframe HTML is an immutable review artifact, not a palette to copy into React.

## Status definitions

- **EXISTING_CODE:** present in application source; preserve it. This does not approve it for the new high-fidelity design.
- **APPROVED_WIREFRAME:** explicitly agreed structure/value for the rough desktop wireframe only. It is not a final visual/runtime token.
- **PENDING_APPROVAL:** proposed or provisional, not an official final standard. Preserve supplied provisional sources without promoting their values. In JSON, `baselineApproval: PROVISIONALLY_APPROVED` explains the footer's narrower status.

## Complete current runtime token inventory

| Token | Exact value | Unit/type | Role | Approval | Scope | Version | Source | Change condition |
|---|---|---|---|---|---|---|---|---|
| `--color-brand` | `#176850` | HEX color | Primary actions/brand | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-brand-hover` | `#0f513e` | HEX color | Brand hover | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-brand-ink` | `#102d25` | HEX color | Strong headings/brand ink | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-surface` | `#ffffff` | HEX color | Surface | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-canvas` | `#f6f8f4` | HEX color | Canvas | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-soft-mint` | `#e9f3eb` | HEX color | Soft mint context | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-soft-sand` | `#fbf7e8` | HEX color | Soft sand context | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-subtle` | `#e8eee8` | HEX color | Subtle track/fill | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-border` | `#dce5dc` | HEX color | Borders | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-copy` | `#192b25` | HEX color | Body copy | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-muted` | `#596b62` | HEX color | Secondary copy | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-warning` | `#9e5d14` | HEX color | Warning | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--color-danger` | `#b63d45` | HEX color | Error | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--radius-card` | `1.25rem` | rem | Existing card corners | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |
| `--font-sans` | `'Be Vietnam Pro', 'Segoe UI', system-ui, sans-serif` | font-family list | Existing sans typography | EXISTING_CODE | Existing React runtime | PR #8 baseline `3c8c4b6` | [tokens.css](../../rise-ai-frontend/src/styles/tokens.css) | Explicit scoped owner approval; change runtime file only in a separately authorized implementation task |

## Wireframe layout registry

The machine-readable authority for the documented layout values is [WIREFRAME_LAYOUT_TOKENS.json](./WIREFRAME_LAYOUT_TOKENS.json). Each entry carries value/unit, sourceVersion, approvalStatus, scope and source. Do not load it into React.

| Rule | Value | Source version | Approval and scope |
|---|---|---|---|
| Desktop reference | 1440px | v0.3.6 | APPROVED_WIREFRAME; desktop reference only |
| Categories | 8; 4 columns × 2 rows | v0.3.2 | APPROVED_WIREFRAME; first-level categories |
| Primary image/content | 37.5% / 62.5% | v0.3.4 | APPROVED_WIREFRAME; card composition, not fixed global dimensions |
| Compact image/content | approximately 38% / 62% | v0.3.5 | APPROVED_WIREFRAME; upper row |
| Compact funding | 100% width | v0.3.5 | APPROVED_WIREFRAME; lower section |
| Footer upper corners | 56px / 56px | v0.6.3 | PENDING_APPROVAL; provisional desktop source, not high-fidelity approval |
| Footer placement | full width, no floating side-margin card | v0.6.3 | PENDING_APPROVAL; preserve provisional desktop structure |

Changes require explicit instruction identifying the affected section, original version, reason, changed tokens and impact on approved components, recorded in the decision log. No implicit approval through assembly, screenshots or a filename containing “approved”.

## Pending visual choices — no assigned value

Category line colors, final typography scale, shadows and final backgrounds remain **PENDING_APPROVAL** (JSON `value: null`). New high-fidelity palette, fonts, spacing, radii, artwork and borders require separate approval. Existing HTML color/font/CSS literals remain artifact-local examples; their preservation is not approval. No new HEX, font, spacing, shadow or radius has been proposed here.

Existing non-token declarations in [global.css](../../rise-ai-frontend/src/styles/global.css), such as the 3px focus outline, .5rem progress track and 9999px track radius, are **EXISTING_CODE**, not newly approved design tokens. Preserve them; do not normalize them during documentation work. Existing DESIGN_SYSTEM.md governs runtime behavior (including 44px targets and 320px responsiveness); the current design review is desktop-only and does not revoke existing accessibility/responsive behavior.

CampaignCard currently uses a vertical media/card composition and FundingProgress receives `netRaised`/`targetAmount`; SiteShell owns role-aware navigation. These observations do not authorize replacing them with the wireframe. Funding and execution progress remain separate; missing data must not become 0%.
