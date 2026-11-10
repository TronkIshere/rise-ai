#!/usr/bin/env bash
# Exports the ABIs (committed) and the creation bytecode (test fixtures for the gateway's integration tests) from the compiled
# contracts, using the pinned Foundry image. Re-run after any contract change.
set -euo pipefail
cd "$(dirname "$0")/.."
FIXTURES="../src/test/resources/contracts"
mkdir -p abi "$FIXTURES"
./scripts/forge.sh 'forge build >/dev/null 2>&1 && for c in CampaignEscrow RiseVND RiseAnchor; do forge inspect $c abi --json > abi/$c.abi.json && forge inspect $c bytecode > abi/$c.bin; done'
for c in CampaignEscrow RiseVND RiseAnchor; do cp "abi/$c.bin" "$FIXTURES/$c.bin"; done
echo "exported: $(wc -c < abi/CampaignEscrow.bin) chars escrow bytecode"
