#!/bin/sh
# Read-only check of the Fuji deployment in deployments/AVALANCHE_FUJI.json. No key, no transaction: eth_chainId,
# eth_getCode and eth_call only. Needs `cast`; with Docker run it from contracts/:  bash scripts/forge.sh "sh scripts/verify-fuji.sh"
# Exit code 0 only when every check passes.
set -eu
RPC="${FUJI_RPC:-https://api.avax-test.network/ext/bc/C/rpc}"
M="${MANIFEST:-deployments/AVALANCHE_FUJI.json}"
field() { sed -n "s/.*\"$1\": *\"\{0,1\}\([^\",]*\)\"\{0,1\}.*/\1/p" "$M" | head -n 1; }
ESCROW=$(field escrow); TOKEN=$(field token); ANCHOR=$(field anchor); ADMIN=$(field admin)
OPERATOR=$(field operator); APPROVER=$(field approver); ANCHORER=$(field anchorer); GUARDIAN=$(field guardian)
DELAY=$(field minReleaseDelay)
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1 (got $2, want $3)"; fail=1; fi; }
lower() { echo "$1" | tr 'A-F' 'a-f'; }
call() { cast call --rpc-url "$RPC" "$@" | sed 's/ .*//'; }

check "chain id is Fuji" "$(cast chain-id --rpc-url "$RPC")" 43113
for c in "$ESCROW" "$TOKEN" "$ANCHOR"; do
  [ "$(cast code --rpc-url "$RPC" "$c" | wc -c)" -gt 10 ] && echo "PASS  bytecode at $c" || { echo "FAIL  no bytecode at $c"; fail=1; }
done
check "escrow pins the manifest token" "$(lower "$(call "$ESCROW" 'token()(address)')")" "$(lower "$TOKEN")"
check "minReleaseDelay" "$(call "$ESCROW" 'minReleaseDelay()(uint64)')" "$DELAY"
check "token is 2-decimal rVND" "$(call "$TOKEN" 'decimals()(uint8)')" 2
OP_ROLE=$(call "$ESCROW" 'OPERATOR_ROLE()(bytes32)'); AP_ROLE=$(call "$ESCROW" 'APPROVER_ROLE()(bytes32)')
G_ROLE=$(call "$ESCROW" 'GUARDIAN_ROLE()(bytes32)'); AN_ROLE=$(call "$ANCHOR" 'ANCHORER_ROLE()(bytes32)')
check "operator holds OPERATOR_ROLE" "$(call "$ESCROW" 'hasRole(bytes32,address)(bool)' "$OP_ROLE" "$OPERATOR")" true
check "approver holds APPROVER_ROLE" "$(call "$ESCROW" 'hasRole(bytes32,address)(bool)' "$AP_ROLE" "$APPROVER")" true
check "guardian holds GUARDIAN_ROLE" "$(call "$ESCROW" 'hasRole(bytes32,address)(bool)' "$G_ROLE" "$GUARDIAN")" true
check "platform admin does NOT hold GUARDIAN_ROLE" "$(call "$ESCROW" 'hasRole(bytes32,address)(bool)' "$G_ROLE" "$ADMIN")" false
check "GUARDIAN_ROLE is its own admin" "$(call "$ESCROW" 'getRoleAdmin(bytes32)(bytes32)' "$G_ROLE")" "$G_ROLE"
check "anchorer holds ANCHORER_ROLE" "$(call "$ANCHOR" 'hasRole(bytes32,address)(bool)' "$AN_ROLE" "$ANCHORER")" true
check "totalHeld equals the escrow's rVND balance" "$(call "$ESCROW" 'totalHeld()(uint256)')" "$(call "$TOKEN" 'balanceOf(address)(uint256)' "$ESCROW")"
echo "info  paused=$(call "$ESCROW" 'paused()(bool)') totalHeld(base units, 0.01 VND)=$(call "$ESCROW" 'totalHeld()(uint256)')"
for k in KIND_LEDGER KIND_AUDIT KIND_FINDINGS; do
  echo "info  anchors $k=$(call "$ANCHOR" 'anchorCount(bytes32)(uint256)' "$(call "$ANCHOR" "$k()(bytes32)")")"
done
echo "info  explorer https://testnet.snowtrace.io/address/$ESCROW"
[ "$fail" -eq 0 ] && echo "ALL CHECKS PASSED" || { echo "SOME CHECKS FAILED"; exit 1; }
