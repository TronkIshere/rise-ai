# 08 — D12 Threshold Proposal (for owner review)

Status: **PROPOSAL. Nothing here is active.** The shipped configuration keeps the placeholder values. No production data on campaign size, milestone size or disbursement behaviour was available, so every number below is a pilot hypothesis to be confirmed with real data and with the per-rule precision report (`GET /api/v1/admin/monitoring/findings/precision`), not a validated value.

## Rules and their numeric inputs

Of the eight Rise rules, only four have numeric parameters. EV-01, EV-02, DP-01 and LG-01 are structural and have none.

| Rule | Severity | Parameter (property under `monitoring.rise`) | Placeholder | Rule input fields |
|---|---|---|---|---|
| DB-01 minimal-evidence approval | MEDIUM | `minimal-evidence-approval-amount` | 50,000,000 VND | `amount`, `acceptedEvidenceCount` of `DISBURSEMENT_APPROVED` |
| DB-01 | | `minimal-evidence-count` | 1 | same |
| DB-02 rapid resubmission | MEDIUM | `resubmission-window` | 24 h | `DISBURSEMENT_REQUESTED` time vs. earlier `DISBURSEMENT_REJECTED` on the same milestone |
| DB-03 repeated requests | LOW | `max-requests-per-milestone` | 3 | count of `DISBURSEMENT_REQUESTED` per milestone |
| AO-01 manual adjustment | MEDIUM at or above the amount, INFO below | `adjustment-review-amount` | 10,000,000 VND | `amount` of `ADJUSTMENT_APPLIED` |

## Observations from the code and seed data

1. **DB-01 cannot fire on the demo data at the placeholder.** The seeded milestones are 30–50 million VND and the largest seeded release request is 40 million, so a 50 million threshold never triggers for them. The comparison is `amount >= threshold`.
2. **All amounts are absolute.** The events carry no campaign target or milestone planned amount, so a rule cannot say "more than half of the milestone". A relative threshold needs the backend to add `milestonePlannedAmount` (and, for AO-01, the escrow `availableBalance`) to the corresponding event payloads. That is a contract change and is not proposed for activation here.
3. **DB-03 may flag normal behaviour.** `PARTIALLY_DISBURSED` is a supported milestone state, so several partial requests on one milestone are legitimate. A limit of 3 would flag an ordinary two-instalment plan with one correction.
4. **DB-02 fires on any resubmission in the window**, including a corrected request after a rejection; it is a prompt to look at what changed, which is why it is MEDIUM at most.
5. **AO-01 already emits INFO for every applied adjustment.** Adjustments are dual-controlled and rare, so the threshold only decides MEDIUM versus INFO.

## Proposal

| Parameter | Placeholder | Proposed pilot value | Rationale | Evidence needed before production |
|---|---|---|---|---|
| `minimal-evidence-approval-amount` | 50,000,000 | **20,000,000** VND | About half of the largest seeded milestone, so mid-size releases on one evidence record are visible. Absolute amounts are a stop-gap until relative thresholds exist | Distribution of approved disbursement amounts; reviewer precision for DB-01 over a pilot period |
| `minimal-evidence-count` | 1 | **1** (unchanged) | Approval already needs at least one accepted evidence record (business rule 28), so the rule means "only the minimum was provided" | None |
| `resubmission-window` | 24 h | **24 h** (unchanged) | No data suggests a better window; the 23 h 59 min and 24 h 01 min boundaries are covered by scenarios below | Median time between rejection and corrected resubmission |
| `max-requests-per-milestone` | 3 | **5** | Allows legitimate partial releases; flags unusual fragmentation. Severity stays LOW | Distribution of requests per milestone |
| `adjustment-review-amount` | 10,000,000 | **10,000,000** (unchanged) | Every adjustment is already INFO and dual-controlled; no basis to move MEDIUM | Adjustment amounts in practice |

Longer term (owner decision, needs a backend event change): replace DB-01 and AO-01 absolute amounts with percentages, for example "approval at or above 50 % of the milestone planned amount". No percentage is proposed yet because no data supports one.

## Test scenarios

The simulator's Rise suite (`--simulator.suite=rise`) already contains the boundary pair for each numeric rule:

| Scenario | Checks |
|---|---|
| RISE-010 / 011 | DB-01 at the threshold fires; one cent below does not |
| RISE-012 | DB-01 with more than the minimum evidence does not fire |
| RISE-013 / 014 | DB-02 fires inside the window; a rejection older than the window does not |
| RISE-015 | DB-03 fires on the request that reaches the limit |
| RISE-016 / 017 | AO-01 is MEDIUM at the threshold and INFO one cent below |

Scenarios to add once the owner confirms values: DB-02 at 23 h 59 min and 24 h 01 min; DB-03 with one fewer request than the limit; DB-01 at exactly the minimal evidence count plus one.

## Dry run of the proposal (not activated)

On 2026-10-29 a throwaway monitor was started with `MONITORING_RISE_MINIMAL_EVIDENCE_APPROVAL_AMOUNT=20000000` and `MONITORING_RISE_MAX_REQUESTS_PER_MILESTONE=5`, and the suite was run with `--simulator.rise-approval-amount=20000000 --simulator.rise-max-requests-per-milestone=5`: **20/20 passed, 8/8 rules covered**. This shows that the scenarios follow configuration. It does not validate the values. A run with the Core at 60,000,000 and the suite at 50,000,000 failed RISE-010 as expected.

## How the owner can apply a decision
Set the environment variables (for example `MONITORING_RISE_MINIMAL_EVIDENCE_APPROVAL_AMOUNT`) on the monitor, restart it, and run the suite with matching `--simulator.rise-*` flags. Record the approved values, the date and the approver in `README.md` (D12) and in this file.
