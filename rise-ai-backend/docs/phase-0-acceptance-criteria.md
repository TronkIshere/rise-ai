# Phase 0 Acceptance Criteria

> **Note (2026-10-25):** package paths in this document refer to the capability-based layout used before the layered refactor (ADR-008). For current locations see `architecture.md` › Package layout.

"Code status" reflects what exists in the repository. **No item has been verified by compiling, testing or running** — the generating agent was instructed not to execute builds. Mark "Verified" locally.

| # | Criterion | Code status | Where | Verified |
|---|---|---|---|---|
| 1 | Complete Spring Boot project | IMPLEMENTED | `pom.xml`, `src/main/java` | ☐ |
| 2 | MySQL config in application.yml | IMPLEMENTED | `src/main/resources/application.yml` | ☐ |
| 3 | Flyway migrations | IMPLEMENTED | `db/migration/V1`, `V2` | ☐ |
| 4 | Repeatable demo data | IMPLEMENTED | `config/DemoDataSeeder` | ☐ |
| 5 | Demo accounts for all roles | IMPLEMENTED | `demo-credentials.md` | ☐ |
| 6 | Authentication + resource authorization | IMPLEMENTED | `security`, services | ☐ |
| 7 | Campaign creation, review, publishing | IMPLEMENTED | `campaign` | ☐ |
| 8 | Simulated donation creation and confirmation | IMPLEMENTED | `donation` | ☐ |
| 9 | Consistent escrow balances and ledger | IMPLEMENTED | `escrow`, reconciliation endpoints | ☐ |
| 10 | Milestone and evidence management | IMPLEMENTED | `milestone`, `evidence` | ☐ |
| 11 | Disbursement request and approval | IMPLEMENTED | `disbursement` | ☐ |
| 12 | No over-disbursement / duplicate release | IMPLEMENTED | locks, idempotency keys, CHECKs | ☐ |
| 13 | Dispute and simulated refund workflows | IMPLEMENTED | `dispute`, `refund` | ☐ |
| 14 | Audit logging | IMPLEMENTED | `audit` | ☐ |
| 15 | Validation and consistent errors | IMPLEMENTED | DTOs, `GlobalExceptionHandler` | ☐ |
| 16 | OpenAPI | IMPLEMENTED | springdoc, `/swagger-ui.html` | ☐ |
| 17 | Unit and integration test files | IMPLEMENTED (not run) | `src/test` | ☐ |
| 18 | docs/ directory | IMPLEMENTED | `docs/` | ☐ |
| 19 | Local setup instructions | IMPLEMENTED | root `README.md` | ☐ |
| 20 | No AI/Avalanche integration | IMPLEMENTED (absent by design) | — | ☐ |

## Known limitations
- Hibernate `ddl-auto: none` (not `validate`): entity/column mismatches surface at query time. Switch to `validate` in Phase 1 after a successful local run.
- Remaining balance after campaign completion has no settlement policy (PLANNED Phase 1).
- Refund approval does not pre-reserve funds; recording re-checks the balance and can fail if funds were reserved meanwhile.
- Access tokens are not revocable before expiry (status checked per request).
- Evidence files are references only; no upload/storage.
