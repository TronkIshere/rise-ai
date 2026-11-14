#!/usr/bin/env bash
# Exports the ABIs (committed) and the creation bytecode (test fixtures for the gateway's integration tests) from the compiled
# contracts, using the pinned Foundry image. Also exports CampaignEscrow's method selectors, which the backend uses to name the
# transactions it reads from the Avalanche Data API. Re-run after any contract change.
set -euo pipefail
cd "$(dirname "$0")/.."
FIXTURES="../src/test/resources/contracts"
BACKEND_CHAIN="../../rise-ai-backend/src/main/resources/chain"
mkdir -p abi "$FIXTURES" "$BACKEND_CHAIN"
./scripts/forge.sh 'forge build >/dev/null 2>&1 && for c in CampaignEscrow RiseVND RiseAnchor; do forge inspect $c abi --json > abi/$c.abi.json && forge inspect $c bytecode > abi/$c.bin; done && forge inspect CampaignEscrow methodIdentifiers --json > abi/CampaignEscrow.methods.json'
for c in CampaignEscrow RiseVND RiseAnchor; do cp "abi/$c.bin" "$FIXTURES/$c.bin"; done
cp abi/CampaignEscrow.methods.json "$BACKEND_CHAIN/campaign-escrow-methods.json"
echo "exported: $(wc -c < abi/CampaignEscrow.bin) chars escrow bytecode"
