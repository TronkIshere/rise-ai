#!/usr/bin/env bash
# Root docker-compose.yml only: deploys RiseVND, CampaignEscrow and RiseAnchor to the Compose Anvil node (chain 31337) with
# Anvil's UNLOCKED account 0, unless the manifest's escrow already has code there (Anvil keeps its state in a volume).
# OPERATOR and APPROVER are placeholder Anvil accounts; bootstrap-local.sh grants the roles to the gateway's ephemeral signers.
# GUARDIAN defaults to Anvil account 4, which plays the independent guardian locally.
set -euo pipefail
RPC="${RPC_URL:-http://anvil:8545}"
MANIFEST=deployments/LOCAL_SIMULATION.json
[ "$(cast chain-id --rpc-url "$RPC")" = 31337 ] || { echo "refusing: $RPC is not chain 31337"; exit 1; }
if [ -f "$MANIFEST" ]; then
  escrow=$(grep -o '"escrow": *"0x[0-9a-fA-F]\{40\}"' "$MANIFEST" | grep -o '0x[0-9a-fA-F]\{40\}')
  if [ "$(cast code --rpc-url "$RPC" "$escrow")" != "0x" ]; then
    echo "contracts already deployed (escrow $escrow); nothing to do"
    exit 0
  fi
fi
DEPLOY_ENVIRONMENT=LOCAL_SIMULATION \
OPERATOR=0x70997970C51812dc3A010C7d01b50e0d17dc79C8 \
APPROVER=0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC \
GUARDIAN="${GUARDIAN:-0x15d34AAf54267DB7D7c367839AAf71A00a2C6A65}" \
MIN_RELEASE_DELAY="${MIN_RELEASE_DELAY:-60}" \
  forge script script/Deploy.s.sol --rpc-url "$RPC" --unlocked --sender 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 --broadcast
cat "$MANIFEST"
