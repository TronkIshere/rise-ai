# Docker E2E harness

Browser and API end-to-end checks against the root Docker Compose stack, with the escrow engine `simulated`. Every run creates its own organization, campaign, donations, refund, disbursement and dispute (names end with a run timestamp), so it can run repeatedly on the same database. It never writes SQL; `db-verify.py` only runs `SELECT`.

| File | What it does |
|---|---|
| `e2e.mjs` | 61 checks: smoke, public UI, auth and RBAC (401/403, IDOR, role injection), creator → admin → donor → admin flows through the real React UI and API, negative cases, reconciliation, monitoring finding review, audit. Playwright Chromium 1440×900 |
| `supplement.mjs` | 3 API checks: `accountType=ADMIN` refused, injected roles ignored, refund above the donation refused (`REFUND_EXCEEDS_REFUNDABLE`) |
| `db-verify.py` | 14 read-only MySQL checks of the entities created by `e2e.mjs`, including the escrow balance identity |

Last result: 2026-11-21, commit `9dde8e1` + this harness, Windows 11, Docker 29.3: `e2e.mjs` 61/61, `supplement.mjs` 3/3, `db-verify.py` 14/14. Details in [`docs/handoff-2026-11-21.md`](../../docs/handoff-2026-11-21.md).

## Run

1. Start the stack from the repository root with a `.env` that has `RISE_ESCROW_ENGINE=simulated`, `RISE_SEED_ENABLED=true`, `RISE_SIMULATION_ENABLED=true` and your own `RISE_DEMO_PASSWORD` (see [`docs/local-docker-stack.md`](../../docs/local-docker-stack.md)). Wait until `curl -s http://127.0.0.1:5174/api/v1/campaigns?size=1` answers `"code":200`.
2. Pick an output folder **outside the repository**, e.g. `OUT=../../../rise-e2e-out`, and put the demo password in `$OUT/secrets/demo-password.txt` (never commit it).
3. Install Playwright once and run (Git Bash on Windows; on Linux drop `MSYS_NO_PATHCONV=1` and use `$(pwd)`):

```bash
cd qa/docker-e2e
docker run --rm -v "$(pwd -W):/h" -w /h mcr.microsoft.com/playwright:v1.56.1-jammy npm i --no-audit --no-fund playwright@1.56.1
mkdir -p "$OUT"/results "$OUT"/screenshots/{public,donor,creator,admin}
MSYS_NO_PATHCONV=1 docker run --rm --ipc=host --add-host host.docker.internal:host-gateway \
  -v "$(pwd -W):/h" -v "$(cd "$OUT" && pwd -W):/out" -v "$(cd "$OUT/secrets" && pwd -W):/secrets:ro" \
  -w /h mcr.microsoft.com/playwright:v1.56.1-jammy node e2e.mjs | tee "$OUT/playwright.log"
node supplement.mjs "$OUT"          # waits and retries if the auth rate limit (20/min per IP) is hit
python db-verify.py "$OUT"          # needs the container name rise-ai-mysql
```

Output: `results/e2e-results.json` (each check with status and evidence), `results/api-results.json` (every API call: method, path, status, error code; tokens never written), `results/db-verification.json`, screenshots per role.

## Notes

- The browser must use origin `http://127.0.0.1:5174`: Nginx forwards `Host` without the port, so the backend applies its CORS allow-list. Inside the container the harness forwards its own `127.0.0.1:5174` to the host, so nothing has to be reconfigured.
- The access token lives in memory only, so the harness navigates with client-side routing after logging in; a page reload logs the user out (expected).
- After an evidence submit the creator's milestone panel may collapse; the harness re-selects the milestone, as a user would.
- Settlements and adjustments are not covered: they need two different admin accounts and the seed has one.
