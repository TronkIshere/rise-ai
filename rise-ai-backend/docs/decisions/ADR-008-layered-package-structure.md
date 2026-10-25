# ADR-008 — Layered Package Structure

## Context
ADR-001 chose a modular monolith with packages per business capability (`campaign/`, `donation/`, …), each holding its controller, service, repository, entities, enums and a `*Dtos` holder of nested request/response records. The project owner asked for a consistent structure organized by technical responsibility, matching their other Spring Boot projects: `controller`, `service` + `service/impl`, `repository`, `entity`, `dto/request`, `dto/response`, `configuration`, `exception`, `enums`, `mapper`, `util`.

## Decision
Supersedes only the packaging part of ADR-001; the modular monolith, single schema and module boundaries stay.
1. Top-level packages under `com.riseai` follow the technical layers listed above (see `architecture.md` › Package layout).
2. Application services are split into an interface in `service/` and an implementation `<Name>Impl` in `service/impl/`. Callers, including other services and tests, depend on the interface. `EscrowEngine`, `DomainEventPublisher` and `OutboxRelay` follow the same pattern. Infrastructure classes (`JwtService`, `MonitoringClient`, schedulers) are not given interfaces.
3. Nested DTO records are flattened into one file each, keeping their simple names, so the OpenAPI schema names and the frozen contract (`docs/api/openapi-v1.json`) stay identical. Requests go to `dto/request`, responses and projections to `dto/response`.
4. Deliberate exceptions: `security/` (cross-cutting authentication infrastructure), `client/` (outbound HTTP), `dto/event/` (internal event model).
5. Test classes: scenario integration tests live in `com.riseai.integration`; unit tests sit in the package of the class they test; `LayeredArchitectureTest` (`com.riseai.architecture`) enforces the layer rules.

## Alternatives
- Keep capability packages: rejected by the owner's convention.
- Hybrid (`campaign/controller`, `campaign/service`, …): still entity-centric at the top level, which is what the owner asked to avoid.
- Introduce a mapping library (MapStruct): rejected. Existing `from(entity)` factories on response records are sufficient, and no mapping dependency is configured.

## Rationale
One predictable place per kind of class, controllers decoupled from implementations, and enforceable dependency rules. Capabilities remain discoverable by class-name prefix.

## Consequences
- No change to endpoints, request/response schemas, entities, repository queries, migrations, transactions or security rules. Verified by the full test suite, `OpenApiContractTest` and `LayeredArchitectureTest`.
- Package-private access between classes of the same capability is no longer possible. One factory (`FundingSummary.from`) became public, and four constants moved from the former DTO holders onto their response records (`DonationResponse.ANONYMOUS_NAME`, `OrganizationResponse.VERIFICATION_DISCLAIMER`, `SettlementResponse.SETTLEMENT_DISCLAIMER`, `EscrowSummary.SIMULATION_DISCLAIMER`).
- Static helpers on services moved to interfaces (`AuditService.meta`), because static interface methods are not inherited; implementations call them qualified.
- Historical documents (Phase 0/1 evidence, earlier ADRs) keep their original package paths.
