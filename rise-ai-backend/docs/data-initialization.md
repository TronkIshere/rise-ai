# Data Initialization — IMPLEMENTED

Schema = Flyway migrations. Demo data = `config/DemoDataSeeder` (ApplicationRunner, enabled by `rise.seed.enabled=true`, default on in `application.yml`, off under the `test` profile).

## How it seeds
Demo **users** are inserted directly (roles cannot be self-assigned through the API). Everything else is created **through the real services** (organization review, campaign submission/approval/publication, donations + simulated confirmation, evidence review, disbursement review/release, disputes, refunds). Ledger entries and balances are therefore produced by the same code paths as production traffic and reconcile by construction.

## Idempotency
- Users: skipped if the email exists.
- Organizations: skipped if the slug exists.
- Campaigns: each scenario is skipped if its slug exists; each scenario runs in **one transaction**, so a failure leaves no partial campaign.
Restarting never duplicates users, campaigns or transactions. Covered by `DemoDataSeederIntegrationTest`.

## Contents (all fictional, labelled `[SIMULATED DEMO DATA]`)
Users: 1 ADMIN, 3 DONOR, 3 CAMPAIGN_CREATOR, 1 SUSPENDED donor — see `demo-credentials.md`.

Organizations: Sunrise Learning Fund, Green Village Community Group, Hope Kitchen Network (VERIFIED by simulated platform review), Mekong Clean Water Initiative (PENDING_REVIEW), Bright Steps Youth Club (UNVERIFIED). Verification notes state that this is not legal verification.

| Campaign | Target (VND) | State produced |
|---|---|---|
| Laptops and Learning Kits for Highland Pupils | 120,000,000 (3 milestones) | ACTIVE; 75M confirmed + 5M pending; M1 40M released and COMPLETED; M2 20M request UNDER_REVIEW (reserved) |
| Riverside Community Library | 80,000,000 (3) | ACTIVE; 45M confirmed; M1 15M released (PARTIALLY_DISBURSED), one evidence rejected; dispute UNDER_REVIEW holds M1 (DISPUTED) with a creator message |
| Hope Kitchen Weekly Meals | 60,000,000 (3) | ACTIVE; 24M confirmed; FAILED and CANCELLED donations; 2M refund RECORDED (approve reserves, record releases and records; Phase 1); 3M refund REQUESTED |
| Mekong Village Clean Water Filters | 150,000,000 (4) | PENDING_REVIEW (organization not yet verified) |
| First-Generation University Scholarships | 90,000,000 (2) | DRAFT |
| Green Village Community Centre Repairs | 70,000,000 (2) | PAUSED (escrow FROZEN); 30M confirmed; 15M request NEEDS_MORE_EVIDENCE (reserved) |

Images use `https://placehold.co/...`; evidence files are `sim://evidence/...` with SHA-256 of the reference string.

## Safe reset (local only)
```bash
docker compose down -v      # drops the MySQL volume
docker compose up -d
mvn spring-boot:run         # Flyway recreates schema, seeder reloads demo data
```
Without Docker: `DROP DATABASE rise_ai; CREATE DATABASE rise_ai;`. Do not `DELETE` from ledger/audit tables — triggers block it by design.
