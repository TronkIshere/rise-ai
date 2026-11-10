# Deployment manifests

`Deploy.s.sol` writes `deployments/<ENVIRONMENT>.json` (ignored by git). It holds addresses only. The gateway (`avalanche.deployment-manifest`) refuses a manifest whose environment or chain id differs from the live endpoint.

## LOCAL_SIMULATION (Anvil)

    OPERATOR=<addr> APPROVER=<addr> MIN_RELEASE_DELAY=60 scripts/deploy-local.sh http://host.docker.internal:8545

## AVALANCHE_FUJI (testnet)

A human runs the same script with a testnet-only wallet they hold themselves, chosen on the forge command line:

    DEPLOY_ENVIRONMENT=AVALANCHE_FUJI OPERATOR=<addr> APPROVER=<addr> MIN_RELEASE_DELAY=86400 \
      forge script script/Deploy.s.sol --rpc-url <fuji https url from the gateway allow-list> --keystore <path> --broadcast

Never pass a private key on the command line or commit one. The wallet becomes admin and simulation treasury. For a demo with a short objection window, set `MIN_RELEASE_DELAY` lower.

Verify on the explorer with `forge verify-contract --chain 43113`. Compiler settings must match `foundry.toml` (solc 0.8.38, optimizer 200, `via_ir`, `evm_version = "cancun"`).
