# Hướng dẫn Fuji: kiểm tra, triển khai lại, verify, smoke test

Tệp này dành cho chủ dự án (người giữ khóa) và ban giám khảo (không cần khóa). Mọi lệnh ký giao dịch đều do **chủ dự án tự chạy** bằng keystore hoặc ví phần cứng của mình. Repo không chứa và không bao giờ chứa khóa riêng, mnemonic, keystore hay mật khẩu (ADR-009).

## 1. Trạng thái hiện tại (đọc on-chain, không cần khóa)

Bản triển khai đang dùng: contract version 2, ngày 2026-11-14, manifest [`rise-ai-avalanche/contracts/deployments/AVALANCHE_FUJI.json`](../rise-ai-avalanche/contracts/deployments/AVALANCHE_FUJI.json).

| Contract | Địa chỉ | Tx triển khai |
|---|---|---|
| CampaignEscrow | [`0x183600b4392E2344df90e8d24b9e925832E32031`](https://testnet.snowtrace.io/address/0x183600b4392E2344df90e8d24b9e925832E32031) | [`0xa5288dea…f65e`](https://testnet.snowtrace.io/tx/0xa5288dea5448e2c2527ed15cebbcb2ec282bf66a661c2190dc5a2f3b2cc8f65e) |
| RiseVND (rVND) | [`0x4Fd2ba7be8DfE084b5b8A78f67f45F781e40e80A`](https://testnet.snowtrace.io/address/0x4Fd2ba7be8DfE084b5b8A78f67f45F781e40e80A) | [`0x17e33407…ba28`](https://testnet.snowtrace.io/tx/0x17e334079616683c7c0cb24095fa6461573458bd119a15458097aac3c369ba28) |
| RiseAnchor | [`0x57C043B4830EfFF02A752476d77Ce45A21c14f4f`](https://testnet.snowtrace.io/address/0x57C043B4830EfFF02A752476d77Ce45A21c14f4f) | [`0x1654e8c7…ec4b`](https://testnet.snowtrace.io/tx/0x1654e8c7a0acdde29b1d0d8a429a1185b733ebf7559965c89519a1e79726ec4b) |

Cả ba đã verify mã nguồn trên Snowtrace (Routescan), solc 0.8.38, optimizer bật.

Tự kiểm tra (chỉ gọi `eth_chainId`, `eth_getCode`, `eth_call`; cần Docker):

```bash
cd rise-ai-avalanche/contracts
bash scripts/forge.sh "sh scripts/verify-fuji.sh"
```

Kết quả mong đợi: 14 dòng `PASS` rồi `ALL CHECKS PASSED` (chain 43113, bytecode của ba contract, escrow trỏ đúng rVND, `minReleaseDelay`, token 2 chữ số thập phân, vai trò operator/approver/guardian/anchorer, guardian tự quản vai trò của mình, admin nền tảng **không** giữ vai trò guardian, `totalHeld` bằng số dư rVND của escrow). Lần chạy 2026-11-21: tất cả PASS, `totalHeld` = 13.500.000.000 đơn vị cơ sở (135.000.000 VND), anchor LEDGER = 4, AUDIT = 6, FINDINGS = 0. Script thoát mã 1 nếu có kiểm tra sai (đã thử bằng manifest sửa `minReleaseDelay`).

## 2. Chuẩn bị ví và coin thử nghiệm

- **Mạng Fuji** (Core hoặc MetaMask): RPC `https://api.avax-test.network/ext/bc/C/rpc`, chain id `43113`, ký hiệu `AVAX`, explorer `https://testnet.snowtrace.io`.
- **AVAX testnet** cho phí gas: faucet chính thức của Avalanche (Core: `https://core.app/tools/testnet-faucet/`, chọn C-Chain). Điều kiện nhận (mã coupon, số dư mainnet) do faucet quyết định và có thể thay đổi.
- **USDC testnet: không cần.** Escrow chỉ giữ rVND mô phỏng; gateway từ chối mọi token không phải rVND 2 chữ số thập phân, kể cả USDC thật hoặc bridge (`ManifestVerifier`). rVND do script deploy mint vào treasury.
- Cần gas cho: deployer (chỉ khi triển khai lại), operator, approver (gateway gửi giao dịch của hai ví này), guardian và beneficiary (chỉ khi tự thao tác từ ví của họ).

## 3. Triển khai lại (chỉ khi cần, ví dụ sau khi sửa contract)

Script `script/Deploy.s.sol` từ chối mọi chain khác 31337/43113, nêu đích danh mainnet 43114 (`MainnetForbidden`), yêu cầu operator ≠ approver, guardian độc lập với mọi ví nền tảng và với ví deploy, rồi ghi manifest `deployments/AVALANCHE_FUJI.json`.

```bash
cd rise-ai-avalanche/contracts && bash scripts/install-deps.sh
export DEPLOY_ENVIRONMENT=AVALANCHE_FUJI MIN_RELEASE_DELAY=60 \
  OPERATOR=0x5F90Fbe1b75C99000d504e2Cb44D7dacE4ae8444 APPROVER=0xdFae3BB55D4aEF68b645964806Ab1dCc6366553F \
  GUARDIAN=0x02d577F92f332Ce258F42E561e1BBF28196b6240
RPC=https://api.avax-test.network/ext/bc/C/rpc

# chạy thử, không gửi gì
forge script script/Deploy.s.sol --rpc-url $RPC --account <tên keystore> --sender <địa chỉ deployer>

# gửi thật, cách A: keystore của Foundry (~/.foundry/keystores, forge hỏi mật khẩu)
forge script script/Deploy.s.sol --rpc-url $RPC --account <tên keystore> --sender <địa chỉ deployer> --broadcast
# cách B: ví Ledger
forge script script/Deploy.s.sol --rpc-url $RPC --ledger --sender <địa chỉ trên Ledger> --broadcast
```

Không có Foundry cài sẵn thì chạy trong image đã ghim (`ghcr.io/foundry-rs/foundry:v1.8.7`) qua `scripts/forge.sh`, mount thêm thư mục keystore chỉ-đọc. Sau khi deploy, chạy lại mục 1 để xác nhận, rồi cập nhật bảng địa chỉ ở mục 1 và trong `rise-ai-avalanche/README.md`.

## 4. Verify mã nguồn trên Snowtrace (không cần khóa)

```bash
V="--chain 43113 --verifier-url https://api.routescan.io/v2/network/testnet/evm/43113/etherscan --etherscan-api-key verifyContract"
forge verify-contract <rVND> src/RiseVND.sol:RiseVND $V --constructor-args $(cast abi-encode "constructor(address)" <admin>)
forge verify-contract <anchor> src/RiseAnchor.sol:RiseAnchor $V --constructor-args $(cast abi-encode "constructor(address)" <admin>)
forge verify-contract <escrow> src/CampaignEscrow.sol:CampaignEscrow $V \
  --constructor-args $(cast abi-encode "constructor(address,address,address,uint64,address)" <admin> <rVND> <admin> 60 <guardian>)
```

`<admin>` là ví deploy (cũng là treasury). Cài đặt trình biên dịch phải khớp `foundry.toml` (solc 0.8.38, optimizer 200, `via_ir`, `evm_version = "cancun"`).

## 5. Smoke test trên Fuji (chủ dự án ký)

Mục tiêu: một luồng chính đầy đủ, lấy tx hash cho báo cáo. Ví operator và approver ký qua gateway; guardian và beneficiary ký từ ví riêng.

1. Khởi động gateway Fuji với keystore của operator và approver: `bash rise-ai-avalanche/scripts/run-fuji-gateway.sh` (đọc đầu file để biết đường dẫn keystore, tệp mật khẩu và MySQL cần có). Dấu hiệu sẵn sàng: log có `Started AvalancheGatewayApplication`.
2. Khởi động backend với `RISE_ESCROW_ENGINE=avalanche`, `RISE_AVALANCHE_INTERNAL_API_KEY` (trùng với gateway), `RISE_AVALANCHE_BENEFICIARY_ADDRESS=0x017b6F8Fc223f02cC4CCc4433cdb26C408454cD0`, `RISE_AVALANCHE_RELEASE_DELAY=PT2M`, và frontend (`rise-ai-frontend`, `npm run dev`).
3. Chạy kịch bản trong README gốc, mục "Kịch bản end-to-end": tạo và duyệt chiến dịch → đóng góp → Admin xác nhận → gửi và duyệt minh chứng → đề nghị giải ngân → duyệt → giải ngân.
4. Giao dịch mong đợi trên escrow, theo thứ tự: `openCampaign`, `recordContribution`, `anchorEvidence`, `decideEvidence`, `reserve`, `queueRelease` (ví approver), `executeRelease` (sau cửa sổ phản đối), `recordPayout`.
5. Nhánh guardian (tùy chọn, trong cửa sổ phản đối): guardian gọi `placeHold` từ ví riêng; `executeRelease` phải bị từ chối với `MilestoneHeld()`; guardian gọi `liftHold`; release chạy tiếp.

   ```bash
   # id trên chain = 0x + 32 số 0 + UUID bỏ dấu gạch; lấy chainCampaignId từ GET /api/v1/transparency/campaigns/{id}
   cast send 0x183600b4392E2344df90e8d24b9e925832E32031 "placeHold(bytes32,bytes32,bytes32,bytes32)" \
     <chainCampaignId> <milestoneId bytes32> <holdId: bytes32 bất kỳ do guardian tự chọn> <hash lý do> --account <keystore guardian> --rpc-url $RPC
   cast send 0x183600b4392E2344df90e8d24b9e925832E32031 "liftHold(bytes32,bytes32)" <chainCampaignId> <holdId> \
     --account <keystore guardian> --rpc-url $RPC
   ```

   Hoặc dùng tab "Write Contract" của contract đã verify trên Snowtrace với MetaMask/Core.
6. Beneficiary gọi `confirmReceipt(chainCampaignId, requestId)` từ ví của mình.
7. Thu tx hash: `GET /api/v1/transparency/campaigns/{campaignId}` (mỗi giao dịch có `explorerUrl`), `GET /api/v1/transparency/activity` (đối chiếu từ Avalanche Data API: không được có dòng `UNRECORDED`), hoặc trang `/transparency` trên giao diện.
8. Chạy lại mục 1 để xác nhận `totalHeld` vẫn bằng số dư rVND của escrow.

## 6. Cấu hình phí gas (đề xuất, chưa áp dụng)

Đo từ receipt trên Fuji (2026-11-14): giao dịch do gateway gửi dùng `maxPriorityFeePerGas` = 1 gwei (mặc định `avalanche.tx.priority-fee-gwei`, ghi chú trong mã là "to calibrate"), trong khi base fee lúc đó là 10 wei; mỗi giao dịch tốn khoảng 0,00025 AVAX. Giao dịch MetaMask của guardian trên cùng contract dùng giá 160 wei. Gas limit cố định 500.000 cho hầu hết lệnh (`CampaignCommandService`), và receipt ghi `gasUsed` = 250.000 cho các giao dịch đó; nguyên nhân chưa kiểm chứng.

Thử nghiệm đề xuất (cần chủ dự án chạy trên Fuji và quan sát thời gian xác nhận): `AVALANCHE_TX_PRIORITYFEEGWEI=0` (phí tối đa còn 2 × base fee), và ước lượng gas (`eth_estimateGas` + biên) thay cho hằng số 500.000. Chưa đổi mặc định vì chưa có số đo thời gian xác nhận với tip 0.

## 7. Số đo trong README và cách đo lại (đọc 2026-11-21, không cần khóa)

| Số đo | Giá trị | Cách đo |
|---|---|---|
| Khoảng cách khối trung bình | 1,48 s/khối | `eth_getBlockByNumber` khối 61.054.000 (timestamp 1794646920) và 61.054.100 (1794647068): 148 s / 100 khối |
| Thời gian từ `liftHold` tới release | 7 s | timestamp khối 61.054.609 (09:15:51 UTC) và 61.054.615 (09:15:58 UTC), lấy từ `eth_getTransactionReceipt` của hai tx trong bảng guardian ở `rise-ai-avalanche/README.md` |
| Giao dịch của escrow v2 | 60 tx: 49 thành công, 11 bị từ chối; 5 ví gửi | `https://api.routescan.io/v2/network/testnet/evm/43113/etherscan/api?module=account&action=txlist&address=0x183600b4392E2344df90e8d24b9e925832E32031` (đếm `isError`, `from`) |
| Phí | trung vị 0,00025 AVAX/tx của ví nền tảng (55 tx), khoảng 1e-11 AVAX/tx của ví độc lập (5 tx); tổng 0,01267 AVAX | cùng danh sách trên: `gasUsed × gasPrice` |
| Số lần neo | LEDGER 4, AUDIT 6, FINDINGS 0 | `sh scripts/verify-fuji.sh` (dòng `info anchors`) |

Số giao dịch và phí sẽ tăng nếu có giao dịch mới tới contract sau ngày đo.

Ví dụ đo khoảng cách khối:

```bash
RPC=https://api.avax-test.network/ext/bc/C/rpc
for b in 61054000 61054100; do
  curl -s -X POST -H 'Content-Type: application/json' \
    --data "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"eth_getBlockByNumber\",\"params\":[\"$(printf '0x%x' $b)\",false]}" $RPC
done   # so sánh trường timestamp
