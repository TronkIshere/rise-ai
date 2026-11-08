# B2 Development Master v1.3.2 — approved Homepage baseline / React handoff

**Owner approval — 2026-11-08:** Homepage B2 v1.3.2 is formally approved in full for React Desktop 1440px, **including Milestones Journey Landscape background**. This supersedes the provisional status in the historical design-source snapshot at commit `215d01ff97fda6a03016c402eb4c7460bb7c8c3b`. The original archived HTML/CSS and assets remain unchanged as the approved visual authority.

**Approved sections:** Hero + 01–02–03 Journey; Categories C3.1; Campaign Discovery (A+C 1+2); Featured Story; Milestones K1 plus Journey Landscape; Transparency T1.2; Footer editorial typography v1.3.2 and wave/background. Sequence remains locked.

**Current delivery stage:** React implementation on a separate feature branch/PR. React build/testing and visual parity must be reviewed independently; **design approval is not code approval or deployment permission**. Only Homepage route and Homepage-specific components/assets/tokens/styles are in scope; no Mobile/Tablet UI work. Auth, donations, Backend, Monitoring and Admin remain untouched.

**Semantics:** Public campaign data must come from Backend `ACTIVE` listing, optional category filter; funding/escrow/disbursements are simulated ledgers, never cash transfers; concept images and story/milestones/dossier narratives are labelled illustrative demo, never evidence or field verification. AI provides non-authoritative advisory signals; Admin has final authority.

Read `REACT_IMPLEMENTATION.md` for source mapping, practical integration limitations and the mandatory verification/PR gate. Do not merge to `master` without review and full tests.

## React Desktop 1440px — owner visual acceptance (2026-11-08)

**Owner decision: VISUAL PASS / APPROVED** for the actual React Homepage B2 v1.3.2 at desktop viewport **1440×900, DPR 1**, compared side by side against the approved standalone HTML B2 Development Master. Approval was explicitly provided by the owner in conversation on **2026-11-08** after review of the Hero visual-fidelity correction. The original HTML Master and all approved section designs remain the visual authority; this acceptance is not a redesign.

- **React commit accepted:** `2750ed773b80a45abb068c408bdd9da1f2dc1f99` (PR #12, branch `feat/homepage-b2-v132-desktop-20261108`). Hero 3-line heading and CTA positions now agree with the approved reference; Hero screenshot geometry: title height 192.46875px, title top 173.5px, CTA bottom 498.546875px, Journey top 574px, identical between React and HTML reference.
- **Scope signed off:** Hero + Journey 01–02–03; Categories C3.1 (8 cards); Campaign Discovery (1 large + 2 compact, fixture data); Featured Story; Milestones K1 + Journey Landscape; Transparency T1.2; Footer v1.3.2. Desktop 1440px only. No new Mobile/Tablet UI authorized.
- **Proof:** [Chromium 1440px visual QA run](https://github.com/TronkIshere/rise-ai/actions/runs/42458193873) (**PASS**) and [React/reference screenshots + QA JSON](https://github.com/TronkIshere/rise-ai/actions/runs/42458193873/artifacts/13609055199). [Frontend checks](https://github.com/TronkIshere/rise-ai/actions/runs/42458197440) and [repository CI](https://github.com/TronkIshere/rise-ai/actions/runs/42458197415) **PASS** at the same React commit. Artifact retention is time limited.
- **Boundary:** The Chromium QA intercepts Backend responses with a labelled test fixture; this **does not certify live Backend integration, production data, payment, deployment or full end-to-end functional acceptance**. It is a visual acceptance milestone and associated technical QA only.
- **Git gate:** Keep PR #12 unmerged and undeployed; stacked dependencies PR #9, #10, #11 are still Draft. No direct merge to `master` without separate code review, ordered integration and live-Backend verification.
