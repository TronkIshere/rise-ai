# On-chain UI: screens awaiting owner decisions

**Status: PROPOSED, nothing approved.** Written 2026-11-14. No frontend code has been written for these screens. Per the design governance in `docs/design/` and `rise-ai-frontend/AGENTS.md`, each screen needs explicit owner instruction plus a versioned entry in `docs/design/DESIGN_DECISION_LOG.md` before implementation. The homepage (`/`, B2 v1.3.2) stays locked and is not touched by anything here.

The backend APIs below already exist, are public (no login), are covered by tests, and were exercised against Fuji on 2026-11-14 (`rise-ai-avalanche/README.md`, "Fuji guardian run").

## Prerequisites (apply to every screen)

- Run `npm run contract:generate` first. The OpenAPI snapshot gained the `/api/v1/transparency/*` endpoints after the last frontend contract generation.
- The browser calls the backend only, through `src/api/client.ts`. It never calls the gateway (`:8082`), the Avalanche Data API or an RPC endpoint directly.
- No new dependency. Colors, spacing and type come from `src/styles/tokens.css`.
- Every screen handles loading, empty, error and success. Missing data shows "Chưa có dữ liệu". With the simulated engine every endpoint answers `onChain: false`; screens then say the escrow runs in simulation mode instead of showing empty chain data.
- Money is labelled as simulation. rVND is shown as a receipt of the escrow bank account, never as money a donor owns.
- Wording follows the monitoring rules: "pattern", "inconsistency", "requires review". Never "fraud", "theft", "misuse confirmed", "criminal", "definitely" or "suspicious person".

## Backend APIs available

| Endpoint | Gives |
|---|---|
| `GET /api/v1/transparency/chain` | network, chain id, escrow / rVND / anchor contract addresses, explorer URLs for transactions and addresses, guardian address, guardian hold maximum (seconds) |
| `GET /api/v1/transparency/campaigns/{campaignId}` | campaign open status on-chain; every CampaignEscrow transaction of the campaign with explorer links; per disbursement request: `chainStatus`, `releaseAt`, reserve / queue / release tx hashes, `openGuardianHolds`, `payoutTxHash`, `receiptConfirmedAt` |
| `GET /api/v1/transparency/reserves` | guardian's latest reserve attestation: period, time, attester, bank balance, held at attestation, gap, statement hash, current held (VND) |
| `GET /api/v1/transparency/activity?pageToken=` | the escrow's transactions as indexed by the Avalanche Data API: method, signer (PLATFORM, GUARDIAN, BENEFICIARY, OTHER), check (RECORDED, INDEPENDENT, REVERTED, UNRECORDED), time, rVND to beneficiary, explorer link, next page token, unrecorded count |
| `GET /api/v1/transparency/anchors` | per history (LEDGER, AUDIT, FINDINGS): CONSISTENT, MISMATCH, NOT_ANCHORED or UNAVAILABLE, anchored and recomputed roots, record counts, tx link |

## Screens

### U1. Campaign detail: on-chain badge

- **Where:** `CampaignDetailPage` (`/campaigns/:slug`), inside `SiteShell`.
- **Shows:** a badge "Ký quỹ on-chain · Avalanche Fuji" when `openStatus` is CONFIRMED, with a link to the escrow contract on Snowtrace. While PENDING or SUBMITTED: "Đang ghi lên chain". NOT_ON_CHAIN: no badge, or "Mô phỏng" when `onChain` is false.
- **APIs:** `/transparency/chain`, `/transparency/campaigns/{id}`.
- **To decide:** badge position (header next to the category, or the funding block); wording.

### U2. Campaign detail: disbursement timeline with chain status

- **Where:** the milestones / disbursement section of `CampaignDetailPage`. Kept separate from funding progress, as the frontend rules require.
- **Shows per request:** amount, backend status, chain status label, a countdown to `releaseAt` during the objection window, guardian hold notice, release transaction, payout transaction and the beneficiary's confirmation time.
- **Proposed labels** (chainStatus → Vietnamese):

  | chainStatus | Label | Note |
  |---|---|---|
  | RESERVING / RESERVED_ON_CHAIN | Đã giữ chỗ trên chain | |
  | QUEUING | Đang đưa vào hàng chờ | |
  | IN_OBJECTION_WINDOW | Đang trong thời gian phản đối · còn mm:ss | countdown to `releaseAt` |
  | BLOCKED_BY_DISPUTE | Tạm dừng do tranh chấp | |
  | HELD_BY_GUARDIAN | Tạm dừng bởi bên giám hộ độc lập | links the guardian address |
  | RELEASED_ON_CHAIN | Đã giải ngân trên chain | + payout and receipt lines |
  | CANCELLING / CANCELLED_ON_CHAIN | Đã hủy trên chain | |
  | FAILED | Cần xem xét | never shows error text; the API does not expose it |
  | NOT_ON_CHAIN | (no label) | |

