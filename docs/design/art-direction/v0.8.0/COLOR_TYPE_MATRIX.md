# v0.8.0 — Color and typography trials

**ALL VALUES PENDING APPROVAL.** These are research palette samples, **not** runtime CSS tokens.

## Proposed pairwise sRGB contrast

Calculated with WCAG relative luminance, rounded to two decimal places.

| Direction | Canvas | Ink | Ink / Canvas | Muted | Muted / Canvas | Accent | Accent / Canvas | White / Dark CTA |
|---|---|---|---:|---|---:|---|---:|---:|
| A Field Notes | `#F3E9D9` | `#293B32` | 9.89:1 | `#46564B` | 6.49:1 | `#9F432F` | 5.26:1 | 11.89:1 |
| B Community | `#F4EFE2` | `#163C32` | 10.59:1 | `#4E6058` | 5.83:1 | `#B24731` | 4.77:1 | 12.16:1 |
| C Evidence | `#EAEFEB` | `#224451` | 8.96:1 | `#4C6571` | 5.30:1 | `#95551B` | 5.02:1 | 10.43:1 |

These sampled pairs exceed 4.5:1 normal-text contrast, but **do not certify all UI states**. Eight category-line shades must be tested individually against adjacent surfaces. Focus, hover, progress, boundaries and photo overlays require their own checks.

## Candidate fonts

| Direction | Editorial headline | Body/UI | Source | Preview limitation |
|---|---|---|---|---|
| A | **Source Serif 4** | **Be Vietnam Pro** | Google Fonts (both have Vietnamese support) | In Chromium 144 offline preview, fallback Noto Serif/Noto Sans |
| B | **Be Vietnam Pro** bold | **Be Vietnam Pro** regular | Be Vietnam Pro is already the existing runtime-first font | Noto Sans fallback in preview |
| C | **IBM Plex Sans** | **IBM Plex Sans / Be Vietnam Pro** | IBM confirms extended Latin covers Vietnamese | Noto Sans fallback in preview |

Before a typography decision, render **real installed font families** at 1440×900. Validate Vietnamese accent stack, title width/height, 1–3-line campaign names, monetary amounts, variable weight, 14/16/18px paragraph legibility, focus and disclosure states. Never infer true font metrics from this fallback screenshot.

## Decision guardrails

- Normal text: >=4.5:1; large text >=3:1; meaningful UI/graphical indicator >=3:1 where WCAG 2.2 applies.
- Category/status identity must use clear text labels in addition to color.
- No final card radius, shadow, typography scale or backgrounds are defined.
- `rise-ai-frontend/src/styles/tokens.css` remains the **only production token authority** and has not been changed.
