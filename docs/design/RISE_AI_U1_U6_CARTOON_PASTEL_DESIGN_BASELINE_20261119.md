# RISE AI — U1–U6 · Cartoon Pastel B2 Design Baseline

**Ngày:** 19/11/2026  
**Phạm vi:** Desktop 1440 × 900, DPR 1; không mở thêm scope thiết kế mobile  
**Trạng thái:** DESIGN LANGUAGE LOCKED (đã chốt); U1–U6 DECISION LOG EXISTING; FINAL SOURCE FILES / RECONFIRMATION IN PROGRESS.  
**Repository:** `TronkIshere/rise-ai`  
**Mục tiêu:** Có design source of truth phục vụ duyệt lại từng màn, bàn giao và tích hợp React. **Không đồng nghĩa đã nghiệm thu React**.

## 0. Phân biệt ba cột trạng thái

- **Thiết kế đã chốt trước đó:** quyết định owner trong lịch sử, không hủy bỏ.
- **Re-freeze file cuối:** chỉ PASS nếu có đúng HTML/source + PNG full-page và Owner xác nhận đó là bản cuối sau mọi chỉnh sửa.
- **React QA:** chỉ PASS sau khi code tích hợp, ảnh Chromium 1440×900 thật, đối chiếu source và test API, interaction. KHÔNG suy từ concept hoặc demo.

## 1. Ngôn ngữ thị giác khóa chung

**Tên sử dụng nội bộ:** `Rise AI · B2 Cartoon Pastel Strong Outline / Pastel Semantic Color Palette`.

Không đánh đồng các lớp:

- *Visual language:* B2 Community Paper Landscape + Playful/Cartoon illustration + Strong Outline, tài liệu minh họa thân thiện nhưng đáng tin cậy.
- *Palette:* Pastel Semantic Color Palette = màu theo vai trò/ý nghĩa, không tô tùy tiện.
- *Components:* Two-tone panel (header sắc đậm, body cùng tông sáng), layered cards, bo góc mềm.
- *Effects:* Border strong / hard offset shadow; không dùng kính mờ đại trà hoặc bóng blur mơ hồ.
- *Typography:* hệ font dự án `Be Vietnam Pro`, bảo đảm dấu tiếng Việt.

### 1.1 Token palette — design reference

| Role | Solid / header | Soft / body | Ý nghĩa |
|---|---|---|---|
| `--rise-ink` | `#123E32` | — | Chữ chính, viền, shadow đậm |
| `--rise-forest` | `#103D30` | — | Hero/section nền tối; chữ phải kem sáng `#FFF8E8` |
| `--rise-mint` | `#49E1A7` | `#D9F8E6` | PLAN / thao tác, trạng thái tích cực / bộ lọc |
| `--rise-apricot` | `#FFBA74` | `#FFE5C3` | RECORD / hồ sơ xử lý, chú ý vừa phải |
| `--rise-sky` | `#71D0F3` | `#DCF2FF` | REVIEW / minh bạch, dữ liệu và đối chiếu |
| `--rise-sage` | `#DCE7B3` | `#EDF1D5` | Thống kê hỗ trợ, nền thư thái **không dùng màu rêu nhạt đại trà** |
| `--rise-coral` | `#FF9F91` | `#FFF0E9` | Nguy cơ/cảnh báo ưu tiên cao; nhấn cục bộ, không phủ tràn mọi card |
| `--rise-cream` | `#FFFDF8` | `#F7F8EF` | Nền giấy trung tính / bố cục |

**Lưu ý:** Đây là *design reference token*, không phải lệnh thay thế token toàn ứng dụng. Khi tích hợp React, mở rộng `rise-ai-frontend/src/styles/tokens.css` sau khi đối chiếu values thực tế; không tạo runtime theme mới song song.

### 1.2 Cấu trúc card chuẩn U5 / U6

