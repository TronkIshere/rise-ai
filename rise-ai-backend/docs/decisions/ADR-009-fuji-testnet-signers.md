# ADR-009 — Operator-held keystore signers on Fuji (testnet exception to ADR-005)

## Status
Accepted by the project owner on 2026-11-10, for AVALANCHE_FUJI only.

## Context
ADR-005 requires keys in KMS/HSM and a human-controlled multisig for releases. Neither exists yet. The UniHack demo needs the backend flow to run on Fuji end to end, but the gateway could only sign with ephemeral keys on a local Anvil.

## Decision
- The gateway gets a `keystore` signer mode that works only in AVALANCHE_FUJI. Dev signers stay LOCAL_SIMULATION only.
- The owner creates three testnet-only wallets and holds every key and password on their own machine:
  - deployer, admin and simulation treasury (`0xB62C…4889`);
  - operator (`0x5F90…8444`);
  - approver (`0xdFae…553F`).
- The gateway loads the operator and approver from encrypted keystores plus password files. Startup fails if:
  - a keystore does not decrypt to its declared address;
  - the two keys are the same;
  - they are not the operator and approver named in the deployment manifest.
- The operator key also relays the permissionless `executeRelease`. No admin, pauser or deployer key is loaded into the gateway.
- Mainnet stays refused everywhere, by chain id and by host.

## Consequences
- Release approval is a single hot key, not a multisig. The contract still enforces the objection window, open disputes, accepted evidence, milestone caps and the fixed beneficiary.
- A leaked approver key could queue releases of reserved funds to the fixed beneficiary only. A leaked operator key cannot move funds out of escrow.
- Before any non-testnet use, ADR-005 applies in full: KMS/HSM, a multisig approver and a security review.
