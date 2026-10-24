# Security — IMPLEMENTED unless marked

## Authentication
- Stateless JWT (HS256, Nimbus JOSE). Claims: `sub` (user id), `iss`, `exp` (30 min default), `jti`, `typ=access`, informational `email`/`roles`.
- The filter verifies signature, algorithm, issuer, type and expiry, then **loads the user from MySQL** and rejects non-ACTIVE accounts. Roles come from the database, so role changes and suspensions take effect on the next request.
- Refresh tokens: 256-bit random, returned once, stored only as SHA-256 hash, 7-day TTL, rotated on every refresh. Reusing a rotated token revokes all of the user's refresh tokens. Logout revokes the presented token. Suspension revokes all tokens.
- Login uses constant-shape failures (`INVALID_CREDENTIALS`) and performs a dummy BCrypt check for unknown emails.
- Access tokens are not individually revocable before expiry (no Redis blacklist). Mitigated by the per-request user status check. PLANNED: shorter TTL or denylist in Phase 4.

## Passwords
BCrypt cost 12. Registration requires 8–72 chars with letters and digits. Passwords and hashes are never returned or logged.

## Authorization
- URL rules: `/api/v1/admin/**` ADMIN only; selected GET routes public; everything else authenticated.
- `@PreAuthorize` on controllers for role checks; services re-check roles (defense in depth, also covers the seeder path).
- Resource checks in services: ownership (`loadOwned`, `loadOwnerOrAdmin`), visibility (`loadVisible` → 404), participant checks for disputes, own-record checks for donations/refunds.
- Conflict-of-interest checks: no self-approval of campaigns, evidence, disbursements, refunds, organization verification, disputes; no self role/status change; creators cannot donate to or dispute their own campaigns.
- Dual control (Phase 1): settlements and ledger adjustments need a second ADMIN who is neither the proposer nor the campaign creator. The service enforces this and so does a DB CHECK (`decided_by <> proposed_by/requested_by`). These endpoints sit under `/api/v1/admin/**` and carry class-level `@PreAuthorize("hasRole('ADMIN')")`.
- Client-supplied `userId`, `role`, `ownerId` fields do not exist in request DTOs.

## Validation & error handling
Bean Validation on every request DTO; money limited to 17 integer + 2 fraction digits; URL patterns for images (`https://`) and evidence (`https://` or `sim://`); SHA-256 format for hashes. `GlobalExceptionHandler` returns stable error codes; root-cause and SQL messages are logged server-side only. `server.error.include-stacktrace: never`.

## Sensitive data
- Audit metadata contains ids, amounts and reasons only — never passwords, tokens or secrets.
- Public donation views mask donor identity for anonymous donations and never expose donor ids; creators see masked data.
- Own audit view omits IP addresses.

## Transport, CORS, headers
CORS allow-list from `rise.security.cors-allowed-origins`. CSRF disabled (stateless bearer API, no cookies). PLANNED (Phase 4): HTTPS termination, HSTS, security headers review, rate limiting.

## Demo limitations
- `application.yml` contains **development-only** MySQL credentials and a development JWT secret default. Override `RISE_JWT_SECRET` and DB credentials outside local development.
- Demo accounts share one password (`docs/demo-credentials.md`). Disable seeding (`rise.seed.enabled=false`) in any shared environment.
- Simulated confirmation endpoint is ADMIN-only and can be disabled with `rise.simulation.donation-confirmation-enabled=false`.
- No email verification, MFA or account lockout. PLANNED (Phase 4).
