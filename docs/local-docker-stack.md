# Local Docker stack (whole app)

The root `docker-compose.yml` runs every module on one machine for **local testing only**. Everything is simulated: the chain is a local Anvil node (chain 31337), the token is test rVND, and the gateway signs with throw-away keys it generates at startup. No real key, wallet or secret is stored in any file.

## What starts

| Service | Port on your machine | Purpose |
|---|---|---|
| `mysql` | not published | MySQL 8.4: databases `rise_ai` (backend) and `avalanche_gateway` (gateway) |
| `rise-ai-monitoring` | not published | Monitoring service, AI off |
| `rise-ai-backend` | `127.0.0.1:8080` | Spring Boot backend, `prod` profile, escrow engine `avalanche` by default |
| `rise-ai-frontend` | `127.0.0.1:5174` | React build behind Nginx; `/api` is proxied to the backend |
| `anvil` | `127.0.0.1:8545` | Local chain 31337; state kept in the `rise_anvil_data` volume |
| `rise-ai-avalanche` | `127.0.0.1:8082` | Avalanche gateway, `LOCAL_SIMULATION`, dev signers, transactions on |
| `avalanche-db-init` | one-shot | Creates the `avalanche_gateway` database if missing |
| `avalanche-contracts` | one-shot | Deploys `RiseVND`, `CampaignEscrow` and `RiseAnchor` with Anvil's unlocked account, unless already deployed |
| `avalanche-bootstrap` | one-shot | Grants the gateway's dev signers their contract roles and gives them test gas |

Start order: mysql and anvil → db init and contract deploy → gateway → bootstrap → monitoring and backend → frontend.

## Run it

Prerequisites: Docker with Compose v2.17 or newer (for `additional_contexts`). The first build takes several minutes; it downloads Maven, npm and Solidity dependencies.

```bash
cp .env.example .env          # then replace every value; .env is git-ignored
docker compose -p rise-ai up -d --build
docker compose -p rise-ai ps -a   # one-shot services should show "Exited (0)"
```

Open http://127.0.0.1:5174. With `RISE_SEED_ENABLED=true` the demo accounts from `rise-ai-backend/docs/demo-credentials.md` exist, with the password you set in `RISE_DEMO_PASSWORD` (the `prod` profile refuses the development default).

Quick checks:

```bash
cd rise-ai-frontend && RISE_FE06_BASE_URL=http://127.0.0.1:5174 RISE_FE06_TARGET=docker npm run deploy:smoke
curl -s http://127.0.0.1:5174/api/v1/transparency/chain     # "onChain": true, "network": "LOCAL_SIMULATION"
```

Without the chain: set `RISE_ESCROW_ENGINE=simulated` in `.env`. The chain services still start, but the backend never calls them.

## Stop and reset

```bash
docker compose -p rise-ai down          # keep data
docker compose -p rise-ai down -v       # delete this project's volumes: MySQL, monitor, Anvil state, manifest
```

`down -v` removes only the volumes of the `rise-ai` project. Reset MySQL and Anvil **together**: the backend's chain commands refer to campaigns on that chain, so a fresh chain with an old database (or the reverse) leaves commands that cannot succeed.

## Limits of this setup

- **Gateway restart.** The dev signers are new random keys on every gateway start, so their roles are lost. The gateway therefore has `restart: "no"`. After it stops, run `docker compose -p rise-ai up -d` again, which re-runs `avalanche-bootstrap`.
- **Bootstrap reads the gateway log.** The gateway's `/config` endpoint returns the operator and approver addresses but not the anchorer, so the bootstrap reads all three from the gateway's startup log line (`/shared/gateway.log`). If that log format changes, the bootstrap fails and the backend does not start. It also funds the RELAYER signer, which sends the permissionless `executeRelease`. Without gas, that transaction is rejected with `INSUFFICIENT_FUNDS` and stays `SIGNED` until the signer is funded; the gateway then resends it after its grace period (30 s by default).
- **Chain relay speed.** Commands run strictly in order per campaign, with an RPC gap of 1 s; the demo seed queues several dozen commands, which take a few minutes to confirm after startup. A release waits for the objection window (`RISE_AVALANCHE_RELEASE_DELAY`, default `PT2M` here; the contract floor is 60 s).
- **Explorer links** in `/transparency/*` responses point to the Fuji explorer (the backend default). They do not resolve for local chain 31337.
- **Rate limiting.** The backend limits per client IP and does not trust `X-Forwarded-For`. Behind the frontend's Nginx, every browser shares one auth bucket (20 requests per minute).
- **Shared database user.** The gateway reuses the `rise_app` MySQL user on its own database (local shortcut).
- **Not a production shape.** It runs the `prod` profile with demo seeding and the simulation path enabled, plain HTTP and no secret manager. Fuji is not covered: it needs the operator-held testnet keystores (ADR-009), which never belong in this repository.
