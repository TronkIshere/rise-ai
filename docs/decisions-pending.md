# Pending owner decisions: default proposals

**Status: PROPOSED, awaiting project owner confirmation.** Written 2026-11-11. Nothing in this file is decided, and none of it is applied to code or configuration. Each section gives the current state (checked in the repository), a default proposal, the reasons, and the trade-offs.

To confirm or change an item, edit its **Decision** line with the choice, the date and the approver, then apply it in a separate change. Until then the current state stays in force.

Out of scope here: checklist items M1.9 and M1.11 (the two git-history exceptions from the monitoring rename). They stay **open** and are not proposed for acceptance (`05-acceptance-checklist.md`).

| # | Topic | Proposed default | Blocks |
|---|---|---|---|
| P1 | Monitoring rule thresholds (D12, M5.1) | Adopt the pilot values in `08-d12-threshold-proposal.md` for a time-boxed pilot | M5.1 |
| P2 | AI notes for admins (M8.7) | Keep AI notes **off** until the M8.5 evaluation gate passes; then show them as clearly labelled, advisory text | M8.7 |
| P3 | Alert destination and owner | One webhook into a team chat channel, owned by a named on-call administrator | M9.6 (real destination) |
| P4 | Data and alert retention | Keep all financial and audit history; purge only delivery bookkeeping, with stated periods | Phase 4 "Data" |
| P5 | Admin two-factor login | TOTP (authenticator app) for every ADMIN account, enforced at login | Phase 4 "Security" |
| P6 | Smart contract security review | External review of `CampaignEscrow`, `RiseVND` and `RiseAnchor` before any public testnet use with real users | Phase 3 acceptance |

---

## P1. Monitoring rule thresholds (D12, M5.1)

**Current state.** The monitor ships placeholder values (`monitoring.rise.*`): `minimal-evidence-approval-amount` 50,000,000 VND, `minimal-evidence-count` 1, `resubmission-window` 24 h, `max-requests-per-milestone` 3, `adjustment-review-amount` 10,000,000 VND. `docs/ai-monitoring/08-d12-threshold-proposal.md` already analyses them; it says that DB-01 can never fire on the seeded data at 50,000,000, and that DB-03 at 3 may flag normal partial releases.

**Proposal.** Adopt the pilot values from `08-d12-threshold-proposal.md` for a **90-day pilot**: DB-01 amount **20,000,000 VND**, DB-03 limit **5**, the other three unchanged. At the end of the pilot, review the per-rule precision report (`GET /api/v1/admin/monitoring/findings/precision`) and either confirm or adjust.

**Reasons.** These are the only values that have been dry-run (Rise suite 20/20 on 2026-10-29). The placeholders hide DB-01 entirely on realistic amounts.

**Trade-offs.**
- More DB-01 findings mean more reviewer work, and the precision may turn out low.
- Absolute VND amounts don't scale with campaign size. Percentage thresholds would need the backend to add `milestonePlannedAmount` to event payloads (a contract change, not proposed here).
- Alternative: keep the placeholders and accept that DB-01 rarely fires.

**Decision:** _open_

## P2. AI notes for admins (M8.7)

**Current state.** The backend already stores the monitor's `aiNote` per finding occurrence (migration V11) and exposes it to ADMIN only. The frontend renders it on the finding detail page when present (`AdminFindingPage.tsx`, "Ghi chú giám sát"). The monitor generates no notes by default: `aiNotesEnabled = false` (`MonitoringProperties`), and AI is off in the `riseai` profile (`MONITORING_AI_ENABLED:false`). The M8.5 evaluation gate (200 valid scenarios) has **not** been run. The current evaluation set is 21 distinct scenarios (`rise-ai-monitoring/docs/evaluation/README.md`).

**Proposal.** Keep AI notes **off** in every shared environment until the M8.5 gate passes with the chosen local model (D13: local Ollama only). After it passes, enable them for administrators only, with the existing advisory wording, and never in public or donor views.

**Reasons.** The UI path already exists, so turning notes on is just a configuration switch. The risk is the content: an ungated model could produce misleading or ungrounded text next to a finding about a real campaign.

**Trade-offs.**
- Reviewers lose a possible explanation aid until the gate passes.
- The gate run takes about three hours of model time on the development machine (estimate in the evaluation README).
- Alternative: enable now with a "beta, unverified" label. Not recommended, because a label does not stop reviewers from relying on the note.

**Decision:** _open_

## P3. Alert destination and owner

**Current state.** Every alert is stored in `alert_notifications` (V10) whether or not a destination exists. Delivery happens only when `RISE_ALERT_WEBHOOK_URL` (and optionally `RISE_ALERT_WEBHOOK_TOKEN`) is set; it is blank by default. Delivery is at-least-once with retries; FAILED alerts are never retried automatically, and an admin can requeue them (`POST /api/v1/admin/monitoring/alerts/{id}/requeue`, audited). No real destination has ever been used; tests use stubs only.

