# Rise AI — Campaign Discovery · Design recovery checkpoint (12/11/2026)

**Scope:** Desktop 1440px public route `/campaigns`, **NOT** Homepage Milestones; React/mobile/backend are **out of scope**.

## Recovered sources from ChatGPT Library
- Latest stand-alone Section 05 source: `section-05-paper-dossier-v13.html` (v1.3; Nov 12 2026 ~00:58 ICT).
- Screenshot: `section-05-v13-full-1440.png`, comparison: `section-05-v12-v13-comparison.png`, and package: `rise-ai-section-05-editorial-v13-review.zip`.
- Earlier `QA_SECTION_05.json` (20/20) was recorded for the earlier stand-alone version; it is **not** v1.3.1 QA evidence.
- Earlier full page `index(3)(1).html` (Section 05 Story Chapters v0.5) predates this newer paper dossier design; **do not replace v1.3 with that page**.
- Section 02: Library `section-02-c31-contrast-v2.html` (8 category C3.1 filter visuals).
- Section 04: Library `section-04-artistic-overflow.html`.
- Section 06: Library `index(3)(1).html` including Landscape Thread B and pagination.
- Homepage B2 v1.3.2 is locked and unchanged.

## This isolated change (v1.3 → v1.3.1)
1. Add an alpha-gradient mask to `.review-section > .bottom-valley` so the **upper boundary** blends into the section background.
2. Correct the stale HTML `data-version` marker from v1.2 to v1.3.1; title/content, three paper dossier rows, images, CTA copy and original landscape asset are preserved.
3. Do **not** touch any approved Homepage HTML, CSS, React, Backend or database.

## Evidence and acceptance
Workflow `Campaign Discovery Section05 HTML Desktop 1440 Review` runs real headless Chromium at **1440×900 DPR 1** on the committed HTML and uploads:
- full-page `before-v13-1440.png` with mask temporarily disabled
- full-page `after-v131-fade-1440.png`
- landscape-element close-up `after-bottom-transition-1440.png` and `qa-report.json`

Technical QA tests 3 dossier rows, 3 CTAs, images, data-version, horizontal overflow, exact pre/post card geometry, pixel change, demo notice and console errors.

**Owner approval: PENDING.** This PR is a visual-review checkpoint, not authorization to merge or to implement React.

**Integration blockers:** There is no verified fully assembled 1440px `/campaigns` page with latest Section02/04/05/06 and Header/Footer; section source HTML previews exist separately in Library. Creating a fictitious all-page PASS is prohibited. Assemble the full page on a follow-on integration stage *after* the last Section05 screenshot is approved. The preview includes fictional data and is not connected to real Backend; real API and accessibility/functionality gates must be verified separately.

**Source integrity:** ChatGPT Library remains the original unmodified source of v1.3; this checkpoint stores a new derivative. No mobile UI work.

## QA rerun note

The initial Chromium run generated full before/after images and passed all 10 checks reached, but the workflow failed on a screenshot **clip-coordinate error**, not an HTML rendering assertion. A subsequent follow-up fixes the screenshot instruction to capture the actual bottom landscape element. Re-run status must be verified from GitHub Actions; do not mark PASS from this note alone.
