# rise-ai-avalanche

Avalanche C-Chain gateway and Solidity escrow for Rise AI backend Phase 3. Originally imported from FreelaX at commit `b543b65` (`avalanche-integration` and `avalanche-contracts`), then rewritten for Rise AI.

**Status (2026-11-11): integrated, opt-in, testnet only.** The backend calls this gateway through `AvalancheGatewayClient` when it runs with `rise.escrow.engine=avalanche` (`RISE_ESCROW_ENGINE=avalanche`): `AvalancheEscrowEngine` keeps the MySQL ledger and queues chain commands (`chain_commands`, V12), which `ChainCommandRelay` sends here. The default engine is still `simulated`, so without that setting donations and releases run on `SimulatedEscrowEngine` only. End-to-end, dispute and anchoring runs on Fuji are recorded below (2026-11-10). The frontend does not call this gateway; it reads chain status from the backend's `/api/v1/transparency/*` endpoints.

**Simulation only:** `LOCAL_SIMULATION` (Anvil, chain 31337) and `AVALANCHE_FUJI` (chain 43113). Startup refuses anything else. Mainnet is out of scope.

## Trust model: what the chain guarantees, and what it does not

**rVND is a receipt, not money.** Donations are real-world payments into an escrow bank account (simulated in this project). Each confirmed donation mints nothing new: it moves rVND from the treasury into `CampaignEscrow`, one rVND base unit per 0.01 VND. The chain is the **ledger of record with rules that enforce themselves**, not a wallet.

What the contract enforces, even against the platform's own keys:

| Rule | Where |
|---|---|
| A release needs accepted evidence on the milestone, waits out a public objection window, and stops while a dispute or a guardian hold is open | `queueRelease`, `executeRelease` |
| Value leaves only to the campaign's fixed beneficiary (release) or the treasury (refund) | `executeRelease`, refunds |
| A milestone never releases more than its planned budget | `reserve` |
| An **independent guardian** can hold a milestone's releases directly on-chain. The platform cannot lift the hold early (only the guardian can, or anyone after 14 days) and cannot revoke or appoint the guardian | `placeHold`, `liftHold`, `GUARDIAN_ROLE` |
| The guardian attests the escrow bank balance; a balance below `totalHeld` is a public reserve gap | `attestReserves` |
| After a release, the platform records the bank transfer reference, and only the beneficiary's own wallet can confirm receiving it | `recordPayout`, `confirmReceipt` |
| The ledger, audit log and monitoring history are anchored as chained Merkle roots; editing, deleting or back-dating a row shows as MISMATCH | `RiseAnchor`, `GET /api/v1/transparency/anchors` |

What the chain does **not** guarantee:

- **That evidence is true.** A hash proves a file was not changed after anchoring, not that its content is real.
- **That the bank balance is real** beyond the guardian's word. Proof of reserves is as good as the guardian's independence and the bank statement it hashes.
- **That a donor's dispute reaches the chain.** Donors hold no wallet, so the backend relays disputes. The guardian is the counterweight: it can hold a milestone without the platform.
- **Key custody.** Fuji uses operator-held keystores (ADR-009). Production needs KMS/HSM and a multisig approver (ADR-005), plus an external contract review (`docs/decisions-pending.md`, P6).

Independent checks anyone can run without trusting the platform: read the verified contract on Snowtrace, call its views (`getCampaign`, `openHoldCount`, `getReserveAttestation`, `getPayout`), and compare `GET /api/v1/transparency/activity`, which lists the escrow's transactions as the **Avalanche Data API** indexed them and flags any successful platform-signed transaction the backend never recorded.

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
- At import, the `FreelaXEscrow` names, ABI and EIP-712 strings were left unchanged. The FreelaX contract has since been replaced: `contracts/src` now holds only `CampaignEscrow`, `RiseVND` and `RiseAnchor`, and no file under `src/main` or `contracts/src` references `FreelaXEscrow`.

## Run the tests

This machine may not have JDK 21, so use the pinned images:

