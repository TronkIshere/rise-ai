# 03 — Charity-Specific Monitoring Requirements

Labels as defined in [README.md](README.md). Rule and capability IDs here are referenced by the roadmap and the acceptance checklist.

**Implementation status (2026-10-25):** the eight MVP rules in §3 (EV-01, EV-02, DB-01, DB-02, DB-03, LG-01, AO-01, DP-01) are implemented in `rise-ai-monitoring/src/main/java/com/riseai/monitoring/rise/rule/`. Their rule IDs are `rise.evidence.duplicate-file-hash`, `rise.evidence.financial-without-hash`, `rise.disbursement.minimal-evidence-approval`, `rise.disbursement.rapid-resubmission`, `rise.disbursement.repeated-requests`, `rise.ledger.reconciliation-inconsistent`, `rise.override.manual-adjustment` and `rise.dispute.misuse-after-release`. Human review HR-01, HR-02, HR-04 (data) and HR-05 is implemented in the backend. MS-01, MS-02, CL-01, RF-01, CH-01 and AI-01 to AI-05 are **not** implemented. Per-rule precision reporting (§5.1) is not implemented; review outcomes are stored, so it can be computed later.

## 0. Principles

1. **Monitoring is advisory.** A finding is a prompt for a human to look, never proof of fraud, misuse or bad intent. This restates backend business rules 24 ("Evidence is metadata; it does not prove legitimate spending") and 36 ("A dispute is never treated as proof of fraud"), `rise-ai-backend/docs/business-rules.md`.
2. **Do not duplicate backend invariants.** The backend already enforces deterministically, among others:
   - budget sum equals target (rules 11, 12, 16);
   - request amount ≤ milestone remaining (27);
   - one open request per milestone (26);
   - ≥ 1 accepted evidence before approval (28);
   - no release beyond planned (30);
   - no self-approval or conflict of interest (`ErrorCode.SELF_APPROVAL_NOT_ALLOWED`, `CONFLICT_OF_INTEREST`);
   - dual-control adjustments (DB CHECK `decided_by <> requested_by`, V5);
   - hourly ledger reconciliation (`escrow/ReconciliationService.java`).

   The monitor surfaces *patterns that are allowed but worth a look*, and the *outputs* of backend checks. It does not re-implement the checks.
3. **Use only data that exists.** Where the backend lacks the data, the capability is marked as needing a backend feature first. It is not faked in the monitor.
4. **Language.** Findings use "pattern", "inconsistency", "requires review", "not yet explained". They never use "fraud", "theft", "misuse confirmed", "criminal", "definitely" or "suspicious person". Enforced by a test over rule templates and by the AI output filter (M8).

## 1. Verified domain inventory (backend)