1. **Card ngoài** bo góc 16–20px, viền xanh thông **2.5–3px**, bóng *hard offset* 5–8px, không blur tràn.
2. **Đầu card có màu đậm rõ ràng** theo vai trò, cao vừa nội dung (không thêm ô vuông minh họa hoặc khối trống trang trí). Title 15–17px trở lên khi đủ chỗ, trọng lượng cao.
3. **Thân card cùng tông sáng** hoặc nền giấy kem phối hợp; chia nhóm thông tin bằng spacing/padding thực, không chỉ một đường line, không rải chữ trơ trên nền.
4. Card chứa dữ liệu cần nhìn một lượt: **tên / con số chủ đạo / trạng thái / giải thích / CTA**, giữ tỷ lệ tương xứng chức năng; không lồng quá nhiều card nhỏ.
5. Các nhóm ngang dùng **khối thông tin bo góc mềm**, padding tương đương, khoảng cách 10–16px, không kẻ lưới ô vuông xuyên toàn card.
6. Head card màu tươi (Mint/Apricot/Sky) dùng **chữ Ink `#123E32`**. Hero/section Forest dùng **chữ kem sáng `#FFF8E8`**. Không đặt chữ tối trên nền tối.
7. Contrast: tối thiểu 4.5:1 cho chữ cỡ thường khi khả thi; kiểm tra tương phản thực tế với token và không phụ thuộc màu để diễn đạt trạng thái.

### 1.3 Typography và spacing

- `--font-sans: 'Be Vietnam Pro', 'Segoe UI', system-ui, sans-serif` — dùng token font **đang có trong project**.
- Nội dung nghiệp vụ chính thường 14–16px; tiêu đề khu vực/card thường 16–20px tùy diện tích; thông tin phụ đủ đọc 12–13px; KPI/tiền 26–34px tùy card.
- Nhấn **đúng chữ số/giá trị và nội dung quan trọng**, không tăng cả card từ 12px lên 18px đồng loạt như lỗi U5 trước đó.
- `line-height` thường 1.45–1.75; kiểm tra dấu thanh, wrapping, chiều cao card và các nhãn dài. Số tiền dùng `font-variant-numeric:tabular-nums`; không bóp hoặc cắt số.
- Spacing nhịp 8/12/16/20/24px, căn nhóm bằng gap/padding; có thể điều chỉnh theo card và screenshot được duyệt; tránh line chia vô nghĩa.
- Không được tự ý thay footer, sidebar sticky, khoảng trắng trang, minh họa, bố cục, ảnh hoặc font style ngoài khu vực được Owner yêu cầu.

### 1.4 Thành phần nghiệp vụ

- U2 On-chain: PLAN=Mint, RECORD=Apricot, REVIEW=Sky; DEMO/RECORD nền kem `#FFF6E8`, **không dùng hồng đất `#F1D8CC`**. Giữ hai luồng off-chain / Fuji có nhãn nguồn.
- U3 Donor: Cartoon Pastel V2 Strong Outline, ba tab Đóng góp / Hoàn tiền / Phản ánh; **chỉ đồng bộ font** sau khi khóa giao diện; không tự thay đổi card, bố cục, minh họa.
- U4 Transparency: Cartoon Pastel Transparency V1.1, màu phản ánh nhóm thông tin; giải thích nguồn và trạng thái rõ ràng.
- U5 Escrow: **V1.2 Typography Refined, không V1.3**; chỉ nhấn mạnh tiền/KPI/nội dung cần đọc mà không làm xuống dòng/tràn chữ. 6 khu vực đã chốt về bố cục.
- U6 Monitoring: **V1.2.2 + Refined Rule Card**. Card Hiệu quả quy tắc giữ **layout ngang**: Quy tắc / Tỷ lệ / Kết quả đã kết luận / Chưa đánh giá; bốn vùng bo góc, nhóm xác nhận/không đủ căn cứ rõ ràng; khi mẫu số = 0 hiển thị `—` thay vì 0%. AI chỉ hỗ trợ Admin đánh giá, không kết luận sai phạm.

## 2. Sổ U1–U6 và trạng thái bàn giao

| ID | Màn hình / route | Design decision đã chốt | File cuối đã xác minh? | Re-freeze Owner |
|---|---|---|---|---|
| U1 | Campaign Detail `/campaigns/:id` | B2 full-page, 3 card Story, Milestones, sidebar progress, Hero/Footer khóa | Có ảnh `U1-Campaign-Detail-APPROVED-CONCEPT.png`, **chưa xác minh HTML cuối tương ứng** | **CHỜ SOÁT FILE** |
| U2 | On-chain Timeline trong Campaign Detail | PLAN / RECORD / REVIEW; đồng bộ Milestones B2, giữ layout | Có lịch sử/patch tham chiếu, **chưa xác minh HTML full-page cuối** | **CHỜ SOÁT FILE** |
| U3 | Donor Dashboard / Receipts | Cartoon Pastel V2 Strong Outline, 3 tab, chỉ typography Be Vietnam Pro | PDF có preview tĩnh, **chưa có HTML cuối được xác minh** | **CHỜ SOÁT FILE** |
| U4 | Transparency `/transparency` | Cartoon Pastel V1.1 | Có screenshot từng vùng, **chưa xác minh master full-page** | **CHỜ SOÁT FILE** |
| U5 | Admin Escrow `/admin/escrow` | V1.2 Typography Refined, không V1.3 | Có decision log, **chưa có HTML cuối được xác minh** | **CHỜ SOÁT FILE** |
| U6 | Admin Monitoring `/admin/monitoring` | V1.2.2 + Refined Rule Card | Có đúng HTML/PNG **card Rule**, **chưa có full-page master cuối** | **CHỜ SOÁT FILE** |

