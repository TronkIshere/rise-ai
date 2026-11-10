#!/usr/bin/env bash
# Starts the gateway on AVALANCHE_FUJI with the owner's keystores (ADR-009). The owner runs this themselves:
# keystores and password files are mounted read-only into the container and never leave this machine.
# Defaults: keystores in ~/.foundry/keystores, password files and the internal API key in ~/rise-secrets,
# MySQL database avalanche_gateway on 127.0.0.1:3320. Stop with: docker rm -f rise-fuji-gateway
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORES="${KEYSTORES:-$HOME/.foundry/keystores}"
SECRETS="${SECRETS:-$HOME/rise-secrets}"
DB_URL="${DB_URL:-jdbc:mysql://host.docker.internal:3320/avalanche_gateway}"
OPERATOR=0x5F90Fbe1b75C99000d504e2Cb44D7dacE4ae8444
APPROVER=0xdFae3BB55D4aEF68b645964806Ab1dCc6366553F

for f in "$KEYSTORES/rise-operator" "$KEYSTORES/rise-approver" "$SECRETS/operator.txt" "$SECRETS/approver.txt" \
         "$SECRETS/gateway-api-key.txt" "$HERE/contracts/deployments/AVALANCHE_FUJI.json" "$HERE/target/rise-ai-avalanche.jar"; do
  [ -f "$f" ] || { echo "missing: $f"; exit 1; }
done
win() { cygpath -w "$1" 2>/dev/null || echo "$1"; }

docker rm -f rise-fuji-gateway >/dev/null 2>&1 || true
MSYS_NO_PATHCONV=1 docker run -d --name rise-fuji-gateway --memory=1g -p 127.0.0.1:8082:8082 \
  -v "$(win "$HERE/target"):/app:ro" -v "$(win "$KEYSTORES"):/keystores:ro" -v "$(win "$SECRETS"):/secrets:ro" \
  -v "$(win "$HERE/contracts/deployments"):/deployments:ro" \
  -e AVALANCHE_ENVIRONMENT=AVALANCHE_FUJI -e AVALANCHE_RPC_PRIMARY_URL=https://api.avax-test.network/ext/bc/C/rpc \
  -e AVALANCHE_TX_ENABLED=true -e AVALANCHE_TX_RECONCILE_INTERVAL_MS=2000 \
  -e AVALANCHE_SIGNERS_ENABLED=true -e AVALANCHE_SIGNERS_MODE=keystore \
  -e AVALANCHE_OPERATOR_KEYSTORE=/keystores/rise-operator -e AVALANCHE_OPERATOR_PASSWORD_FILE=/secrets/operator.txt -e AVALANCHE_OPERATOR_ADDRESS=$OPERATOR \
  -e AVALANCHE_APPROVER_KEYSTORE=/keystores/rise-approver -e AVALANCHE_APPROVER_PASSWORD_FILE=/secrets/approver.txt -e AVALANCHE_APPROVER_ADDRESS=$APPROVER \
  -e AVALANCHE_DEPLOYMENT_MANIFEST=/deployments/AVALANCHE_FUJI.json \
  -e AVALANCHE_DB_URL="$DB_URL" -e AVALANCHE_DB_USERNAME=avalanche_gw -e AVALANCHE_DB_PASSWORD=avalanche_gw_local \
  -e AVALANCHE_INTERNAL_API_KEY="$(cat "$SECRETS/gateway-api-key.txt")" -e JAVA_TOOL_OPTIONS=-Xmx512m \
  maven:3.9-eclipse-temurin-21 java -jar /app/rise-ai-avalanche.jar >/dev/null

echo "Starting the gateway (decrypting keystores takes a few seconds)..."
for i in $(seq 1 90); do
  if docker logs rise-fuji-gateway 2>&1 | grep -qE 'Started AvalancheGatewayApplication|APPLICATION FAILED|EnvironmentGuardException'; then break; fi
  sleep 2
done
docker logs rise-fuji-gateway 2>&1 | grep -E 'Avalanche environment validated|Keystore signers loaded|Started AvalancheGatewayApplication|EnvironmentGuardException|APPLICATION FAILED|code=' | head -n 8
docker ps --filter name=rise-fuji-gateway --format 'gateway container: {{.Status}}'
