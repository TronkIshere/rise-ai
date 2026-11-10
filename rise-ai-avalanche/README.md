# rise-ai-avalanche

Avalanche C-Chain gateway and Solidity escrow for Rise AI backend Phase 3. Imported from FreelaX at commit `b543b65` (`avalanche-integration` and `avalanche-contracts`).

**Status: imported, not integrated.** Nothing in `rise-ai-backend` or `rise-ai-frontend` calls this module yet, and donations and releases still run on `SimulatedEscrowEngine`.

**Simulation only:** `LOCAL_SIMULATION` (Anvil, chain 31337) and `AVALANCHE_FUJI` (chain 43113). Startup refuses anything else. Mainnet is out of scope.

## Layout

| Path | Content |
|---|---|
| `src/` | Spring Boot 3.5.16 gateway, Java 21, web3j 6.1.0 (`com.riseai.avalanche`), default port 8082 |
| `contracts/` | Foundry project (solc 0.8.38, OpenZeppelin 5.7.0): escrow, mock USDC, deploy script |

Standalone Maven project. It shares no code or database with the other modules.

## What changed in the import

- Package `com.freelax.avalanchegateway` became `com.riseai.avalanche`.
- Maven coordinates, application name and jar became `rise-ai-avalanche`; the FreelaX parent POM was replaced by the Spring Boot parent with the same pinned versions.
- Default port moved from 9194 to 8082.
- `FreelaXEscrow` names, the ABI and EIP-712 strings were left unchanged on purpose. They are part of the contract's signing domain and must change together with the contract.

## Run the tests

This machine may not have JDK 21, so use the pinned images:

```bash
MSYS_NO_PATHCONV=1 docker run --rm --memory=2560m \
  -v //var/run/docker.sock:/var/run/docker.sock \
  -v "D:/hackathon/rise-ai/rise-ai-avalanche:/src" \
  -v "C:/Users/Tuan_Minh/.m2:/root/.m2" -w /src \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e MAVEN_OPTS=-Xmx384m \
  maven:3.9-eclipse-temurin-21 mvn -B -ntp clean test -DargLine=-Xmx1g

cd contracts && bash scripts/install-deps.sh && bash scripts/forge.sh "forge test --summary"
```

Docker-gated tests skip silently when Testcontainers cannot reach Docker, so check the skipped count.

### Last verified (2026-11-10, after the import)

| Suite | Environment | Result |
|---|---|---|
| Gateway `mvn clean test` | `maven:3.9-eclipse-temurin-21`, real Anvil and MySQL via Testcontainers | 119 run, 0 failures, 0 errors, 3 skipped, BUILD SUCCESS |
| Foundry `forge test --summary` | `ghcr.io/foundry-rs/foundry:v1.8.7` | 77 passed, 0 failed, 1 skipped |

Rise AI contracts added afterwards (`CampaignEscrow`, `RiseVND`, `RiseAnchor`): `forge test --summary` reports 143 passed, 0 failed, 1 skipped in total, 66 of them new. The 5 invariants ran 64 runs of 64 calls with 1,431 of 4,096 calls reverting as expected. Three deliberate mutations on a scratch copy (held balance not reduced, payout to the caller, objection window and dispute check removed) were each caught. These contracts have no security review and run locally only.

The skipped tests are optional: `AnvilWebContextTest`, `FujiReadOnlyWebContextTest`, one case in `ManifestIntegrationTest`, and the Foundry `UsdcForkTest`. These results cover the imported FreelaX escrow only, not any Rise AI flow.

## Fuji runbook (testnet only, ADR-009)

Keys and passwords stay on the owner's machine. Nothing here is ever committed.

| Role | Address |
|---|---|
| Deployer, admin and treasury | `0xB62CA2602De72DC1edf5f6A7B614ba6376E14889` |
| Operator (backend writes; also relays releases) | `0x5F90Fbe1b75C99000d504e2Cb44D7dacE4ae8444` |
| Approver (queues releases, records refunds) | `0xdFae3BB55D4aEF68b645964806Ab1dCc6366553F` |
| Beneficiary (receives simulated rVND releases; no key needed by the system) | `0x017b6F8Fc223f02cC4CCc4433cdb26C408454cD0` |

