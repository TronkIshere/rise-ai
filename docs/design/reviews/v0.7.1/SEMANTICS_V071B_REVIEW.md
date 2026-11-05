# v0.7.1b Semantic A11Y Gate

**PASS · READY FOR OWNER COMPOSITION REVIEW**
Tested 2026-11-06 on the PR #10 assignment head `04360e1e64e2fa2996be76bd6aa0ffc7440559a4` plus the additive [v0.7.1b candidate](./homepage-visual-rhythm-v0.7.1b-semantics.html). This status applies to the static desktop candidate and the checks below.

## Phase A · Transparency disclosure

Playwright drove Chromium **148.0.7778.96** headless and offline at **1440 × 900, DPR 1**. It loaded exact HTML bytes from Git for the original and v0.7.1a. Values below are `aria-expanded` / `aria-hidden` / “Trạng thái minh chứng” exposed in the Chromium accessibility tree.

| Candidate | Initial | Enter | Space |
|---|---|---|---|
| Original v0.7.1 | false / true / no | true / true / no | false / true / no |
| v0.7.1a | false / true / no | true / false / yes | false / true / no |
| v0.7.1b (retested in Phase C) | false / true / no | true / false / yes | false / true / no |

In all versions Enter showed the panel visually and Space hid it. The original open panel is ignored with reason `ariaHiddenElement`. The v0.7.1a and v0.7.1b open panels are exposed, with the expected text in both the Chromium tree and the Playwright ARIA snapshot. All versions measured `scrollWidth=1440` in every state and `scrollHeight=6166 / 6282 / 6166` in the state order above. Page JavaScript exceptions and HTTP(S) attempts were both zero per run.

| Rendered source | SHA-256 |
|---|---|
| v0.7.1 | `5df675bbaa5a4cd4ae7c0674a526f450627cda13d9011a9791cb42bed722a398` |
| v0.7.1a | `d7b309164ed68f487f60bd98a366585c40934693f8b8c8917513042dc8956ece` |
| v0.7.1b | `277bba9979998d4a931fdb5b6b321fa09785c74d77f21bc4b26c63e3cd9167d1` |

## Phase B · Semantic scope

One page-level `main` starts with Hero and ends after Transparency. The header and footer sit outside it. Categories, Story and Transparency retain their existing container classes on `div` elements. The header and Hero share their original scoped style declarations across separate containers. Hero supplies the only `h1`. The Story and Transparency titles are `h2` with the original IDs and label references. Transparency title styles target its ID to preserve the original type and spacing. Predecessor HTML files remain byte-for-byte identical to the assignment head.

## Phase C · Visual and semantic checks

| Exact RGB comparison | Dimensions | Changed pixels | Difference bounds |
|---|---:|---:|---|
| Original vs v0.7.1a, initial | 1440 × 6166 | 0 / 8,879,040 | None |
| Original vs v0.7.1a, expanded | 1440 × 6282 | 0 / 9,046,080 | None |
| v0.7.1a vs v0.7.1b, initial | 1440 × 6166 | 0 / 8,879,040 | None |
| v0.7.1a vs v0.7.1b, expanded | 1440 × 6282 | 0 / 9,046,080 | None |
| v0.7.1a vs v0.7.1b, expanded Transparency crop | 1440 × 1281 | 0 / 1,844,640 | None |

The mask marks a pixel whenever any RGB channel differs. No threshold or region exclusions were applied. DOM region bounds match exactly in the initial, open and reclosed states. The initial measurements below are CSS pixels, rounded to two decimal places. Exact values for every state are in `phase-C.json`.

| Region | x | y | Width | Height |
|---|---:|---:|---:|---:|
| Hero | 0 | 131 | 1440 | 615.28 |
| 01–02–03 | 0 | 746.28 | 1440 | 151 |
| Categories | 0 | 897.28 | 1440 | 464 |
| Campaign Discovery | 0 | 1361.28 | 1440 | 1298.67 |
| Story | 0 | 2659.95 | 1440 | 1772.11 |
| Milestones | 177 | 3535.44 | 1167 | 605.75 |
| Transparency | 0 | 4432.06 | 1440 | 1164.13 |
| Footer | 0 | 5672.19 | 1440 | 493.69 |

- **Headings and landmarks PASS.** One DOM `main`, one AX main landmark, one banner and one contentinfo. One Hero `h1`, Story and Transparency `h2`, no skipped downward levels in tree order, and all 30 accessible heading names match v0.7.1a. Computed typography, margins, bounds and wrapping rectangles of all three titles match exactly.
- **References and keyboard PASS.** No nested main, duplicate IDs, invalid `aria-controls` or invalid `aria-labelledby` references. Enter and Space match the v0.7.1a state and accessibility-tree results above.
- **Layout stress PASS.** In each of v0.7.1a and v0.7.1b, five text blocks were checked with 115, 231 and 463 Vietnamese characters. All 15 cases per version fit their text rectangles, kept the same font sizes and measured 1440 px document width. Stress text was injected temporarily in the browser.

## Reproduce and locate evidence

Local evidence directory: `outputs/riser-v071b-a11y/` under the task workspace. Binary evidence is stored there. The three tested HTML snapshots, `phase-A.json`, `phase-C.json`, per-state `*-ax.json`, `pixel-diff.json` and exact `diff-*.png` masks are in this directory. Full screenshots are `{original,a,b}-{initial,open}.png`. The expanded Transparency crops are `{original,a,b}-transparency-open.png`.

With Node, Playwright, Chromium 148 and Python/Pillow installed, run the scripts saved in the evidence directory. Set `RISER_CHROMIUM` to the Chromium 148 executable and, if needed, `RISER_PLAYWRIGHT` to the installed Playwright module path. Set `REPO` to the isolated checkout and `EVIDENCE` to the local evidence directory.

```powershell
node "$env:EVIDENCE/semantic-gate.cjs" "$env:REPO" "$env:EVIDENCE" A
node "$env:EVIDENCE/semantic-gate.cjs" "$env:REPO" "$env:EVIDENCE" C
python "$env:EVIDENCE/pixel-diff.py" "$env:EVIDENCE"
```
