# Project Overview

## Product
RISE AI (product context name: ImpactGuard AI) manages charitable campaigns with transparent fundraising, budget allocation, milestone tracking and controlled disbursement through a **milestone-based escrow** model.

Problems addressed:
- Donors cannot see how contributions are used after donating → public campaign pages, milestone progress, evidence metadata, disbursement records and the escrow ledger are readable for published campaigns.
- Creators need structure → budgets, milestones, evidence and disbursement requests with explicit rules.
- Administrators need traceability → append-only ledger, status history and audit log.

## Users
| Role | Summary |
|---|---|
| ADMIN | Reviews organizations, campaigns, evidence, disbursements, refunds and disputes; simulates payment confirmation; reads audit logs and statistics. |
| DONOR | Browses campaigns, makes simulated donations, requests refunds, opens disputes. |
| CAMPAIGN_CREATOR | Manages own organizations, campaigns, milestones, evidence and disbursement requests. |

## Phase 0 scope — IMPLEMENTED
Spring Boot modular monolith, MySQL + Flyway schema, JWT auth with refresh-token rotation, role and resource-level authorization, CRUD/query APIs, simulated escrow engine (contributions, reservations, releases, refunds), evidence, disputes with hold policy, audit and status history, demo seed data, OpenAPI, unit and integration test files.

## Out of scope — NOT IMPLEMENTED
Real payment gateway, real fund custody, blockchain/Avalanche, event broker, object storage for evidence files, email/notifications, frontend. (AI monitoring was added in Phase 2 as a separate service; see below.)

## Limitations (must stay visible to users and agents)
- Phase 0 escrow is an internal MySQL simulation, not a smart contract. No funds are locked on-chain.
- Database records cannot establish an organization's legal legitimacy. `VERIFIED` means *internal platform review only*.
- Submitted evidence is self-reported metadata; its existence does not prove proper use of funds.
- A future blockchain escrow cannot recover funds already transferred to an unrelated external wallet or bank account unless a supported mechanism exists.
- AI monitoring (Phase 2, IMPLEMENTED as MVP) runs in the separate `rise-ai-monitoring` service. The backend publishes minimized domain events through a transactional outbox; the monitor returns deterministic, explainable findings (for example the same receipt file reused across milestones, or a reconciliation inconsistency) that admins review in the backend. Findings are advisory: they never decide fraud, approve or release funds, or control keys.
- A dispute is not proof of fraud.