1. Deploy with the deployer keystore. `MIN_RELEASE_DELAY` is the contract floor; the backend chooses the actual window, which must not be shorter.

       cd contracts && bash scripts/install-deps.sh
       DEPLOY_ENVIRONMENT=AVALANCHE_FUJI OPERATOR=0x5F90Fbe1b75C99000d504e2Cb44D7dacE4ae8444 \
         APPROVER=0xdFae3BB55D4aEF68b645964806Ab1dCc6366553F MIN_RELEASE_DELAY=60 \
         forge script script/Deploy.s.sol --rpc-url https://api.avax-test.network/ext/bc/C/rpc \
         --account <deployer keystore name> --sender 0xB62CA2602De72DC1edf5f6A7B614ba6376E14889 --broadcast

2. Start the gateway with the manifest and the two keystores:

       AVALANCHE_ENVIRONMENT=AVALANCHE_FUJI AVALANCHE_RPC_PRIMARY_URL=https://api.avax-test.network/ext/bc/C/rpc
       AVALANCHE_TX_ENABLED=true AVALANCHE_TX_RECONCILE_INTERVAL_MS=2000 AVALANCHE_SIGNERS_ENABLED=true AVALANCHE_SIGNERS_MODE=keystore
       AVALANCHE_OPERATOR_KEYSTORE=<path> AVALANCHE_OPERATOR_PASSWORD_FILE=<path> AVALANCHE_OPERATOR_ADDRESS=0x5F90...8444
       AVALANCHE_APPROVER_KEYSTORE=<path> AVALANCHE_APPROVER_PASSWORD_FILE=<path> AVALANCHE_APPROVER_ADDRESS=0xdFae...553F
       AVALANCHE_DEPLOYMENT_MANIFEST=contracts/deployments/AVALANCHE_FUJI.json AVALANCHE_INTERNAL_API_KEY=<shared with the backend>
       AVALANCHE_DB_URL / USERNAME / PASSWORD: a local MySQL database named avalanche_gateway

3. Start the backend with `RISE_ESCROW_ENGINE=avalanche`, `RISE_AVALANCHE_INTERNAL_API_KEY` (same key), `RISE_AVALANCHE_BENEFICIARY_ADDRESS` and `RISE_AVALANCHE_RELEASE_DELAY` (for example `PT2M` for a demo, `PT24H` otherwise).

### Fuji end-to-end run (2026-11-10)

Deployment: escrow `0xBfD3BcEc6A23b5107c7AEb13F601c4B39719652A`, rVND `0x1e5F693e787AB7556846fE6A41545F5D6CBd2980`, anchor `0xc89c4de6b0fd19db8ff236A55eeD84e9ea17B0b5`.

A campaign of 30,000,000 VND in two milestones ran through the real backend (`RISE_ESCROW_ENGINE=avalanche`) and the gateway in keystore mode, with a 2-minute objection window. Seven transactions, all with receipt status success:

