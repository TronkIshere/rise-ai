# FE-06 — Frontend deployment and rollback runbook

## Release architecture
React/Vite `dist/` is served by Nginx. The browser calls only **same-origin** `/api/v1/**`; Nginx proxies to the trusted Spring Boot Backend origin configured at container startup via `BACKEND_UPSTREAM`. The separate Monitoring service must never be called from the browser. TLS for a public site must terminate at a verified HTTPS ingress, not this localhost-only Compose file.

**No production URL, hosted Backend or provisioned frontend deployment is claimed here.** This runbook supports reproducible local/staging release validation, and is not authorization to publish a public site.

## Windows local isolated staging

Prerequisites: Docker Desktop working, the Backend already running independently at a reachable port (e.g. 18082) with its own isolated MySQL; `rise-ai-frontend/` checkout on the tested PR SHA. Don't run full root Compose against existing FreelaX/other service ports.

In PowerShell from repository root:

```powershell
git status --short
git rev-parse HEAD

$env:RISE_FRONTEND_BACKEND_UPSTREAM = "http://host.docker.internal:18082"
$env:RISE_FRONTEND_HTTP_PORT = "5174"
docker compose -f rise-ai-frontend/compose.frontend.yml config
docker compose -f rise-ai-frontend/compose.frontend.yml up -d --build
docker compose -f rise-ai-frontend/compose.frontend.yml ps

Invoke-WebRequest http://127.0.0.1:5174/healthz

cd rise-ai-frontend
$env:RISE_FE06_BASE_URL = "http://127.0.0.1:5174"
$env:RISE_FE06_TARGET = "docker"
npm run deploy:smoke
```

**Required Backend CORS setup BEFORE starting Spring Boot for this staging origin:** The unchanged Backend application sets `rise.security.cors-allowed-origins` to `http://localhost:3000,http://localhost:5173` by default. The container UI is served at **`http://127.0.0.1:5174`**, so the Backend must explicitly allow that origin for browser POSTs. With Spring Boot relaxed environment binding, set the following on the Backend process (not in the frontend client):

```powershell
$env:RISE_SECURITY_CORS_ALLOWED_ORIGINS = "http://localhost:3000,http://localhost:5173,http://127.0.0.1:5174"
# Start the unchanged Backend here with its existing DB/JWT and SERVER_PORT configuration.
```

Keep the list exact, never use `*`. If the configured port/origin differs, update this allowlist accordingly and restart Backend. `npm run deploy:smoke` sends a **read-only OPTIONS preflight** with that Origin and checks `Access-Control-Allow-Origin`; a simple anonymous GET smoke alone cannot validate authenticated browser mutations. This preflight is a configuration check, not a replacement for real login and review E2E.

The example Backend port is **illustrative**, not assumed available. Verify Backend health/port first and adjust the upstream accordingly. Docker Desktop uses `host.docker.internal` to reach a locally running Backend; Linux uses the configured `host-gateway` extra host. A healthy Nginx `/healthz` alone does **not** prove Backend connectivity. `deploy:smoke` must verify both SPA and API.

The Compose file publishes only `127.0.0.1:5174`; it does not provision Backend, MySQL, Monitoring or TLS. A standalone frontend without a reachable Backend is an **incomplete** deployment.

## Build and artifact gate

```powershell
cd rise-ai-frontend
npm ci
npm run verify
npm run release:check
```

`verify` includes release checking after build. `VITE_API_BASE_URL` is set to the **empty** value in the Docker build on purpose: browser requests use the Nginx same-origin proxy. Never bake localhost URLs, tokens, database passwords, API keys or Admin credentials into the client bundle. `BACKEND_UPSTREAM` is configured **at container runtime** by a trusted operator, not exposed to browser JavaScript.

## Remote production prerequisites (NOT automatically authorized)
- Confirm hosting target and who controls it; register a real HTTPS domain and ingress. Configure access control or private staging during acceptance.
- Deploy a reachable trusted Backend independently, with durable database, migrations, secrets, restricted network rules and own monitoring; Frontend does not manage any of those resources.
- Configure TLS and ensure reverse proxy forwards `/api/v1` to Backend with Authorization intact. Preserve Backend 401/403. Do not expose internal Monitor or test accounts.
- Verify all smoke and real-browser E2E checks on the deployed commit. Review CSP, CORS, cache policy, basic rate limiting, request size, headers and rollback.
- Obtain explicit owner approval **before public production publishing**. If target/backend URL is missing, record **BLOCKED — external deployment**, not PASS.

## Diagnostics
- `docker compose -f rise-ai-frontend/compose.frontend.yml logs --tail=150` shows only FE container logs.
- `docker compose -f rise-ai-frontend/compose.frontend.yml ps` checks state; `docker ps -a` shows unrelated services that must not be touched.
- A 502 on `/api/**` indicates Backend unreachable or a bad upstream; Nginx health may still be 200. A 401/403 on ADMIN endpoints **without login** is expected.
- A 404 on deep-linked SPA routes means fallback routing is misconfigured; check `try_files ... /index.html`.
- Before retrying a build or deployment, capture exact Git SHA, configuration name (not secrets), console and API status logs.

## Rollback and cleanup
- For staging-only deployment, save the last verified Docker image under a uniquely versioned tag **before** rebuilding. Roll back by restoring that locally held tag (or re-building the previously accepted Git SHA) and re-run read-only smoke; do not treat rollback as rolling back the Backend DB.
- Stop only this frontend Compose project from repository root:
```powershell
docker compose -f rise-ai-frontend/compose.frontend.yml down
```
- **Never run `down -v`, `docker system prune`, `docker volume prune` or delete unrelated containers.** This Compose declares no database volume; FreelaX/shared MySQL/Redis services must be preserved.
- No automatic production deploy/rollback GitHub workflow is provided until a deployment platform, target and protected-environment review process are approved.
