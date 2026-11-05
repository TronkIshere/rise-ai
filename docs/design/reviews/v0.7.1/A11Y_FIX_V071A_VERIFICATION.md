# Riser AI — v0.7.1a Transparency accessibility fix

**Status:** FIX VERIFIED LOCALLY — **PENDING OWNER REVIEW** (not a final baseline approval).  
**Scope:** standalone Homepage HTML at Desktop 1440px only.  
**Parent source:** `homepage-visual-rhythm-v0.7.1.html` at commit `54bc04b8c41bff69899cb99ce18ba785c6cb841c`.

## Problem from independent Codex review

Opening **Mở giải thích** changed `aria-expanded` to `true`, but `#details` retained `aria-hidden="true"`, which excluded its text from the accessibility tree.

## Single behavior fix

Added exactly one state sync to the existing toggle event handler:

```js
details.setAttribute('aria-hidden', String(!opened));
```

No changes to visual CSS, labels, content, financial data, layout or React source. The original v0.7.1 file stays unchanged; the repaired candidate is [homepage-visual-rhythm-v0.7.1a-a11y.html](./homepage-visual-rhythm-v0.7.1a-a11y.html).

## Local independent test evidence (2026-11-06)

Replayed both archived v0.7.1 and repaired v0.7.1a HTML with **Chromium 144**, viewport **1440×900**, DPR 1, using Playwright `page.set_content` to avoid blocked `file://` navigation. No network requests and no page JavaScript errors in either run.

| Check | v0.7.1 | v0.7.1a |
|---|---|---|
| Initial `aria-expanded` / `aria-hidden` | false / true | false / true |
| Enter opens `#details` | true / **true (bug)** | true / **false (fixed)** |
| Space closes `#details` | false / true | false / true |
| Accessibility tree includes `Trạng thái minh chứng` after opening | No | Yes, not ignored |
| Document `scrollWidth` | 1440px | 1440px |
| Document `scrollHeight` | 6122px | 6122px |
| Full-page screenshots before toggle | Visual identical | Visual identical |
| JavaScript page errors / network requests | 0 / 0 | 0 / 0 |

Pixels were compared with Pillow `ImageChops.difference`: bounding box = `None` for the closed/default view. This verifies the **small interaction fix**, not the full accessibility or visual acceptance suite. Measurements may differ from Codex's Chromium 148 environment.

## Known untouched accessibility items

The two HTML sources still include **three `h1`** and **three `main`** landmarks inherited from standalone sections. These should be reviewed in a **separate semantic structure change** so their headings and styles are not silently altered in this narrowly scoped patch.

No sign-off for the whole Homepage, High-Fidelity, React, or merge is implied. Preserve the original HTML and design tokens; request a repeat test on the patched candidate before approving the composition baseline.
