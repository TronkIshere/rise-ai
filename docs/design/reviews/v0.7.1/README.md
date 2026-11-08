# Codex handoff — Riser AI Homepage v0.7.1 (visual rhythm review)

**Status:** `COMPOSITION REFINEMENT CANDIDATE — PENDING OWNER VISUAL APPROVAL`  
**Target:** Desktop 1440px only  
**Task type:** Standalone HTML handoff for independent review, **not React implementation**  
**Source:** ChatGPT-generated v0.7.1 review artifact from 2026-11-05, imported byte-for-byte

## Open files directly from Git

- [Homepage visual rhythm v0.7.1 HTML](./homepage-visual-rhythm-v0.7.1.html)
- [Review and rationale](./VISUAL_RHYTHM_REVIEW.md)
- [Archived v0.7.0 Master (unchanged)](../../wireframes/homepage-master-v0.7.0.html)
- [Design Decision Log](../../DESIGN_DECISION_LOG.md)
- [UI Guardrails](../../UI_GUARDRAILS.md)
- [UI Token Registry](../../UI_TOKEN_REGISTRY.md)
- [Layout token documentation](../../WIREFRAME_LAYOUT_TOKENS.json)

**Original source hashes:**
- HTML SHA-256: `5df675bbaa5a4cd4ae7c0674a526f450627cda13d9011a9791cb42bed722a398`
- Review SHA-256: `f7f536901a2ef7e623d8662659228094776c5c664d1ad479d939b6fe8a90097d`

Images/screenshots from the chat are **not** archived in this PR. For visual inspection render the HTML locally in Chromium at desktop 1440px; do not treat a GitHub raw-file code preview as a screenshot.

## What to do in Codex

1. Fetch branch `docs/homepage-v071-codex-handoff-20261105`; read this README and the two linked files **before touching any source code**.
2. Read `rise-ai-frontend/AGENTS.md` plus the baseline rules and token docs above.
3. Open `homepage-visual-rhythm-v0.7.1.html` as an offline/static preview at 1440px; capture the complete page and review Story, Milestones and Transparency. This is a static review HTML, not a React build.
4. Compare it to the archived v0.7.0 Master. Review text hierarchy, spacing, content readability, the transition between Story and Transparency, and the preservation of all already-approved sections.
5. Report observable deviations and whether it is appropriate to approve the **composition only**. Separate verified observations from aesthetic suggestions.

### Strict non-goals

- Do not change React components, API, Backend, Monitoring, deployment, runtime tokens or `AGENTS.md`.
- Do not overwrite the approved/archived v0.7.0 HTML.
- Do not alter Hero, category layout, primary Hybrid A+C card, compact cards or provisional footer.
- Do not approve High-Fidelity or claim E2E verification based only on this HTML.
- Do not promote provisional colors, typography, backgrounds, shadows or radii to approved tokens.
- Do not merge PRs as part of review. PR #9 remains a separate governance task.

## Branch/PR ancestry

This handoff is intentionally stacked on `docs/ui-baseline-governance-20261105` (Draft PR #9), to reuse the design documentation without modifying that PR or editing `master`. This branch should remain a Draft review artifact until the owner accepts v0.7.1.
