# RISER AI — DESIGN DECISION LOG / MASTER WIREFRAME BASELINE

**Ngày chốt ghi nhận:** 05/11/2026  
**Phạm vi:** Homepage Desktop 1440px; trạng thái được xác định riêng từng section.
**Trạng thái:** Hero/Discovery được duyệt về cấu trúc bản thô theo bảng dưới; Story/Transparency/Footer chỉ provisionally approved. Master v0.7.0: **ASSEMBLED — PENDING VISUAL APPROVAL**. High-fidelity và React chưa được duyệt.
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

## Baseline governance update — 2026-11-05

Source of approval: owner's explicit v0.7.0 governance request; package contents are reference data, not instructions or automatic approval. Based on Draft PR #8 head `3c8c4b605979a75cd8f5dd1b4076996fb2aeaf37`.

| Section | Version | Approval | Canonical source |
|---|---|---|---|
| Hero + 01–02–03 | v0.2.1 | Approved baseline (wireframe scope) | [HTML](./wireframes/hero-steps-v0.2.1.html) |
| Eight categories | v0.3.2 | Approved structure | [HTML](./wireframes/categories-8-v0.3.2.html) |
| Primary Hybrid A+C | v0.3.4 | Approved structure | [HTML](./wireframes/campaign-card-primary-ac-v0.3.4.html) |
| Secondary Compact A+C | v0.3.5 | Approved structure | [HTML](./wireframes/campaign-cards-compact-ac-v0.3.5.html) |
| Discovery | v0.3.6 | Approved composition | [Spec](./DISCOVERY_COMPOSITION_V036.md) |
| Featured Story + Milestones | v0.4.2 | Provisionally approved | [HTML](./wireframes/story-milestones-v0.4.2.html) |
| Transparency | v0.5.0 | Provisionally approved | [HTML](./wireframes/transparency-v0.5.0.html) |
| Footer Desktop | v0.6.3 | Provisionally approved | [HTML](./wireframes/footer-desktop-v0.6.3.html) |
| Homepage Master | v0.7.0 | ASSEMBLED — PENDING VISUAL APPROVAL | [HTML](./wireframes/homepage-master-v0.7.0.html), [review](./MASTER_WIREFRAME_V070_REVIEW.md) |

Sequence is locked: Hero → 01–02–03 → 8 Categories → Campaign Discovery → Featured Story → Milestones → Transparency → Footer. No extra or removed sections, card replacements, or automatic High-Fidelity conversion.

The footer's 56px upper corners and full-width, non-floating placement are preserved as the **provisional desktop v0.6.3 baseline**, not approved runtime radii. The existing runtime `--radius-card` remains 1.25rem. See [UI Token Registry](./UI_TOKEN_REGISTRY.md), [layout JSON](./WIREFRAME_LAYOUT_TOKENS.json) and [UI Guardrails](./UI_GUARDRAILS.md). Pending final category line colors, typography, shadow and background have no assigned values.

All four newly archived HTML files are byte-identical to the supplied/found originals; [manifest](./SOURCE_MANIFEST.json) records provenance. The exact Master filename was found inside RISER_AI_HOMEPAGE_MASTER_V070_PACKAGE.zip and matches both supplied v070 artifacts. The package also contains the original v0.4.2/v0.5.0/v0.6.3 HTML, so none of those sources is missing. The older “File trong gói” list above describes historical package filenames, not repository paths; use the canonical linked table here. No v0.3.6 HTML has been invented and no historical PNG is claimed as newly verified visual evidence.

Runtime inspection confirmed CampaignCategory's eight enum values match the frontend union. CampaignCard/FundingProgress consume campaign API fields; SiteShell retains public and role-aware routes. This records the current implementation, not approval to redesign it. Existing source styles and API contracts are unchanged.