| Area | Tables (Flyway) | Key states / types (enums in `src/main/java/com/riseai`) |
|---|---|---|
| Users | `users`, `user_roles`, `refresh_tokens` | Roles ADMIN, DONOR, CAMPAIGN_CREATOR |
| Organizations | `organizations` | Verification UNVERIFIED, PENDING_REVIEW, VERIFIED, REJECTED |
| Campaigns | `campaigns` (`target_amount`, `budget_breakdown` JSON, `beneficiary_description` text, `beneficiary_count`, `start_at`, `end_at`, `fundraising_closed_at`) | DRAFT, PENDING_REVIEW, APPROVED, REJECTED, ACTIVE, PAUSED, COMPLETED, CANCELLED |
| Milestones | `campaign_milestones` (`planned_amount`, `reserved_amount`, `released_amount`, `due_at`, `release_conditions`, `expected_deliverables`) | PLANNED, READY, IN_PROGRESS, EVIDENCE_SUBMITTED, UNDER_REVIEW, PARTIALLY_DISBURSED, DISBURSED, DISPUTED, COMPLETED, CANCELLED |
| Donations | `donations` | PENDING, CONFIRMED, FAILED, CANCELLED, REFUND_PENDING, PARTIALLY_REFUNDED, REFUNDED. Payment methods are all `SIMULATED_*` |
| Escrow | `escrow_accounts`, `escrow_ledger_entries` (append-only) | Ledger types CONTRIBUTION_CONFIRMED, FUNDS_RESERVED, RESERVATION_CANCELLED, FUNDS_RELEASED, REFUND_RECORDED, REFUND_RESERVED, REFUND_RESERVATION_RELEASED, SETTLEMENT_REFUND_RECORDED, ADJUSTMENT_CREDIT, ADJUSTMENT_DEBIT |
| Disbursements | `disbursement_requests` | SUBMITTED, UNDER_REVIEW, NEEDS_MORE_EVIDENCE, APPROVED, REJECTED, RELEASED, CANCELLED, DISPUTED. Release is simulated ("no real transfer was executed", `DisbursementService.java:218`) |
| Evidence | `evidence_records` (`evidence_type`, `title`, `description`, `file_url`, `file_hash` CHAR(64) nullable, `external_reference`) | Types RECEIPT, INVOICE, DELIVERY_RECORD, PHOTO, PROGRESS_REPORT, ACTIVITY_DESCRIPTION, EXTERNAL_REFERENCE. Status SUBMITTED, ACCEPTED, REJECTED |
| Refunds | `refund_requests` | REQUESTED, APPROVED, REJECTED, CANCELLED, RECORDED |
| Disputes | `disputes`, `dispute_messages` | Types MISUSE_OF_FUNDS, MISSING_EVIDENCE, MISLEADING_INFORMATION, DELIVERY_NOT_RECEIVED, OTHER. Status OPEN, UNDER_REVIEW, RESOLVED_UPHELD, RESOLVED_DISMISSED, WITHDRAWN |
| Settlement / adjustments | `settlement_requests`, `escrow_adjustments` | Settlement type PRO_RATA_DONOR_REFUND; adjustment REQUESTED, APPLIED, REJECTED, CANCELLED |
| Reconciliation | `reconciliation_runs` | CONSISTENT, INCONSISTENT, FAILED |
| Audit | `audit_logs`, `status_history` | About 70 action strings, for example `DISBURSEMENT_RELEASED`, `ADJUSTMENT_APPLIED`, `RECONCILIATION_RUN_COMPLETED` |

Facts that shape the plan (VERIFIED):
- `file_hash` is **supplied by the client** in the evidence request (`evidence/EvidenceDtos.java:20` at the time of writing; now `dto/request/EvidenceRequest.java:14`, SHA-256 hex pattern) and stored lower-cased (`EvidenceService.java:80`). The backend stores no files and computes no hash. There is no uniqueness constraint and no duplicate check.
- There is **no expense, invoice-amount, vendor, line-item or beneficiary entity**. Evidence has no amount or date-of-expense field.
- Campaign edits are allowed only in DRAFT and REJECTED (`CampaignService.java:57`, `EDITABLE`). Milestone edits require an editable campaign. Post-publication changes to financial terms are therefore **blocked by the backend**.
- `CAMPAIGN_UPDATED` is audited without a field diff (`CampaignService.java:93`, metadata `null`).

## 2. Requirement mapping