**CHỜ SOÁT FILE không có nghĩa mất quyền chốt thiết kế cũ.** Chỉ thể hiện cần tìm lại/đóng băng chính xác đầu ra sau chỉnh sửa, trước khi triển khai.

## 3. Quy trình đóng băng U theo thứ tự

1. **Inspect:** Đọc quyết định và asset đã duyệt, mở source HTML/React hiện tại; nêu chênh lệch. Không tự vẽ lại nếu không được yêu cầu.
2. **Review:** Cho Owner xem một ảnh **full-page 1440px** và crop trọng điểm với phiên bản, đường dẫn, nguồn; phân biệt concept/fixture/React thật.
3. **Approve:** Owner xác nhận rõ `CHỐT Ux — tên version` trên chính source này. Nếu chưa đúng, thay đúng mục bị yêu cầu và cho xem lại.
4. **Archive:** `docs/design/approved/u1-u6-20261119/Ux/` chứa `index.html` hoặc source manifest đúng bản cuối, `fullpage-1440.png`, `decision.md`, `sha256.txt`, `qa-observations.md`. Đánh số phiên bản, ghi nguồn và các giới hạn.
5. **Commit:** Gom U1–U6 + design system vào một **gói commit có kiểm soát**, không commit nhỏ lẻ, không thêm branch QA. Giữ PR Draft và yêu cầu reviewer độc lập trước merge. Nếu phải commit tài liệu khởi tạo, commit đó không được tự đánh dấu U1–U6 `FINAL SOURCE VERIFIED`.
6. **Build React:** Chỉ sau khi bản bàn giao được xác minh. Dùng nhánh triển khai được kiểm tra trạng thái, chỉ sửa phần trong scope, đúng API; QA thực bằng Chromium 1440×900 DPR1 với ảnh + log + direct/SPA, loading/error/empty; không tự đánh dấu PASS.

## 4. Quy chuẩn chống quên và chống lẫn phiên bản

- Không đặt nhãn `FINAL APPROVED` cho ảnh cũ, mockup tái tạo hoặc ảnh AI sinh mà chưa có xác nhận của Owner.
- Với U5: **cấm nâng lên V1.3** khi tạo source cuối; typography-only refactor.
- Với U6: **cấm đổi Rule Card ngang thành dọc hoặc kẻ bảng vuông**, giữ đúng HTML Rule Card đã chốt làm source khi tích hợp vào toàn trang.
- Mỗi bản chốt cần **SHA-256**, commit SHA, đường dẫn file repo, screenshot full-page. Không xem screenshot tràn hoặc ảnh crop là full-page.
- Không tạo thêm branch QA, không force/reset/clean/stash trước khi kiểm tra working tree; không merge khi chưa reviewer.
- Scope hiện tại **desktop 1440**, không bổ sung thiết kế mobile.

## 5. Nguồn đối chiếu

- PDF hội thoại: `ChatGPT-Khoảng trắng dưới footer-20261119-2224.pdf` (lịch sử đến U4 V1).
- Owner decisions U4 V1.1 / U5 V1.2 / U6 V1.2.2: cuộc trò chuyện Rise AI hiện tại ngày 19/11/2026.
- `U1-Campaign-Detail-APPROVED-CONCEPT.png` được lưu trong bộ nguồn xác minh; **ảnh concept, không phải bằng chứng HTML final.**
- `rise_ai_u6_v122_rule_card_refined.html` và screenshot PNG: nguồn chính cho card Rule cuối của U6.
- Runtime tokens hiện tại: `rise-ai-frontend/src/styles/tokens.css`; guardrail hiện có: `docs/design/UI_GUARDRAILS.md`.

> **Source authority:** Owner approval for design intent > matching approved HTML/source > matching screenshot evidence > previous React screenshot / draft / reconstruction. Mọi thiếu hụt phải được ghi nhận thay vì đoán.