# Phase 0 Acceptance Criteria

> **Note (2026-10-25):** package paths in this document refer to the capability-based layout used before the layered refactor (ADR-008). For current locations see `architecture.md` › Package layout.

"Code status" reflects what exists in the repository. Each row was re-checked on 2026-10-30 against the source and the full backend run (`mvn -B clean test`, JDK 21, Testcontainers MySQL 8.4): **177 run, 0 failures, 0 errors, 0 skipped**, plus a local Docker Compose run (MySQL 8.4, prod profile, seed on) in which Flyway applied V1–V11 and the demo seed completed. A tick means the Evidence column names a test or run that exercised the criterion; it is not inferred from the test count.

| # | Criterion | Verified | Evidence (2026-10-30) |
|---|---|---|---|
| 1 | Complete Spring Boot project | ☑ | `mvn -B clean test` BUILD SUCCESS; Compose image builds and the service starts and reports `/actuator/health/readiness` 200 |
| 2 | MySQL config in application.yml | ☑ | Datasource block in `application.yml`; all integration tests run against MySQL 8.4 (Testcontainers) and the Compose run against MySQL 8.4 |
| 3 | Flyway migrations | ☑ | Compose run: "Successfully validated 11 migrations", schema at version 11; `ddl-auto: validate` passes on every test context |
| 4 | Repeatable demo data | ☑ | `DemoDataSeederIntegrationTest` (3); Compose seed run on an empty database. Seeding requires `rise.simulation.donation-confirmation-enabled=true` because the seeder confirms donations through the simulation path (an intentional dependency, not a configuration defect); the `prod` profile now fails at startup with an explicit message when seeding is on and simulation is off |
| 5 | Demo accounts for all roles | ☑ | `DemoDataSeederIntegrationTest`; 8 users in the seeded database (restore check) |
| 6 | Authentication + resource authorization | ☑ | `AuthSecurityIntegrationTest` (15), `OrganizationApiIntegrationTest`, `findingEndpointsAreAdminOnly`, `AlertAdminIntegrationTest.alertEndpointsAreAdminOnlyAndFilterByStatus` |
| 7 | Campaign creation, review, publishing | ☑ | `CampaignLifecycleIntegrationTest` (11), `CampaignCancellationIntegrationTest` (3) |
| 8 | Simulated donation creation and confirmation | ☑ | `EscrowLifecycleIntegrationTest` (14) |
| 9 | Consistent escrow balances and ledger | ☑ | `EscrowLifecycleIntegrationTest`, `LedgerInvariantPropertiesTest`, `ReconciliationJobIntegrationTest` (3) |
| 10 | Milestone and evidence management | ☑ | `DomainEventOutboxIntegrationTest.milestoneLifecycleProducesOneMinimizedEventPerCommittedAction`, `EscrowLifecycleIntegrationTest` |
| 11 | Disbursement request and approval | ☑ | `EscrowLifecycleIntegrationTest`, `RequestIdempotencyIntegrationTest` (4) |
| 12 | No over-disbursement / duplicate release | ☑ | `ConcurrencyIntegrationTest` (6), `RequestIdempotencyIntegrationTest`, `SimulatedEscrowEngineTest` (10); database CHECK constraints in V1 |
| 13 | Dispute and simulated refund workflows | ☑ | `DisputeAndRefundIntegrationTest` (9), `RefundReservationIntegrationTest` (3) |
| 14 | Audit logging | ☑ | Audit rows asserted in `AlertAdminIntegrationTest`, `OutboxRelayIntegrationTest` (requeue, review) and the lifecycle tests; append-only guards in V2 |
| 15 | Validation and consistent errors | ☑ (breadth thin) | `OrganizationApiIntegrationTest` asserts `VALIDATION_FAILED`; `HardeningIntegrationTest` asserts `RATE_LIMITED` / `REQUEST_TOO_LARGE` use the standard error body. Per-field validation tests outside organizations are sparse |
| 16 | OpenAPI | ☑ | `OpenApiContractTest` compares live `/v3/api-docs` with `docs/api/openapi-v1.json` (regenerated 2026-10-30: 240 additions, 0 removals) |
| 17 | Unit and integration test files | ☑ | 177 tests ran and passed |
| 18 | docs/ directory | ☑ | Present; synchronised 2026-10-30 |
| 19 | Local setup instructions | ☑ | Root `README.md`; the Compose path was executed on 2026-10-30 with the documented variables |
| 20 | No AI/Avalanche integration | ☑ (superseded in part) | No blockchain code exists. The original "no AI" wording predates Phase 2: the backend now relays minimised events to a separate monitoring service and stores advisory findings (ADR-004, ADR-007). The backend contains no AI model code |

## Known limitations
- Hibernate `ddl-auto` is now `validate` (Phase 1 objective 1).
- Settlement of the remaining balance is implemented (Phase 1 objective 3).
- Refund approval now reserves funds (Phase 1 objective 4).
- Access tokens are not revocable before expiry (status checked per request).
- Evidence files are references only; no upload/storage.
