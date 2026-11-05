# RISER AI — DESIGN DECISION LOG / MASTER WIREFRAME BASELINE

**Ngày chốt ghi nhận:** 05/11/2026  
**Phạm vi:** Homepage Desktop 1440px — Header/Hero + 01–02–03 + Campaign Discovery  
**Trạng thái:** APPROVED về **cấu trúc bản thô**; NOT APPROVED về high-fidelity, tài sản hình ảnh, hình thức card, giao diện React.  
**Nguồn:** Các quyết định đã duyệt trong hội thoại và các bản HTML gốc. Lưu vào repository bằng PR tài liệu ngày 05/11/2026.

## Danh sách quyết định đang có hiệu lực

| Phần | Nguồn chuẩn | Quyết định đã thống nhất |
|---|---|---|
| Hero + dải hành trình 01–02–03 | v0.2.1 | Giữ hình thức đã chọn ở v0.2.1, không lấy v0.2.2/v0.2.3. |
| Phân loại chiến dịch | v0.3.2 | 8 danh mục **cấp 1** theo mã Backend, bố trí 2 hàng × 4; line nhận diện riêng từng chủ đề. |
| Card chính | v0.3.4 A+C | Hình ảnh ~37,5% trái; nội dung + tài chính ~62,5% phải; tiến độ rõ, gắn nhãn mô phỏng. |
| Hai card phụ | v0.3.5 Compact A+C | Hai card đặt cạnh nhau; vùng trên ảnh trái ~38%, thông tin phải ~62%; vùng tiền và progress bar chạy toàn bề ngang bên dưới. |
| Bố cục hợp nhất | v0.3.6 (ảnh minh họa) | Danh mục trên, bên dưới là phần dẫn trái + card chính phải + 2 card phụ bên dưới. Chốt **sơ đồ tổ chức**; không coi ảnh AI là source hay high-fidelity được duyệt. |

## 8 danh mục chính thức — API / Backend

1. EDUCATION — Giáo dục
2. COMMUNITY — Cộng đồng
3. FOOD_SECURITY — Lương thực
4. WATER_SANITATION — Nước sạch
5. SCHOLARSHIP — Học bổng
6. INFRASTRUCTURE — Cơ sở vật chất
7. HEALTH — Y tế
8. OTHER — Khác

Hệ thống không có trường `subcategory` được xác nhận trong frontend contract. Các mô tả như “Bữa ăn định kỳ”, “Thư viện cộng đồng”, “Thiết bị học tập” là nhãn nội dung đề xuất, **không phải mã danh mục con độc lập**. Màu line chưa là token sản phẩm cuối.

## Nội dung card đã chốt ở mức thông tin

- Ảnh bìa / fallback có nhãn rõ
- Danh mục, tên và mô tả ngắn
- Đơn vị thực hiện, đối tượng thụ hưởng (chỉ khi backend trả dữ liệu)
- Trạng thái chiến dịch
- Đã đóng góp ròng (`funding.netRaised`), mục tiêu (`targetAmount`), % hoặc thanh tiến độ
- Link xem hồ sơ chiến dịch
- Chú thích **tài chính mô phỏng**; không nhầm tiến độ huy động với tiến độ milestone
- Khi không có dữ liệu hợp lệ, biểu diễn 'Chưa có dữ liệu', không đặt 0% giả.

## Dữ liệu DEMO tham khảo (kịch bản seed, không phải API live)

| Campaign | Backend category | Đã đóng góp ròng* | Mục tiêu |
|---|---|---:|---:|
| Hope Kitchen Weekly Meals (Demo) | FOOD_SECURITY | 22.000.000 VND | 60.000.000 VND |
| Riverside Community Library (Demo) | COMMUNITY | 45.000.000 VND | 80.000.000 VND |
| Laptops and Learning Kits for Highland Pupils (Demo) | EDUCATION | 75.000.000 VND | 120.000.000 VND |

\* Số liệu seed giả định được tính sau nghiệp vụ, phải kiểm tra lại `funding.netRaised` từ API khi tích hợp. Tên/địa chỉ/tổ chức trên **ảnh v0.3.6 do AI sinh không được dùng làm nguồn dữ liệu**, có thể sai. Theo seed: Hope Kitchen Network (Demo), Green Village Community Group (Demo), Sunrise Learning Fund (Demo) tương ứng với ba chiến dịch trên.

## Phần CHƯA được phê duyệt

- Bo góc, shadow, kiểu viền, accent color hex cuối cùng, typography, artwork, layout mobile.
- Bản High-Fidelity toàn trang và React implementation.
- Không chấp nhận ảnh v0.3.6 làm source thiết kế trực tiếp, vì đây là ảnh tĩnh do AI dựng; nó chưa có HTML/source component và có thể tự bịa chi tiết trình diễn.
- Chỉ lưu trữ trên branch tài liệu và mở PR; không merge/deploy hoặc sửa worktree DB-06, MEDIA-01.

## File trong gói

- `01_hero_steps_v021.html` — Hero + 01–02–03 theo bản đã giữ.
- `02_categories_8_v032.html` và `.png` — tám lĩnh vực, 2×4.
- `03_primary_card_AplusC_v034.html` và `.png` — cấu trúc card lớn được duyệt.
- `04_compact_cards_AplusC_v035.html` và `.png` — hai card nhỏ được duyệt.
- `05_combined_composition_v036_IMAGE_REFERENCE_ONLY.png` — ảnh tham chiếu bố cục kết hợp, không phải source of truth.

## Cách tránh thất lạc

Bản HTML chuẩn và nhật ký quyết định đã được đưa lên nhánh `docs/design-wireframe-baseline-20261105` qua PR tài liệu. PNG minh họa và ảnh hợp nhất v0.3.6 vẫn nằm trong gói tải trên hội thoại, chưa có tệp ảnh được đẩy lên GitHub. Figma chưa được cập nhật.

**Quy tắc cho phiên tiếp theo:** Luôn mở log này trước; KHÔNG tự thiết kế lại các phần đã được duyệt. Mọi sửa đổi phải ghi rõ version, scope và ảnh hưởng.