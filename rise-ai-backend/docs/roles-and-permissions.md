# Roles and Permissions — IMPLEMENTED

Roles are an enum stored in `user_roles` (ADR-002). A user may hold several roles; every check also applies resource ownership and conflict-of-interest rules. Identity always comes from the JWT subject → DB lookup; client-supplied ids/roles are never trusted.

| Capability | ADMIN | DONOR | CAMPAIGN_CREATOR | Anonymous |
|---|---|---|---|---|
| Register (DONOR or CAMPAIGN_CREATOR only) | – | – | – | ✔ |
| View/update own profile | ✔ | ✔ | ✔ | – |
| Manage users, suspend, change roles (not self) | ✔ | – | – | – |
| Create organization | ✔ | – | ✔ | – |
| Edit/delete organization | ✔ | – | own | – |
| Verify organization (not own) | ✔ | – | – | – |
| Browse published campaigns, milestones, evidence, disbursements, ledger | ✔ | ✔ | ✔ | ✔ |
| View DRAFT/PENDING/APPROVED/REJECTED campaigns | ✔ | – | own | – |
| Create/edit campaign & milestones (DRAFT/REJECTED) | – | – | own | – |
| Submit campaign / publish approved campaign | publish | – | own | – |
| Approve / reject campaign (not own) | ✔ | – | – | – |
| Pause / resume / complete campaign | ✔ | – | – | – |
| Cancel campaign | any non-final | – | own, pre-publication | – |
| Start milestone | – | – | own | – |
| Complete / cancel milestone | ✔ (not creator) | – | – | – |
| Submit evidence | – | – | own campaign | – |
| Review evidence (not submitter/creator) | ✔ | – | – | – |
| Create simulated donation (not own campaign) | – | ✔ | – | – |
| Confirm / fail simulated donation (not own) | ✔ | – | – | – |
| View own donations | ✔ | ✔ | ✔ | – |
| View campaign donations with donor identity | ✔ | – | masked, own campaign | masked, confirmed only |
| Request disbursement | – | – | own campaign | – |
| Review / approve / reject / release disbursement (not requester/creator) | ✔ | – | – | – |
| Request refund | ✔ (any) | own donation | – | – |
| Approve / reject / record refund (not requester/donor) | ✔ | – | – | – |
| Open dispute | ✔ | with confirmed donation | not on own campaign | – |
| Message in dispute | ✔ | opener | creator of campaign | – |
| Assign / resolve dispute (not a party; resolver = assignee) | ✔ | – | – | – |
| Read all audit logs, statistics, reconciliation | ✔ | – | – | – |
| Trigger reconciliation run, read run history (Phase 1) | ✔ | – | – | – |
| Propose settlement / request adjustment, not on own campaign (Phase 1) | ✔ | – | – | – |
| Approve or reject settlement / adjustment: a different admin from the proposer, not the campaign creator (Phase 1) | ✔ | – | – | – |
| Cancel own settlement proposal / adjustment request (Phase 1) | ✔ | – | – | – |
| Read own audit activity | ✔ | ✔ | ✔ | – |

Nobody — including ADMIN — can edit or delete ledger entries, audit logs or status history through the API; DB triggers also block it.

## Phase 2 — AI monitoring (advisory)
| Action | ADMIN | CAMPAIGN_CREATOR | DONOR | Anonymous |
|---|---|---|---|---|
| List / read monitoring findings and their history | ✔ | – | – | – |
| Review a finding | ✔ (not the campaign creator, not an actor of a source event) | – | – | – |
| Read outbox statistics and rows | ✔ | – | – | – |
| Requeue a FAILED outbox event | ✔ | – | – | – |

The monitoring service authenticates to nobody in the backend: it has no backend credentials. The backend authenticates to the monitoring service with a shared service key (`rise.monitoring.service-key`).
