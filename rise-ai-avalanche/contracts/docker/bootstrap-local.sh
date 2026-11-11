#!/usr/bin/env bash
# Root docker-compose.yml only, LOCAL_SIMULATION on Anvil: the gateway's dev signers are fresh random keys on every start,
# so after it is up this grants them their contract roles from Anvil's UNLOCKED admin account and gives them test gas.
# No private key is read, passed or stored. Re-run (docker compose up) after the gateway restarts.
set -euo pipefail
RPC="${RPC_URL:-http://anvil:8545}"
ADMIN=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
MANIFEST=deployments/LOCAL_SIMULATION.json
LOG="${GATEWAY_LOG:-/shared/gateway.log}"
[ "$(cast chain-id --rpc-url "$RPC")" = 31337 ] || { echo "refusing: $RPC is not chain 31337"; exit 1; }

# shortcut: the gateway exposes only operator and approver in /config, so all addresses are read from its startup log line;
# upgrade when the gateway exposes the anchorer address.
line=""
for _ in $(seq 1 60); do
  line=$(grep 'Ephemeral development signers created' "$LOG" 2>/dev/null | tail -1 || true)
  [ -n "$line" ] && break
  sleep 2
done
[ -n "$line" ] || { echo "no dev signer line in $LOG"; exit 1; }
signer() { echo "$line" | grep -o "$1=0x[0-9a-fA-F]\{40\}" | cut -d= -f2; }
contract() { grep -o "\"$1\": *\"0x[0-9a-fA-F]\{40\}\"" "$MANIFEST" | grep -o '0x[0-9a-fA-F]\{40\}'; }

escrow=$(contract escrow)
anchor=$(contract anchor)
grant() { # contract role account
  cast send --rpc-url "$RPC" --unlocked --from "$ADMIN" "$1" 'grantRole(bytes32,address)' "$(cast keccak "$2")" "$3" >/dev/null
  echo "granted $2 on $1 to $3"
}
# RELAYER sends the permissionless executeRelease, so it needs gas but no role.
for role in OPERATOR APPROVER ANCHORER RELAYER; do
  account=$(signer "$role")
  [ -n "$account" ] || { echo "no $role address in the gateway log"; exit 1; }
  cast rpc --rpc-url "$RPC" anvil_setBalance "$account" 0x56BC75E2D63100000 >/dev/null # 100 test AVAX
  case "$role" in
    OPERATOR) grant "$escrow" OPERATOR_ROLE "$account" ;;
    APPROVER) grant "$escrow" APPROVER_ROLE "$account" ;;
    ANCHORER) grant "$anchor" ANCHORER_ROLE "$account" ;;
    RELAYER) echo "funded RELAYER $account" ;;
  esac
done
