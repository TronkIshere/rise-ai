# Fuji read-only integration — FJ-01 / FJ-02

This profile reuses the existing Backend, Gateway, contracts and Transparency APIs. It does not turn the existing Docker Demo into a Fuji environment, introduce an API, deploy contracts, import simulated history or enable a wallet. Defaults remain unchanged for existing deployments; both read-only properties default to `false`.

## Isolated profile

The local validation instance uses MySQL `127.0.0.1:24318`, Gateway `127.0.0.1:28082`, Backend `127.0.0.1:28080` and unchanged React at `127.0.0.1:5186`. Demo5174 and the separate Frontend candidate5176 retain their own source/runtime. Choose unused ports before reproducing, and never reuse their database volumes.

Gateway properties:

- `avalanche.environment=AVALANCHE_FUJI`, official HTTPS Fuji C-Chain RPC, verified chain43113 and existing `contracts/deployments/AVALANCHE_FUJI.json`.
- `avalanche.tx.enabled=true` retains the existing GET controller/deployment verification. **It is not the no-send switch.** Set `avalanche.read-only=true` (`AVALANCHE_READ_ONLY=true`) and `avalanche.signers.enabled=false`. Do not mount keystores/password files.
- Use a new empty isolated MySQL instance with schema `avalanche_gateway`, as required by the existing database guard. Never copy signed or pending transaction rows into this reader.
- Use a private local internal API key where the existing configuration requires it; never print or commit its value. Mutation requests are forbidden even when this key is valid.

Backend properties:

- `rise.escrow.engine=avalanche`, `rise.avalanche.read-only=true`, existing Gateway URL on28082.
- `rise.seed.enabled=false`, `rise.jobs.enabled=false`, `rise.monitoring.enabled=false`; private local JWT/config values where startup requires them.
- Use a separate empty business database. It is intentionally **not** historical Fuji evidence. Do not label its ledger as matched with old Fuji anchors.
- During the captured validation, the existing Data API URL is quarantined to unused `http://127.0.0.1:28085`. No fake service or response exists there. The Backend consequently reports activity unavailable. This prevents repeated calls after the provider's previous Cloudflare1010 rejection; it does not verify Data API integration or schema.

Do not run `scripts/run-fuji-gateway.sh` for this profile: that older signing helper replaces a named container, uses the Demo gateway port and enables signing.

## FJ-01: public identity without signing

The existing Gateway `/config` obtains operator/approver from the signer registry when present. With signers absent, it now falls back only to addresses in the **already verified deployment manifest**. This preserves the response contract and the Backend activity classifier's public identity information without creating, loading or decrypting keys. A fallback address does not grant signing capability or modify contract roles.

## FJ-02: independent no-send barriers

- The Backend read-only filter rejects mutating HTTP verbs, including login/authentication writes, before controllers. Public U4 GETs continue to work.
- Backend chain scheduling, direct command/anchor relay entry points and the Gateway client mutation path are guarded by the explicit read-only property. The existing general jobs switch alone is insufficient.
- Gateway mutations are rejected before execution even with a valid internal key. Enabling signers in read-only mode fails startup before signer creation or keystore access.
- The Gateway does not create its reconciliation/scanning schedulers in read-only mode; submission is unavailable. The native RPC client also rejects `eth_sendRawTransaction` before calling web3j.
- Read-only RPC method logs, HTTP denial tests, isolated command/anchor/transaction/nonce row counts and public account nonces provide complementary evidence. A stable external account nonce alone is not proof that no attempt was made; source guards/tests and the instance's own observations are required.

Local schema migration/initialization writes are expected in the fresh databases. The restriction concerns business, signing, broadcast and scheduler mutations after initialization. No active Demo database is modified.

## Existing data and truthful U4 states

Reuse `/api/v1/transparency/chain`, `/anchors`, `/reserves`, `/activity` and `/campaigns/{campaignId}`. The approved `TransparencyPage.tsx`, its CSS/illustration and Frontend API types are unchanged.

Contract metadata, existing reserve attestation and on-chain anchor state can be read without a signer. A real Guardian attestation is displayed with its original timestamp and simulated-rVND labels; it is not a new or independently verified bank balance. If no attestation exists, the existing absent-attestation state must remain visible.

An empty unrelated SQL database cannot reproduce old LEDGER/AUDIT roots: `MISMATCH`, missing history or unavailable comparison is honest. FINDINGS with no anchor remains `NOT_ANCHORED`. The campaign endpoint first requires a visible Backend campaign; without corresponding canonical records, its positive historical-mapping scenario remains blocked. Never seed fictitious campaigns/hashes merely to obtain a successful response.

Data API transaction history remains **BLOCKED** while provider access is unresolved. The preceding403 body identified Cloudflare1010/browser-signature rejection, not proof of a missing key. Follow the provider's documented endpoint/access process; do not rotate clients or spoof identities to bypass protection. Adding a key to a host `.env` does not inject it into an existing Backend container by itself. No provider retry was needed for the other Fuji read gates.

## Validation and evidence

Run the related FJ tests, then the existing Frontend `npm run verify`. Verify the two read-only properties are active before starting any instance. Negative tests must show local HTTP403 and RPC send rejection without a downstream broadcast. Capture observed SQL/nonce/log values before and after the observation window; retain evidence of the runtime settings without secret values.

Chromium1440×900DPR1 should capture the full U4 React page and card details, verify actual platform fonts and no overflow, and exercise only GET navigation. The full-page image is required; section captures are supplementary. Source Git/blob hashes and live Demo/Preview checks establish preservation. Existing user-profile pages are not part of this public read-only environment and authentication writes are intentionally unavailable.

Classify each gate independently. Passing contract reads and no-send tests does not make Data API history, historical SQL/Fuji reconciliation or signing E2E PASS. Those require separate source evidence/access and, for any future transaction, separate Owner authorization. No merge is implied by this profile.