| Step | Transaction |
|---|---|
| open campaign | [0xc9b4…dc0c](https://testnet.snowtrace.io/tx/0xc9b49ba5b6c5e333c35c310f87f24dc2cdec5ccd6e442ba9c0f202a17970dc0c) |
| contribution (25,000,000) | [0x3e06…7b93](https://testnet.snowtrace.io/tx/0x3e06918a1f758d960752483e4bcfcc00e68f48031dc5038aa14418bb97017b93) |
| anchor invoice hash | [0x3aa4…79d0](https://testnet.snowtrace.io/tx/0x3aa462abe7e48e0d9834ef6d7b08734afbef69fec2f0608f7281e40d44ea79d0) |
| evidence accepted | [0xf3d8…ce29](https://testnet.snowtrace.io/tx/0xf3d81cbc43921ab7c57aeca5d03f9496b964a94dc52760cdf2da54399819ce29) |
| reserve (15,000,000) | [0xdd88…79b2](https://testnet.snowtrace.io/tx/0xdd88b093a185b6d9df97215e3ab16d76577c465e75b28bdf75cc1bdf3feb79b2) |
| queue release (approver) | [0x131c…1f15](https://testnet.snowtrace.io/tx/0x131cf0fc403f37ce91bdc78564fe5b2c7621253deef81617ea29f0a2df4b1f15) |
| execute release after the window | [0x675e…e8d9](https://testnet.snowtrace.io/tx/0x675e5e2465a48b4a2c4c9c519bdbcb00ac36d20b7cec2d8d00f33c762ba8e8d9) |

Read back from the chain afterwards: the beneficiary holds 15,000,000.00 rVND; the escrow holds 10,000,000.00; `totalHeld` equals the escrow balance; and the campaign shows confirmed 25,000,000, available 10,000,000, disbursed 15,000,000. The backend moved the request to RELEASED only after the execute transaction was confirmed, about two minutes after the admin's click.

### Fuji dispute run (2026-11-10)

A donor opened a misuse-of-funds dispute while a 10,000,000 VND release sat in its objection window.

1. After the window ended, `execute-release` was sent straight to the gateway, bypassing the backend. The contract refused it with `MilestoneDisputed()`: [0x203f…f768](https://testnet.snowtrace.io/tx/0x203fdba9829485ee7cff9dbbab2a171b217093b4fbb115fb8b9ec2e731c4f768) (receipt status failed).
2. The reservation stayed QUEUED, the beneficiary balance did not change, and the backend kept the request DISPUTED.
3. Once the admin dismissed the dispute, the backend sent [close-dispute](https://testnet.snowtrace.io/tx/0x4fbbc83091c1347052aa5cf6d7b391a1c150237a237cc8544d14a6860ef9f6c6).
4. The release then [executed](https://testnet.snowtrace.io/tx/0x267ebbf513e320ddc1016e71bb44d46a4dd6ececf6137a4e6bae339088e1fe1f), and the request became RELEASED.

### Fuji anchoring run (2026-11-10)

The escrow ledger (23 rows) and the audit log (185 rows) were anchored on RiseAnchor:
- [ledger](https://testnet.snowtrace.io/tx/0xc5fffecc07e535720abf031480eadc8419a27fe15c9878147417c4e1319f6a88);
- [audit log](https://testnet.snowtrace.io/tx/0x521a1aa639131e4c3ad9447498a185b906c6439961bf6a33cbf51b6873fd0225).

`GET /api/v1/transparency/anchors` then reported both as CONSISTENT, with the recomputed root equal to the on-chain root.

## Reusable as is

Chain client with RPC pacing, nonce allocation, transaction tracking through its full state set, log scanning with a cursor, deployment manifest verification, environment guards, and the internal API key filter. None of these depend on the freelance domain.

## Gaps against Rise AI

| # | Gap | Why it matters | Likely fix |
|---|---|---|---|
| 1 | **Contract model.** The gateway still targets `FreelaXEscrow`, one client, one freelancer and one fixed amount per escrow | Rise AI pools many donors per campaign, with several milestones, partial releases, reservations and pro-rata refunds | `contracts/src/CampaignEscrow.sol` written and tested locally; not deployed and not yet used by the gateway |
| 2 | **Domain layer.** `EscrowActionService`, `EscrowCalls`, `EscrowAuthorizations`, `FundTerms`, `EscrowEvents` and the DTOs are written for fund, submit, revise, dispute | They encode the freelance lifecycle | Rewrite against the new contract |
| 3 | **Backend adapter.** No `AvalancheEscrowEngine` and no gateway client exist | The module is unreachable from business flows | Add the engine behind `rise.escrow.engine`, default `simulated` |
| 4 | **Sync versus async.** `EscrowEngine` runs inside a mandatory transaction and every ledger row is `COMPLETED` | Chain writes are asynchronous | Pending/confirmed semantics, chain command outbox, event mirror (ADR-003, ADR-005) |
| 5 | **Currency.** `ManifestVerifier` still requires a 6-decimal USDC token | `CampaignEscrow` uses `RiseVND` (simulated VND, 2 decimals, matching `DECIMAL(19,2)`) | Update the verifier and manifest |
| 6 | **Beneficiary address.** Organizations have no on-chain address | `openCampaign` needs a fixed beneficiary | Use a configured simulation address for now |
| 7 | **Signing on Fuji.** Backend signers are limited to `LOCAL_SIMULATION`, and ADR-005 requires KMS/HSM and human-held multisig | There is no route to Fuji writes | Human-signed flow or KMS decision |
| 8 | **Architecture record.** ADR-005 places chain logic inside the backend; this module is a separate service | The decision needs updating | New ADR |
| 9 | **Operations.** No Dockerfile, Compose service or CI job; needs its own MySQL database named `avalanche_gateway` | Cannot run with the stack yet | Add when integration starts |
| 10 | **Assurance.** No Fuji deployment, no external audit; Slither triage only, in FreelaX | Evidence covers the FreelaX contract and local runs | Security review of the new contract before any public testnet use |

## Rules

- AI and monitoring never hold a signing role or trigger a chain write.
- Never commit private keys, mnemonics or keystores. Blank `AVALANCHE_INTERNAL_API_KEY` means every POST is refused.
- Contract state is the source of truth for custody; MySQL only mirrors confirmed events (ADR-005).