| # | Requirement area | Backend support today | Events (designed / proposed) | Monitoring capability | Status |
|---|---|---|---|---|---|
| 1 | Donor contributions and funding records | `donations`, `DonationService` create/confirm/fail/cancel, ledger CONTRIBUTION_CONFIRMED, `GET /admin/donations` | DONATION_CONFIRMED (designed) | Context in projection (confirmed totals per campaign). Pattern rules on donors deferred: payments are simulated, and donor-level profiling raises privacy questions | Data VERIFIED; monitoring PLANNED post-MVP (RF-01) |
| 2 | Campaigns, goals, budgets, organizers, beneficiaries | `campaigns`, `organizations` with verification, `budget_breakdown` JSON | CAMPAIGN_CREATED, _SUBMITTED_FOR_REVIEW, _APPROVED, _PAUSED (designed); _COMPLETED, _CANCELLED (proposed) | Context only (campaign → organization, target, category). **Beneficiary-level monitoring unsupported**: beneficiaries are a free-text description and a count | Context in MVP; beneficiary monitoring needs a backend feature (not planned) |
| 3 | Milestone creation, submission, review, approval, rejection, completion | `campaign_milestones` lifecycle; approval and rejection happen on **evidence** and **disbursement requests**, not on the milestone itself | MILESTONE_CREATED, MILESTONE_EVIDENCE_SUBMITTED (designed); EVIDENCE_ACCEPTED/_REJECTED, MILESTONE_COMPLETED (proposed) | EV-01, EV-02 in MVP; MS-01, MS-02 post-MVP | MVP partial |
| 4 | Escrow, allocation, disbursement requests, completed transfers | `escrow_accounts`, ledger, `disbursement_requests`; transfers simulated | DISBURSEMENT_REQUESTED, _APPROVED, _RELEASED (designed); DISBURSEMENT_REJECTED, _EVIDENCE_REQUESTED (proposed) | DB-01, DB-02, DB-03 | MVP |
| 5 | Expenses, invoices, receipts, documents, budget utilization | Evidence **metadata** only; budget utilization only per milestone (released vs planned) | MILESTONE_EVIDENCE_SUBMITTED | EV-01 (exact duplicate file hash), EV-02 (financial evidence without hash). Expense-level checks (amount vs invoice, vendor reuse, category overrun) are **impossible** without a backend expense model | MVP partial; expense monitoring needs a backend feature first (future, decision) |
| 6 | Changes to campaign info, beneficiaries, milestone requirements, financial details | Changes blocked after DRAFT/REJECTED; organization edits after verification reset verification (`ORGANIZATION_VERIFICATION_RESET`) | Not in catalogue | Low need: backend controls prevent the risk. Optional post-MVP CH-01 (verification reset while campaigns are ACTIVE) | Covered by backend controls; CH-01 post-MVP |
| 7 | Unusual spending, repeated claims, duplicate receipts, inconsistent records, misuse | Pieces: file hashes, request history, reconciliation, disputes | Above, plus RECONCILIATION_RUN_COMPLETED, DISPUTE_OPENED | EV-01, DB-02, DB-03, LG-01, DP-01 | MVP (within the data limits stated per rule) |
| 8 | Delayed milestones, missing reports, budget overruns, unexpected progress | `due_at` exists; PROGRESS_REPORT is an evidence type; overruns are **prevented** by backend rules 16, 27, 30 | MILESTONE_COMPLETED (proposed) | MS-01 overdue milestone, MS-02 released without progress report. Both need time-based evaluation and the push return path (D3) | PLANNED post-MVP |
| 9 | Administrative overrides, disputes, approval history, audit | Dual-control adjustments, settlements, disputes, `status_history`, `GET /admin/audit-logs` | ADJUSTMENT_APPLIED, SETTLEMENT_EXECUTED, DISPUTE_RESOLVED (proposed); DISPUTE_OPENED (designed) | AO-01, DP-01; review trail for findings (HR-02) | MVP |
| 10 | Closure, final reporting, reconciliation | Fundraising close job, settlement (pro-rata refund), reconciliation runs; no final-report entity | CAMPAIGN_COMPLETED, SETTLEMENT_EXECUTED, RECONCILIATION_RUN_COMPLETED (proposed) | LG-01 in MVP; CL-01 closure consistency post-MVP | MVP partial |

## 3. Deterministic monitoring rules (monitor, M5)

Severity is fixed per rule. The "Rationale" column is the documented justification required for any severity; it is reviewed with D12. Thresholds marked *(D12)* are configuration placeholders, not validated values.

