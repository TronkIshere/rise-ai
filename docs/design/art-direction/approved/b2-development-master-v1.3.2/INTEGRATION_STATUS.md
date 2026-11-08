# B2 Development Master v1.3.2 — approved Homepage baseline / React handoff

**Owner approval — 2026-11-08:** Homepage B2 v1.3.2 is formally approved in full for React Desktop 1440px, **including Milestones Journey Landscape background**. This supersedes the provisional status in the historical design-source snapshot at commit `215d01ff97fda6a03016c402eb4c7460bb7c8c3b`. The original archived HTML/CSS and assets remain unchanged as the approved visual authority.

**Approved sections:** Hero + 01–02–03 Journey; Categories C3.1; Campaign Discovery (A+C 1+2); Featured Story; Milestones K1 plus Journey Landscape; Transparency T1.2; Footer editorial typography v1.3.2 and wave/background. Sequence remains locked.

**Current delivery stage:** React implementation on a separate feature branch/PR. React build/testing and visual parity must be reviewed independently; **design approval is not code approval or deployment permission**. Only Homepage route and Homepage-specific components/assets/tokens/styles are in scope; no Mobile/Tablet UI work. Auth, donations, Backend, Monitoring and Admin remain untouched.

**Semantics:** Public campaign data must come from Backend `ACTIVE` listing, optional category filter; funding/escrow/disbursements are simulated ledgers, never cash transfers; concept images and story/milestones/dossier narratives are labelled illustrative demo, never evidence or field verification. AI provides non-authoritative advisory signals; Admin has final authority.

Read `REACT_IMPLEMENTATION.md` for source mapping, practical integration limitations and the mandatory verification/PR gate. Do not merge to `master` without review and full tests.