**Proposal.**
- **Destination:** one incoming webhook into a private team chat channel (whichever chat tool the team already uses), configured through `RISE_ALERT_WEBHOOK_URL` and a token kept in the secret store, never in the repository.
- **Owner:** one named on-call administrator per week, with a named backup. They acknowledge CRITICAL alerts within one working day and check `GET /api/v1/admin/monitoring/alerts/stats` for FAILED rows daily.

**Reasons.** It reuses the existing webhook client without new code. A chat channel is visible to the whole team, and a named owner prevents alerts that everyone assumes someone else handles.

**Trade-offs.**
- A chat webhook is not a paging system. Nobody is woken at night, which is acceptable for an advisory, simulation-only platform but not for real funds.
- At-least-once delivery means occasional duplicate messages.
- Alternative: email via a mail relay. That needs new code (no mail client exists) and is easier to ignore.

**Decision:** _open_

## P4. Data and alert retention

**Current state (from configuration).**

| Data | Current rule |
|---|---|
| Published outbox rows (backend) | Purged after 30 days (`published-retention: P30D`) |
| Monitor deduplication records | 24 h (`MONITORING_DEDUPLICATION_RETENTION`) |
| Monitor event projection | 400 days (`projection-retention: 400d`, D9) |
| Ledger, audit log, status history, findings, reviews | Never deleted (append-only guards on ledger and audit, V2) |
| `alert_notifications` (PENDING, RETRYING, SENT, FAILED) | Never purged; no cap |
| Chain commands and anchors (V12, V13) | Never purged |

**Proposal.**
- Keep the ledger, audit log, status history, findings and reviews **indefinitely** (they are the accountability record, and on-chain anchors depend on the full history).
- Keep the existing 30-day, 24-hour and 400-day rules.
- Add one rule: purge `SENT` alerts after **90 days**. Keep FAILED and still-pending alerts until an admin handles them.
- Defer personal-data deletion requests (pseudonymising users without touching ledger integrity, Phase 4) to a separate design, because it needs legal input for the target jurisdiction.

**Reasons.** Only delivery bookkeeping grows without bound and has no accountability value once delivered. Deleting financial or audit history would break reconciliation and the anchors' Merkle roots.

**Trade-offs.**
- Indefinite retention of audit data that names users conflicts with data-minimisation principles. The pseudonymisation design is the mitigation and is still missing.
- Purging SENT alerts loses delivery evidence older than 90 days; the audit log keeps the underlying events.
- Implementing the 90-day purge needs a small scheduled job (not written).

**Decision:** _open_

## P5. Admin two-factor login

**Current state.** Login is email and password with JWT access tokens and refresh tokens. There is no MFA and no integration point for one (`phase-4-production-readiness.md`, "Admin MFA: OPEN"). Per-account lockout is not implemented; auth is rate limited per IP (20 per minute).

**Proposal.** TOTP (RFC 6238, any authenticator app), **required for every ADMIN account**, optional for other roles. Enrolment requires the current password; recovery uses one-time recovery codes generated at enrolment. Add per-account lockout after repeated failed second factors.

**Reasons.** Admins approve releases, refunds and adjustments, so their accounts are the highest-value target. TOTP needs no external service or SMS provider and works offline.

**Trade-offs.**
- New code: an enrolment flow, a secret column, recovery codes and a frontend screen. Moderate effort.
- Lost devices need an admin-recovery procedure (another admin, under dual control).
- Alternatives: an external identity provider (OIDC) moves the problem out of the codebase but adds a dependency and a vendor. WebAuthn or passkeys are stronger, but more work and less familiar to users.

**Decision:** _open_

## P6. Smart contract security review

**Current state.** `CampaignEscrow`, `RiseVND` and `RiseAnchor` are tested locally: 143 Foundry tests, 5 invariants and three deliberate mutations caught (`rise-ai-avalanche/README.md`). They were deployed and exercised on Fuji on 2026-11-10. They have **no external security review**. Fuji signing uses operator-held hot keystores (ADR-009), not KMS or a multisig.

**Proposal.**
- **Before any public testnet use with real users:** an external review of the three contracts by an independent reviewer, plus an automated static-analysis pass (for example Slither) recorded in the repository. Fix or explicitly accept every finding.
- **Before any non-testnet use:** apply ADR-005 in full (KMS/HSM keys, multisig approver) and repeat the review on the final code.
- Until then, keep the current limits: Fuji or local Anvil only, simulated rVND only, mainnet refused.

**Reasons.** The contract enforces the objection window, dispute block, evidence acceptance, milestone caps and fixed beneficiary. Those are exactly the guarantees a reviewer must confirm, and tests written by the same author tend to share its blind spots.

**Trade-offs.**
- An external review costs money and time, and code changes after it need a re-review.
- Static analysis alone is cheap but misses logic errors.
- Alternative: an internal peer review by a second developer. Better than nothing, but not independent.

**Decision:** _open_
