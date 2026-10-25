# Development Guide — Where Code Goes

Layer responsibilities and dependency rules are defined in `architecture.md`; this guide is the practical checklist. Base package: `com.riseai` (`src/main/java/com/riseai/`).

## Adding a feature (example: a "Beneficiary" resource)
| What | Where | Conventions |
|---|---|---|
| Enum (status, type) | `enums/BeneficiaryStatus.java` | Shared sets as `public static final Set<…>` on the enum (see `DonationStatus.COUNTED`) |
| Entity | `entity/Beneficiary.java` | Extend `BaseEntity` (id, created/updated) or `CreatedEntity` (immutable rows); Lombok `@Getter @Setter @NoArgsConstructor`; `@Version` on mutable rows; `@Enumerated(STRING)`; `@JdbcTypeCode(SqlTypes.CHAR)` for `CHAR` columns (`ddl-auto: validate`) |
| Migration | `src/main/resources/db/migration/V10__beneficiaries.sql` | Next free number; never edit an applied migration |
| Repository | `repository/BeneficiaryRepository.java` | `JpaRepository<Beneficiary, UUID>`; locking reads as `findByIdForUpdate` with `@Lock(PESSIMISTIC_WRITE)`; projections into `dto/response` records |
| Request DTO | `dto/request/CreateBeneficiaryRequest.java` | `record`, Bean Validation annotations; name ends with `Request` |
| Response DTO | `dto/response/BeneficiaryResponse.java` | `record` with `public static BeneficiaryResponse from(Beneficiary b)`; never return entities |
| Service interface | `service/BeneficiaryService.java` | Business-level operations taking `AuthUser` and DTOs; no Spring annotations |
| Service implementation | `service/impl/BeneficiaryServiceImpl.java` | `@Service @RequiredArgsConstructor`, `implements BeneficiaryService`, `@Override` on interface methods; `@Transactional` / `@Transactional(readOnly = true)`; inject other services **by interface**; ownership and state checks (`States.require`, `AuthUser.require(...).requireRole(...)`); call `AuditService.transition/record` for every state change |
| Controller | `controller/BeneficiaryController.java` | `@RestController @RequestMapping("/api/v1/...")`; `@PreAuthorize` for role gates; `@AuthenticationPrincipal AuthUser`; `@Valid @RequestBody`; return `ResponseAPI.ok/created(...)` or `PageResponse.of(page)`; inject the service interface only |
| Error code | `exception/ErrorCode.java` | Add a constant with HTTP status; throw `ApplicationException` |
| Configuration property | `configuration/RiseProperties.java` (or a dedicated `@ConfigurationProperties` record such as `MonitoringRelayProperties`) | Default in `application.yml`; secrets only via environment variables |
| Domain event (if monitored) | factory in `mapper/RiseEvents.java`, call `domainEvents.publish(...)` in the implementation next to the audit call | Payload allowlist only: no free text or personal data. Update `event-contracts.md` |
| Generic helper | `util/` | Stateless and business-agnostic only |

## Tests
| Kind | Location | Base |
|---|---|---|
| Scenario / API integration test | `src/test/java/com/riseai/integration/` | Extend `support/AbstractMySqlIntegrationTest` (Testcontainers MySQL); use `@AutoConfigureMockMvc` for HTTP-level checks |
| Unit test of one class | Same package as the class (for example `service/impl`, `util`, `security`) | Plain JUnit 5 / AssertJ / Mockito; jqwik for properties |
| Architecture rules | `architecture/LayeredArchitectureTest` | Extend it when you add a layer rule |

## Checklist before a pull request
1. `mvn clean test` with `JAVA_HOME` pointing to JDK 21 (Docker running).
2. If an endpoint or DTO changed: `mvn test -Dtest=OpenApiContractTest -Dopenapi.snapshot.update=true`, review the diff of `docs/api/openapi-v1.json`, update `api-specification.md`.
3. Update `business-rules.md`, `state-machines.md` and `roles-and-permissions.md` when behaviour changes.
