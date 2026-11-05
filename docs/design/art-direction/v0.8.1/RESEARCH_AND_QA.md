# B v0.8.2 · Research and Chromium QA

**PASS — TWO VARIANTS READY FOR OWNER VISUAL COMPARISON**
Nghiên cứu bố cục Desktop 1440px ngày 2026-11-06. Hai biến thể đều PENDING_APPROVAL. Lựa chọn và ghi chú cho chủ dự án nằm trong [scorecard](./COMPARISON_SCORECARD.md).

## Nguồn và giới hạn thay đổi

- Assignment head PR #11 `3141016a5c6b23f0a9a773e42942dc6b230e9363`. Baseline [v0.7.1b](../../reviews/v0.7.1/homepage-visual-rhythm-v0.7.1b-semantics.html), Git blob `3e9865d591e46c8f06390ea1bb67909f0742041a`, SHA-256 Git LF `277bba9979998d4a931fdb5b6b321fa09785c74d77f21bc4b26c63e3cd9167d1`. SHA-256 checkout CRLF được render là `5cff24a109235b1a07bc821f45f3774c0d7c232ad500f2e14ed60057dad6de7a`.
- SHA-256 của B1 `fcf9bac7f0c2cf7a61ad75bd1559aec62e1b6e33656ab03ec793c54eb630063c`, B2 `84314e4deb1ced871b5a437dd972cd7fb3365d48108ff412190a5c77faf8e68b`. B1/B2 sinh trực tiếp từ HTML baseline. Sau khi thay SVG Hero bằng cùng một mốc so sánh trong bản sao DOM, hash của toàn bộ phần body giống nhau ở cả ba bản. CSS và SVG Hero là hai phần nghiên cứu.
- Master v0.7.0 blob `454a752d0c58228ec671b9b2376b842f01475ad3`. Runtime `tokens.css` blob `a532dc943d32029830d8207bb645518027642dc4`. Git diff chỉ thêm năm file trong thư mục nghiên cứu. Hai ứng viên là HTML tĩnh tự chứa CSS và SVG, dùng font cài sẵn tại máy xem.
- Seed giữ nguyên cả sáu giá trị VND, các phần trăm, lời chú thích mô phỏng và nhãn placeholder. Progress huy động khác timeline mốc và hồ sơ xét duyệt. Không có ảnh tư liệu được cấp quyền trong nguồn. Xem quy tắc provenance trong scorecard.

## Môi trường và kết quả

Windows, Playwright, Chromium headless **148.0.7778.96**, file HTML cục bộ, **1440 × 900, DPR 1**, offline. So sánh bản chụp riêng ở đúng 1440 px, sau đó mới thu nhỏ và ghép ảnh.

| Kiểm tra | Baseline | B1 | B2 |
|---|---:|---:|---:|
| scrollWidth | 1440 | 1440 | 1440 |
| scrollHeight khi đóng | 6166 | 6164 | 6164 |
| JS exceptions / HTTP(S) attempts | 0 / 0 | 0 / 0 | 0 / 0 |
| Lưới danh mục | 4 × 2 | 4 × 2 | 4 × 2 |
| Thẻ chính, khung (px) | 932 × 452 | 932 × 452 | 932 × 452 |
| Compact, khung (px) | 612 × 457.375 | 612 × 457.375 | 612 × 457.375 |
| Compact, hàng ảnh và hàng tiền | 276 px / 610 px rộng | 276 px / 610 px rộng | 276 px / 610 px rộng |
| Footer | 1440 px, 56/56 px | 1440 px, 56/56 px | 1440 px, 56/56 px |

Grid track khai báo 37.5/62.5 và 38/62 ở cả ba bản. Tỷ lệ ảnh đo bằng offsetWidth làm tròn đến pixel là 37.53% và 38.03%. Chiều cao trang giảm khoảng 2 px do các dòng chữ và quy tắc thị giác, kích thước ba khung thẻ vẫn khớp baseline. Tất cả section và tám mã lĩnh vực giữ nguyên thứ tự.

- **Trợ năng:** một DOM main, một AX main, một Hero h1, Story và Transparency h2, không trùng ID, đích aria-controls hợp lệ. Enter mở panel và chuyển `aria-expanded/aria-hidden` thành true/false. Chromium AX chứa “Trạng thái minh chứng”. Space đóng, trở lại false/true và loại nội dung khỏi AX tree. Tám tile đều có nhãn, focus 3 px và khi chọn bằng bàn phím tên được gạch dưới với `aria-pressed=true`.
- **Typography:** 12 ca stress mỗi bản. Tiêu đề thẻ chính và compact lần lượt thử 1, 2, 3 dòng. Story và Transparency có mẫu ngắn, vừa, dài. Các mẫu vừa khung ở cỡ chữ cố định và không clamp. Chromium vẽ đúng chuỗi dấu “ắ ằ ẵ ặ ễ ộ ờ ữ” và chuỗi VND, với dữ liệu font glyph bên dưới.
- **Phân biệt hình:** ảnh xám đã bỏ nhãn cho thấy B1 có tấm giấy sáng, chữ số xếp bậc và đường mảnh. B2 có nền mực đậm, trạm hình học và tuyến gấp. Ảnh thẻ còn cho thấy khung mở và vạch phân cách của B1 đối lập với khung kín, dải lĩnh vực và nền tài chính của B2. Các hình minh họa trong HTML đều mang nhãn artwork.