- **Receipt line:** "Người nhận đã xác nhận nhận tiền lúc …" when `receiptConfirmedAt` is set, otherwise "Người nhận chưa xác nhận". No deadline is shown: none has been decided.
- **To decide:** the labels above; whether the countdown refreshes live (proposed: every 30 seconds through TanStack Query) or only on load.

### U3. Donor: on-chain receipt of a donation (2B)

- **Where:** `DonorDashboardPage` (`/donor`), on each confirmed donation.
- **Shows:** "Biên nhận on-chain" linking to the `contribution` transaction on Snowtrace, found in `/transparency/campaigns/{campaignId}` → `transactions` where `action = contribution` and `referenceId` equals the donation id. A PENDING donation shows no receipt (it is not confirmed and not on-chain). A confirmed donation whose command is still pending shows "Đang ghi lên chain".
- **To decide:** link only, or also a short explainer ("Mã biên nhận chính là mã khoản quyên góp của bạn, ghi trên Avalanche"); whether a public lookup by donation id is wanted (not proposed: the donation id stays private to the donor).

### U4. New public page: "Đối chiếu on-chain"

- **Where:** a new route, proposed `/transparency`, inside `SiteShell`, linked from the footer or the main navigation.
- **Sections:**
  1. **Hợp đồng và mạng:** network, contract addresses with Snowtrace links, a note that the source is verified.
  2. **Bên giám hộ độc lập:** guardian address, what it can do (hold a milestone up to 14 days, attest reserves) and what it cannot (move funds); hold maximum from `/transparency/chain`.
  3. **Bằng chứng tiền dự trữ:** the latest attestation from `/transparency/reserves`: period, bank balance, held at that time, gap. A positive gap is shown as "Số dư ngân hàng thấp hơn số đang ký quỹ: cần xem xét". Not attested yet: "Chưa có xác nhận".
  4. **Toàn vẹn lịch sử:** the three anchors from `/transparency/anchors` with CONSISTENT / MISMATCH.
  5. **Hoạt động trên chain (Avalanche Data API):** a paged table from `/transparency/activity`: time, method, signer, check, rVND to beneficiary, Snowtrace link. Check labels: RECORDED "Khớp hồ sơ nền tảng", INDEPENDENT "Do bên độc lập thực hiện", REVERTED "Bị hợp đồng từ chối", UNRECORDED "Không có trong hồ sơ nền tảng: cần xem xét". The source is named on the page: "Dữ liệu từ Avalanche Data API, không từ cơ sở dữ liệu của Rise AI".
- **To decide:** the route name; navigation placement (new nav item, footer only, or both); section order; whether the activity table shows full addresses or shortened ones.

### U5. Admin: release in chain mode

- **Where:** `AdminReviewPage` (disbursement review) and `AdminEscrowPage`.
- **Shows:** after the admin presses release in Avalanche mode, the request stays APPROVED until the chain executes it. The page should say "Đã gửi lệnh giải ngân · chờ hết thời gian phản đối (đến hh:mm)" with the chain status from U2, instead of looking like nothing happened. `AdminEscrowPage` adds the Data API `unrecordedCount` as a "requires review" counter linking to U4.
- **To decide:** whether the release button is disabled while a release is on its way (the backend already ignores repeats); counter placement.

### U6. Guardian and beneficiary actions

- **Proposed:** no in-app screen. The guardian and the beneficiary act from their own wallets on the verified contract's Snowtrace "Write Contract" tab, as in the Fuji run. U4 explains this and links the contract. Keeping wallets out of the app matches the design (donors and the platform hold no wallet UI) and adds no dependency.
- **To decide:** confirm this, or ask for an in-app wallet flow (would need a new wallet dependency, listed for approval first).

## Owner decisions to record

| # | Decision | Proposed default |
|---|---|---|
| UI-1 | Approve U1–U5 placements | as above |
| UI-2 | Route and navigation for U4 | `/transparency`, linked from the footer and the main navigation |
| UI-3 | Vietnamese labels for chain status and checks | the tables in U2 and U4 |
| UI-4 | Viewport scope | Desktop 1440px first, like the approved homepage; mobile and tablet out of scope unless instructed |
| UI-5 | Address display | shortened (`0x1836…2031`) with copy and Snowtrace link |
| UI-6 | Live refresh of countdown and statuses | every 30 seconds while the page is open |
| UI-7 | Guardian and beneficiary actions | Snowtrace only, no in-app wallet (U6) |

Once decided, record each in `docs/design/DESIGN_DECISION_LOG.md` with the date and approver, then implement screen by screen with its acceptance checks (`npm run verify`).