| ID | Rule | Inputs | Finding | Severity and rationale | Limitations | Evaluation | MVP |
|---|---|---|---|---|---|---|---|
| EV-01 | Same evidence file hash used for a different milestone | MILESTONE_EVIDENCE_SUBMITTED (`fileHash`, evidenceId, milestoneId, campaignId) + projection of prior hashes | EVIDENCE_INTEGRITY; subject = new evidence; facts = hash, earlier evidenceIds and milestones | HIGH when the earlier use is in another campaign; MEDIUM within the same campaign (one invoice can legitimately span milestones) | Hash is client-supplied and optional, so only exact byte reuse is caught, and evasion is trivial. Says nothing about authenticity | Unit fixtures (same/different hash, same/different campaign, redelivery); scenario test; precision from reviews | Yes |
| EV-02 | Financial evidence without a file hash | MILESTONE_EVIDENCE_SUBMITTED with type RECEIPT or INVOICE and no `fileHash` | DATA_QUALITY | LOW: reduces what EV-01 can detect; not a concern in itself | None material | Unit fixtures | Yes |
| DB-01 | Approval with minimal accepted evidence above an amount | DISBURSEMENT_APPROVED (`acceptedEvidenceCount`, amount) | DISBURSEMENT_PATTERN | MEDIUM: allowed by rule 28, but large releases on one document deserve a second look. Threshold amount *(D12)* | Counts documents, not their quality | Unit fixtures at, below and above the threshold | Yes |
| DB-02 | Rapid resubmission after rejection | DISBURSEMENT_REJECTED then DISBURSEMENT_REQUESTED on the same milestone within a window *(D12, placeholder 24 h)* | DISBURSEMENT_PATTERN | MEDIUM: repeated claims are a recognised review trigger; legitimate corrections also look like this | Requires both events; out-of-order arrival gives no finding (rather than a wrong one) | Unit fixtures including reversed order | Yes |
| DB-03 | Many requests on one milestone | Count of DISBURSEMENT_REQUESTED per milestone ≥ N *(D12, placeholder 3)* | DISBURSEMENT_PATTERN | LOW: partial disbursements are legitimate (PARTIALLY_DISBURSED exists) | A count only | Unit fixtures | Yes |
| LG-01 | Reconciliation inconsistency | RECONCILIATION_RUN_COMPLETED with `runStatus` INCONSISTENT or FAILED | LEDGER_INTEGRITY; recommendedAction CHECK_RECONCILIATION | CRITICAL for INCONSISTENT: a broken money invariant always needs a human. HIGH for FAILED: the check did not run | Surfaces the backend's own result; adds no detection | Unit fixture per status | Yes |
| AO-01 | Manual ledger adjustment applied | ADJUSTMENT_APPLIED | ADMINISTRATIVE_OVERRIDE; recommendedAction REVIEW_ADMIN_ACTION | INFO below an amount threshold, MEDIUM above *(D12)*. Dual control already applies; this is oversight visibility | No judgement of the reason (free text is excluded) | Unit fixtures | Yes |
| DP-01 | Misuse-of-funds dispute on a milestone that already received funds | DISPUTE_OPENED (`disputeType` MISUSE_OF_FUNDS, milestoneId) + projection of DISBURSEMENT_RELEASED | DISPUTE_PATTERN | HIGH: funds have left escrow, and the backend cannot claw back released funds (rule 36) | A dispute is an allegation, not a fact | Unit fixtures (released / not released / order reversed) | Yes |
| MS-01 | Milestone overdue | Projection: milestone `due_at` passed, status not COMPLETED/CANCELLED | MILESTONE_PROGRESS | LOW, raised to MEDIUM after a grace period *(D12)* | Needs a monitor scheduler and a push return path (D3); `due_at` must be in the event payload | Clock-controlled tests (`MutableClock` exists) | Post-MVP |
| MS-02 | Funds released, no progress report | DISBURSEMENT_RELEASED, then no PROGRESS_REPORT evidence within a window | MILESTONE_PROGRESS | LOW | Time-based; same needs as MS-01 | Clock-controlled tests | Post-MVP |
| CL-01 | Closure consistency | CAMPAIGN_COMPLETED / SETTLEMENT_EXECUTED payload totals: confirmed = released + refunded + settled + remaining | LEDGER_INTEGRITY | HIGH on mismatch | Duplicates reconciliation unless payload totals come from a different source; evaluate after LG-01 data | Unit fixtures | Post-MVP |
| RF-01 | Refund cluster on a campaign | Count of REFUND_REQUESTED per campaign per window | DISPUTE_PATTERN | LOW | Donor behaviour; privacy review needed | Unit fixtures | Post-MVP |
| CH-01 | Organization verification reset while campaigns are active | ORGANIZATION_VERIFICATION_RESET event (would need adding) + projection | ADMINISTRATIVE_OVERRIDE | LOW | Needs a new event type | Unit fixtures | Post-MVP |

The existing generic monitor rules remain active as validation: required fields, timestamps, supported event types, payload limits. The generic `attribute-threshold` and `reported-severity` rules stay available but are not configured for Rise events in MVP.

## 4. AI-assisted capabilities (monitor, M8)

| ID | Capability | Inputs | Output | Limitations | Evaluation | MVP |
|---|---|---|---|---|---|---|
| AI-01 | Plain-language explanation of a rule finding | Finding facts and allowlisted event scalars (D5); no IDs or free text | `aiNote` on the finding (assessment, self-reported confidence, text), labelled "AI-generated, unverified" | Local model quality varies; can paraphrase but adds no new evidence | Offline evaluation gate (§5.2) before enabling in the admin UI | Optional (D6): built, shipped disabled |
| AI-02 | Campaign monitoring summary for reviewers | All open findings for one campaign plus event counts | Short summary of outstanding issues | Same as AI-01; risk of overstating patterns | §5.2 plus reviewer usefulness rating | Post-MVP |
| AI-03 | Review queue prioritisation | Finding features and reviewer history | Ordering suggestion | Needs labelled outcomes first; until then the queue orders by severity then age (deterministic) | Ranking metrics against review outcomes | Advanced |
| AI-04 | Evidence document summarisation | Document content | Summary of what a document shows | **Impossible today.** The backend stores no documents, only a URL and a client-supplied hash. Fetching `file_url` from the monitor would create SSRF and privacy risks | Not applicable until document storage exists | Not now |
| AI-05 | Cross-record text inconsistency (for example a description contradicting the evidence) | Free text from campaigns, evidence and disputes | Inconsistency hints | Requires sending free text (possibly personal data) to the model, which conflicts with D5; prompt-injection surface | Not applicable until D5 is revisited | Not now |