```bash
MSYS_NO_PATHCONV=1 docker run --rm --memory=2560m \
  -v //var/run/docker.sock:/var/run/docker.sock \
  -v "<repo>/rise-ai-avalanche:/src" \
  -v "<home>/.m2:/root/.m2" -w /src \
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

**Re-run 2026-11-21** (branch `contest/avalanche-readiness`, same images): gateway `mvn clean test` 118 run, 0 failures, 0 errors, 3 skipped (the three optional tests below), BUILD SUCCESS; `forge clean && forge test --summary` 14 suites, 93 passed, 0 failed, 0 skipped (the FreelaX suites are gone), 7 invariants at 64 runs × 64 calls with 1,341 expected reverts; `sh scripts/verify-fuji.sh` against the live Fuji deployment 14/14 PASS (read-only, see `docs/fuji-guide.md`).

The skipped tests are optional: `AnvilWebContextTest`, `FujiReadOnlyWebContextTest`, one case in `ManifestIntegrationTest`, and the Foundry `UsdcForkTest`. The table above covers the imported FreelaX escrow only. CI now runs both suites on every pull request (`avalanche-gateway` and `avalanche-contracts` jobs in `.github/workflows/ci.yml`).

## Fuji runbook (testnet only, ADR-009)

Keys and passwords stay on the owner's machine. Nothing here is ever committed.

| Role | Address |
|---|---|
| Deployer, admin and treasury | `0xB62CA2602De72DC1edf5f6A7B614ba6376E14889` |
| Operator (backend writes; also relays releases) | `0x5F90Fbe1b75C99000d504e2Cb44D7dacE4ae8444` |
| Approver (queues releases, records refunds) | `0xdFae3BB55D4aEF68b645964806Ab1dCc6366553F` |
| Beneficiary (receives simulated rVND releases; no key needed by the system) | `0x017b6F8Fc223f02cC4CCc4433cdb26C408454cD0` |
| Guardian (independent: holds releases, attests reserves; never a platform key) | `0x02d577F92f332Ce258F42E561e1BBF28196b6240` |

The guardian and the beneficiary act from their own wallets, for example MetaMask on the verified contract's "Write Contract" tab. The platform never holds their keys.

1. Deploy with the deployer keystore. `MIN_RELEASE_DELAY` is the contract floor; the backend chooses the actual window, which must not be shorter.

       cd contracts && bash scripts/install-deps.sh
       DEPLOY_ENVIRONMENT=AVALANCHE_FUJI OPERATOR=0x5F90Fbe1b75C99000d504e2Cb44D7dacE4ae8444 \
         APPROVER=0xdFae3BB55D4aEF68b645964806Ab1dCc6366553F GUARDIAN=0x02d577F92f332Ce258F42E561e1BBF28196b6240 MIN_RELEASE_DELAY=60 \
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

### Fuji deployment, contract version 2 (2026-11-14)

Deployed from the deployer keystore with `GUARDIAN=0x02d577F92f332Ce258F42E561e1BBF28196b6240`; all three contracts are source-verified on Snowtrace (Routescan).

| Contract | Address |
|---|---|
| CampaignEscrow | [`0x183600b4392E2344df90e8d24b9e925832E32031`](https://testnet.snowtrace.io/address/0x183600b4392E2344df90e8d24b9e925832E32031) |
| RiseVND (rVND) | [`0x4Fd2ba7be8DfE084b5b8A78f67f45F781e40e80A`](https://testnet.snowtrace.io/address/0x4Fd2ba7be8DfE084b5b8A78f67f45F781e40e80A) |
| RiseAnchor | [`0x57C043B4830EfFF02A752476d77Ce45A21c14f4f`](https://testnet.snowtrace.io/address/0x57C043B4830EfFF02A752476d77Ce45A21c14f4f) |

Read back from the chain after deployment: the guardian holds `GUARDIAN_ROLE` and is that role's own admin; the platform admin does not hold it; operator and approver hold their roles; `GUARDIAN_HOLD_MAX` is 1,209,600 seconds (14 days); `minReleaseDelay` is 60; the escrow pins the deployed rVND.

Verify a contract again (no key needed):

    forge verify-contract <address> src/CampaignEscrow.sol:CampaignEscrow --chain 43113 \
      --verifier-url https://api.routescan.io/v2/network/testnet/evm/43113/etherscan --etherscan-api-key verifyContract \
      --constructor-args $(cast abi-encode "constructor(address,address,address,uint64,address)" <admin> <token> <treasury> 60 <guardian>)

### Fuji guardian run, contract version 2 (2026-11-14)

A 12,000,000 VND release of a demo campaign was queued with a 10-minute objection window. The guardian, the beneficiary and the platform each acted from their own wallet; the guardian and the beneficiary used MetaMask on the verified contract's "Write Contract" tab.

| Time (UTC) | Who | What | Transaction |
|---|---|---|---|
| 09:01:21 | Platform (approver) | release queued | [0x3368…927a](https://testnet.snowtrace.io/tx/0x336856f6c868cd2465bbac40c62b24ae724d8942d8b4bfeb62daf683d706927a) |
| 09:08:47 | Guardian | `placeHold` on the milestone | [0x3d7a…6deb](https://testnet.snowtrace.io/tx/0x3d7af22aac6d0234df7c4806a50261ebafb2d50529c20ab64bc100860236fdeb) |
| 09:08:51 | Guardian | the same hold again: refused with `AlreadyRecorded()` | [0xd966…40d3](https://testnet.snowtrace.io/tx/0xd966972e790a73ef4ad28d3a348d6ef61033a44e540aa7fe794326eb9c6e40d3) |
| 09:11–09:15 | Platform (relayer) | `executeRelease` after the window, repeatedly refused with `MilestoneHeld()`; the backend showed HELD_BY_GUARDIAN | for example [0x15aa…a303](https://testnet.snowtrace.io/tx/0x15aac4db30fb266703d52efaaa36aff3a484209a675764c613a8e7214e00a303) |
| 09:15:51 | Guardian | `liftHold` | [0xd4b9…c0f0](https://testnet.snowtrace.io/tx/0xd4b95716d2f5e40c421f1447127b3676999e5757f606131359d507c6e842c0f0) |
| 09:15:58 | Platform (relayer) | release executed; the request became RELEASED | [0x69e5…5da2](https://testnet.snowtrace.io/tx/0x69e54ef0269b430a55661667abc531fe80e1be2eebc63cc30bfd075c19f75da2) |
| 09:16:10 | Platform (operator) | payout recorded with a simulated bank reference | [0x7692…14a0](https://testnet.snowtrace.io/tx/0x76929a8219e30d3446b57594eb7537faa897f10a5c48ed5bd8c88ad5732d14a0) |
| 09:18:36 | Guardian | `attestReserves`, period 202611: bank 135,000,000 VND against 135,000,000 held, gap 0 | [0x42c9…3e68](https://testnet.snowtrace.io/tx/0x42c94e1b11c52acc24436b048b6546fa073eb660601148c4085557acb1a03e68) |
| 09:29:40 | Beneficiary | `confirmReceipt` from its own wallet | [0xbe08…78d8](https://testnet.snowtrace.io/tx/0xbe08d1fa3cdc3747e1c0eed2fff9f2531496eda512b13efc3817e711197378d8) |

`GET /api/v1/transparency/activity` listed these from the Avalanche Data API with the expected checks: the guardian's and the beneficiary's transactions as INDEPENDENT, the refused calls as REVERTED, every successful platform transaction as RECORDED, and zero UNRECORDED. The 32 commands of the demo seed (six campaigns) were relayed to the same deployment before this run, all confirmed; the seeded library release stays blocked by its open dispute.

### Fuji end-to-end run (2026-11-10)

These runs used contract version 1, before the guardian, reserve attestation and payout receipts. Deployment: escrow `0xBfD3BcEc6A23b5107c7AEb13F601c4B39719652A`, rVND `0x1e5F693e787AB7556846fE6A41545F5D6CBd2980`, anchor `0xc89c4de6b0fd19db8ff236A55eeD84e9ea17B0b5`.

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

Written at import time. The State column was updated on 2026-11-11 from the merged code.

| # | Gap at import | State (2026-11-11) |
|---|---|---|
| 1 | **Contract model.** The gateway targeted `FreelaXEscrow` (one client, one freelancer, one fixed amount) | **Closed.** `CampaignEscrow` is deployed on Fuji (address above) and used by the gateway (`contract/CampaignCalls`, `CampaignEvents`, `CampaignReader`) |
| 2 | **Domain layer** written for the freelance lifecycle | **Closed.** `api/CampaignCommandService` and `POST /api/v1/avalanche/campaigns/{campaignId}/actions/{action}/execute` |
| 3 | **Backend adapter** missing | **Closed.** `AvalancheEscrowEngine` behind `rise.escrow.engine`, default `simulated`; `AvalancheGatewayClient` |
| 4 | **Sync versus async** chain writes | **Addressed differently from ADR-003/005.** Ledger rows are still `COMPLETED` in the business transaction; chain calls go through the `chain_commands` outbox (V12), and a release becomes RELEASED only after the chain executes it. There is no chain-event mirror into the ledger |
| 5 | **Currency** (6-decimal USDC) | **Closed.** `ManifestVerifier` requires a 2-decimal rVND token and refuses real or bridged USDC |
| 6 | **Beneficiary address** | **Closed for the simulation.** One configured address, `RISE_AVALANCHE_BENEFICIARY_ADDRESS` |
| 7 | **Signing on Fuji** | **Closed as a testnet exception** (ADR-009, keystore mode). KMS/HSM and a multisig are still required before any non-testnet use |
| 8 | **Architecture record**: ADR-005 places chain logic inside the backend | **Open.** ADR-009 covers signers only; no ADR records the separate gateway service |
| 9 | **Operations**: no Dockerfile, Compose service or CI job | **Closed for local use.** CI jobs exist; `Dockerfile` and a LOCAL_SIMULATION Compose service (Anvil, contract deploy, gateway, signer bootstrap) were added by PR #23, see `docs/local-docker-stack.md`. No Fuji or production deployment shape exists |
| 10 | **Assurance**: no Fuji deployment, no external audit | **Partly closed.** Deployed and run on Fuji once (2026-11-10). No external security review of `CampaignEscrow`, `RiseVND` or `RiseAnchor` |

## Rules

- AI and monitoring never hold a signing role or trigger a chain write.
- Never commit private keys, mnemonics or keystores. Blank `AVALANCHE_INTERNAL_API_KEY` means every POST is refused.
- Contract state is the source of truth for custody; MySQL only mirrors confirmed events (ADR-005).