### Font tải thật · PENDING_APPROVAL

Năm file TTF từ [thư mục Be Vietnam Pro của Google Fonts](https://github.com/google/fonts/tree/main/ofl/bevietnampro) được nạp bằng `FontFace` từ bytes cục bộ trong harness. Chrome DevTools `CSS.getPlatformFontsForNode` báo `isCustomFont=true` và họ Be Vietnam Pro/Be Vietnam Pro ExtraBold ở cả bảy vùng mẫu, cộng thêm chuỗi stress dấu và VND. Không thấy fallback trong các mẫu đó. Các tên weight là Regular 400, Medium 500, SemiBold 600, Bold 700 và ExtraBold 800. SHA-256 của từng file có trong `browser-evidence.json`. Git không chứa file font. Khi tự mở HTML, cài font cục bộ để xem đúng metric. Nếu thiếu font, HTML dùng Segoe UI hoặc sans-serif. Ảnh trong trang đối chiếu đã chụp với font thật.

### Ma trận tương phản ứng viên · PENDING_APPROVAL

Đo theo relative luminance sRGB. 56 cặp mỗi bản đạt ngưỡng đã gán, lấy từ màu computed tại các vùng mẫu và cặp trạng thái. Ngưỡng 4.5:1 cho chữ thường, 3:1 cho chữ lớn và dấu UI thiết yếu. Đây là QA mẫu, phạm vi hẹp hơn đánh giá WCAG toàn trang. [W3C WCAG 2.2 Non-text Contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)

| Cặp mẫu | Màu foreground / background | Tỷ lệ |
|---|---|---:|
| Hero headline | `#163C32` / `#F4EFE2` | 10.59 |
| Hero body | `#43594D` / `#F4EFE2` | 6.59 |
| Hero CTA | trắng / màu xanh computed `#173F31` | 11.72 |
| Chữ danh mục hover | `#243028` / `#F4EFE2` | 11.97 |
| Viền selected | `#163C32` / `#F4EFE2` | 10.59 |
| Focus trên trắng / kem | `#A93E2C` / `#FFFFFF` hoặc `#F4EFE2` | 6.15 / 5.36 |
| Focus trắng trên footer tối | `#FFFFFF` / `#163C32` | 12.16 |
| Thanh huy động | `#163C32` / `#D3DDD5` | 8.73 |
| Viền control trên trắng | `#68786E` / `#FFFFFF` | 4.67 |
| Nhãn mốc hiện tại | `#FFFFFF` / `#A93E2C` | 6.15 |
| Nút disclosure hover | `#FFFFFF` / `#163C32` | 12.16 |

| Lĩnh vực | Màu vạch | Trắng, mặc định | Kem, hover và selected |
|---|---|---:|---:|
| Giáo dục | `#386AB0` | 5.45 | 4.74 |
| Cộng đồng | `#2C8064` | 4.80 | 4.18 |
| Lương thực | `#BE692E` | 4.00 | 3.48 |
| Nước sạch | `#22849A` | 4.34 | 3.78 |
| Học bổng | `#8063AB` | 4.89 | 4.26 |
| Cơ sở vật chất | `#78634C` | 5.69 | 4.96 |
| Y tế | `#AE505A` | 5.13 | 4.47 |
| Khác | `#69716B` | 5.03 | 4.38 |

## Bằng chứng và lệnh chạy lại

Mở [trang so sánh](./B1_VS_B2_REVIEW.html) để xem ảnh thu nhỏ và hai ứng viên 1440px. Bằng chứng đầy đủ trên máy kiểm thử nằm dưới `outputs/riser-v082-research/` trong workspace.

- `B1-closed.png`, `B2-closed.png`, `B1-expanded.png`, `B2-expanded.png` và `base-closed.png` là ảnh toàn trang Chromium. `overview.png` là ảnh của trang so sánh.
- `compare-full.png`, `compare-blind-grayscale.png` và các ảnh `compare-{hero,categoriesDiscovery,cards,story,milestones,transparency,footer,transparency-expanded}.png` ghép baseline, B1 và B2, trừ bản xám chỉ có B1/B2.
- `compare-type-stress.png`, `compare-diacritics.png`, `browser-evidence.json`, `contrast-evidence.json` chứa bằng chứng typography, số đo, cây trợ năng phần disclosure và bảng tương phản đầy đủ.
- Scripts cục bộ trong thư mục bằng chứng. Trỏ `REPO` tới worktree, `EVIDENCE` tới thư mục bằng chứng, `RISER_CHROMIUM` tới Chromium 148, và `RISER_PLAYWRIGHT` tới module Playwright nếu cần. Đặt năm file TTF kể trên vào thư mục cục bộ `private-fonts` trước khi chạy.

```powershell
node "$env:EVIDENCE/chromium-qa.cjs" "$env:REPO" "$env:EVIDENCE"
python "$env:EVIDENCE/compare.py" "$env:EVIDENCE"
python "$env:EVIDENCE/overview.py" "$env:REPO" "$env:EVIDENCE"
node "$env:EVIDENCE/overview-qa.cjs" "$env:REPO" "$env:EVIDENCE"
```

Phạm vi verdict là nghiên cứu thị giác Desktop và các kiểm tra HTML tĩnh nêu trên. Font, bảng màu, điểm nhấn danh mục và chính sách ảnh vẫn chờ chủ dự án chọn.
