# 07 — Operations Runbook (monitoring integration)

Scope: the backend → `rise-ai-monitoring` event path. The monitor is advisory only. It never authorizes or executes a financial action, and a monitor outage never blocks business operations; events wait in the backend outbox.

Status: every section was drilled on 2026-10-29 (sections 1, 3, 4, 5, 6 and 7 on the Compose stack; section 2 on a packaged monitor). Results and limits are in `06-progress-report.md` 12.4. Section 6 was corrected by the drill.

All admin calls below need an `ADMIN` access token (`Authorization: Bearer <token>`). Monitor calls need the service key.

## 1. Monitor is down or unreachable
Symptoms: `GET /api/v1/admin/monitoring/outbox/stats` shows `pending` growing and `oldestUndeliveredAgeSeconds` rising; backend log lines mention `ConnectException` with backoff.

1. Confirm: `curl http://<monitor>:8081/actuator/health`.
2. Restart or repair the monitor. Business operations (donations, evidence, disbursements) continue unaffected.
3. The relay retries with exponential backoff (5 s up to 15 min, 12 attempts). After recovery `pending` drains without action.
4. Check `failed` in `outbox/stats`. If it is above 0, go to section 3.

## 2. Ollama is down (only when `monitoring.ai.enabled=true`)
Rise events do not use AI by default. If AI is enabled, results carry `analysisStatus` of `UNAVAILABLE` or `INCONCLUSIVE` (per `monitoring.ai.failure-mode`) while deterministic findings are still produced and delivered. Restart Ollama; no outbox action is needed. To stop the noise, set `MONITORING_AI_ENABLED=false` and restart the monitor.

## 3. Outbox rows in `FAILED`
Cause: the monitor rejected the event permanently (4xx other than rate limit) or 12 attempts were exhausted. The backend logs an `ALERT` line.

1. List: `GET /api/v1/admin/monitoring/outbox?status=FAILED`.
2. Read the `lastError` of each row. Fix the cause (key mismatch, contract change, monitor outage).
3. Requeue one row: `POST /api/v1/admin/monitoring/outbox/{id}/requeue` with `{"reason":"..."}`. Attempts are reset and the action is audited as `OUTBOX_EVENT_REQUEUED`.
4. Re-check `outbox/stats` until `failed` is 0.

Requeue each row individually (`GET .../outbox?status=FAILED` returns the rows in `data.data[]`). Never edit outbox rows in SQL. Never delete `FAILED` rows: they are the only record of the missed analysis.

## 4. Monitor database unavailable or corrupted
The monitor keeps only a minimized projection (H2 file under `/data` in the container, or the URL in `MONITORING_DB_URL`). It holds no business data.

1. Stop the monitor.
2. Restore the file from backup, or delete it to start empty (rules lose history for duplicate-evidence and repeat-request checks; backlog events are re-evaluated idempotently).
3. Start the monitor; Flyway recreates the schema.
4. Findings already delivered stay in the backend; nothing is lost there.

## 5. Service key rotation (no downtime)
The monitor accepts the current key and an optional previous key at the same time.

1. Generate a new key. With the root Compose file the backend key can differ from the monitor key through `RISE_BACKEND_MONITORING_KEY` (defaults to `RISE_MONITORING_SERVICE_KEY`).
2. Monitor: set `MONITORING_SERVICE_KEY=<new>` and `MONITORING_PREVIOUS_SERVICE_KEY=<old>`. Restart the monitor. Both keys now work.
3. Backend: set `RISE_MONITORING_SERVICE_KEY=<new>`. Restart the backend. Check `outbox/stats`: `pending` and `failed` stay stable.
4. Monitor: clear `MONITORING_PREVIOUS_SERVICE_KEY`. Restart. The old key is now rejected (401, error code 1006).

Automated coverage: `ServiceKeyRotationAndLoggingIntegrationTest`.

## 6. Suspected key compromise
Skip the overlap: set a new `MONITORING_SERVICE_KEY` with no previous key, restart the monitor, then update the backend and restart it.

A rejected key is a permanent failure (HTTP 401, code 1006), so events created during the gap become `FAILED` rows, they do not wait in `PENDING`. After the backend uses the new key, requeue every `FAILED` row (section 3). Drilled: 2 events failed, 2 requeued, 0 failed afterwards.

## 7. CRITICAL finding raised
A `CRITICAL` finding (for example a reconciliation mismatch, rule LG-01) needs human review. It does not change state automatically.

1. `GET /api/v1/admin/monitoring/findings?severity=CRITICAL&status=OPEN`.
2. Follow its `recommendedAction` and the backend reconciliation runbook.
3. Record the outcome with `POST /api/v1/admin/monitoring/findings/{id}/review` (a note is mandatory). The reviewer must not be the person who performed the action the finding is about: the platform answers 403 `CONFLICT_OF_INTEREST`. A second ADMIN is therefore needed when one admin ran the reconciliation.

## 8. Deployment reminders
- Seeding and the simulated donation confirmation must be off outside development (`RISE_SEED_ENABLED=false`, `RISE_SIMULATION_ENABLED=false`; the root `docker-compose.yml` defaults to off).
- Secrets come from the environment: `RISE_JWT_SECRET`, `RISE_MONITORING_SERVICE_KEY`, database credentials.
- Alerts: `ALERT` log lines always. `RISE_ALERT_WEBHOOK_URL` (http or https only; redirects are not followed) adds a JSON POST on `OUTBOX_EVENT_FAILED` and `CRITICAL_FINDING`; `RISE_ALERT_WEBHOOK_TOKEN` adds a bearer token. Sending happens after commit on a separate single thread with a queue of 100, 3 s timeouts and two attempts one second apart; when the queue is full or both attempts fail the alert exists only as a log line. No alert destination is configured by default and none has been selected; the webhook has only been tested against a local stub.