Existing AI guarantees that remain mandatory:
- AI never changes severity or risk level.
- AI failure never removes or downgrades findings.
- Output must pass `AiOutputParser` schema validation.
- `FakeAiEventAnalyzer` and `StubOllamaServer` keep all tests deterministic and model-free.

## 5. Scores, confidence and evaluation

### 5.1 Rules
- **No numeric risk score in `finding.v1`.** Severity is categorical and fixed per rule, with the rationale in §3. A score is not introduced until it can be calibrated.
- Deterministic rules have no "confidence": the condition either held or did not. The `facts` field shows exactly what held.
- **Precision tracking (M7):** per rule and per quarter, `precision = CONCERN_CONFIRMED / (CONCERN_CONFIRMED + DISMISSED)`. Recall cannot be measured, because there is no ground truth for unflagged cases; say so in any report.
- **Rule review trigger:** precision below a product-set floor over at least 30 reviewed findings → rule threshold or severity review (D12).

### 5.2 AI
- `confidence` is the model's **self-reported** qualitative value (LOW / MEDIUM / HIGH, `core/analysis/AiConfidence.java`). It is displayed as "model self-assessment, not calibrated" and never used for sorting or thresholds.
- Gate before AI-01 is shown to reviewers (all measured on an offline set of at least 200 labelled scenarios generated by the extended `test-simulator` plus hand-written edge cases):
  - schema-valid output ≥ 99 %;
  - **grounding** ≥ 95 %: every number, enum value and entity type in the text appears in the supplied facts (automated check);
  - forbidden-language occurrences = 0 (§0.4 list);
  - p95 latency below `ollama.request-timeout`;
  - reviewer agreement with the `assessment` field reported, not gated, for the first release.
- Calibrating a numeric score (Brier score, reliability curve) is **advanced research**. It needs several hundred reviewed findings per category and has no target date.

## 6. Human review (backend, M7)

| ID | Capability | Inputs | Output | Limitations | MVP |
|---|---|---|---|---|---|
| HR-01 | Findings queue | `monitoring_findings` | `GET /api/v1/admin/monitoring/findings` with filters (status, severity, category, campaign), sorted by severity then age | ADMIN only | Yes |
| HR-02 | Review action with note | Finding + ADMIN decision | Status transition, audit and status history entries | No automatic action follows | Yes |
| HR-03 | Act through existing workflows | Reviewer judgement | Reviewer uses existing endpoints: request more evidence, reject a request, pause a campaign, resolve a dispute. Each runs the backend's own authorization and rules | The finding stores no link to the action in MVP. A `linkedAuditLogId` is post-MVP | Yes (process, not code) |
| HR-04 | Feedback labels | Review outcomes | Precision metrics (§5.1); AI evaluation labels | Labels reflect reviewer judgement, not ground truth | Yes (data); reporting post-MVP |
| HR-05 | Reviewer separation | Who reviewed vs who is the subject | Block review by the campaign creator or the actor of the source event (same principle as `CONFLICT_OF_INTEREST`) | Admins who are also creators exist only if roles overlap | Yes |

## 7. Explicitly not now

| Not now | Why |
|---|---|
| Automatic holds, freezes or rejections driven by findings | Violates ADR-004 and the phase-2 AI permission boundary; AI and monitoring stay advisory |
| Numeric fraud or risk scores | No labelled data to calibrate; would read as a verdict |
| Document or image analysis | No document storage in the backend; SSRF and privacy risks |
| Donor profiling or behaviour scoring | Privacy; payments are simulated so the signal would be meaningless |
| Hosted LLM providers | D13; donor and beneficiary data protection |
| Message broker | One producer, one consumer, low volume; the outbox and HTTP give the required guarantees (architecture §4) |
| Expense, invoice and vendor monitoring | Requires a backend expense model, which is a product decision outside this module |
| Blockchain or Avalanche correlation | Backend Phase 3 is not implemented |
