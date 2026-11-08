# RISER AI — COMMUNITY SIGNATURE
## B v0.8.1 | Visual Grammar & Component Art Direction Specification

**Ngôn ngữ:** Tiếng Việt | **Ngày:** 06/11/2026  
**Trạng thái:** B — Community Signature đã được chủ dự án **chọn làm HƯỚNG nhận diện**. Tài liệu này là **đặc tả để nghiên cứu và review**, không phải phê duyệt màu, font, High-Fidelity hay React.  
**Nguồn cấu trúc bắt buộc:** Homepage Composition Baseline v0.7.1b, Desktop 1440px; xem `docs/design/COMPOSITION_BASELINE_V071B.md`.  
**Quy tắc:** Không thay thế source v0.7.1b bằng ảnh AI hoặc style tile. Không tự thiết kế lại bố cục được chốt.

---

## 1. B là gì?

**Một phong trào cộng đồng có dấu ấn thị giác riêng, rõ ràng và đáng tin.** Người đến trang cần cảm nhận ba điều theo thứ tự: **có cộng đồng cùng hành động → có chiến dịch thực tế cần theo dõi → có cách xem tiến độ và đối chiếu thông tin**.

B có ba trụ cột:

1. **Community energy:** bố cục có lực, chữ tự tin, tương phản rõ; tạo động lực tham gia mà không phô trương.
2. **Connected moments:** sử dụng đường và điểm nối có *ý nghĩa ngữ cảnh*: các bước 01–02–03, cột mốc thực hiện, chuyển từ câu chuyện sang cách kiểm tra.
3. **Accountable storytelling:** thông tin về huy động, tiến độ, minh chứng và xét duyệt nội bộ không bị trộn lẫn. Cảm xúc không thay thế bằng chứng.

**Câu hỏi định hướng:** “Một người có thể tham gia điều gì, và họ sẽ biết chiến dịch đang tiến triển ra sao?” Không phải “website có nhiều card đẹp hay không?”.

**Dấu hiệu nhận biết:** kể cả bỏ logo và tên “Community Signature”, người xem vẫn nhận ra tính cộng đồng nhờ nhịp chữ, đường kết nối chức năng, thẻ chiến dịch có cấu trúc đặc trưng, cùng hệ dấu hiệu trạng thái nhất quán.

## 2. B không được là gì?

- Không phải **generic AI green SaaS**: nền xanh mint ở mọi section, card trắng bo 20–32px hàng loạt, shadow mờ, icon đồng dạng, gradient vô lý.
- Không phải **landing page công nghệ**: mạng chấm/nút kết nối giả lập, dashboard KPI khổng lồ, biểu đồ không có dữ liệu thật, chữ “AI” phô diễn.
- Không phải **trang từ thiện dùng ảnh stock không nguồn**: không tạo ảnh giả người thụ hưởng rồi giới thiệu như hoạt động thực tế; không dùng ảnh AI làm minh chứng dự án.
- Không phải **fintech/bảng quản trị**: giao dịch mô phỏng không phải giao dịch thật, admin review không phải kiểm chứng độc lập.
- Không phải giao diện chỉ khác concept A/C bằng **một mã màu hoặc font**.
- Không thêm section, thay kiểu card, nén chữ để vừa chiều cao cố định, mở rộng sang mobile, sửa React/Backend/deployment.

## 3. Hệ ngữ pháp thị giác cốt lõi

**Cặp nhận diện chính (đang nghiên cứu):** *Bold Type + Meaningful Path*.

- **Bold Type:** mỗi section có một điểm nhấn chữ thật sự rõ: tiêu đề lớn hoặc con số/mốc có ngữ cảnh; phần phụ phải lùi lại. Chỉ một mức nhấn chính cho một vùng thị giác.
- **Meaningful Path:** nét nối biểu thị **trình tự, quan hệ hoặc trạng thái**. Điểm nút tương ứng với bước thực sự; không sinh node ngẫu nhiên chỉ để trông “công nghệ”.
- **Campaign Identity:** card có một dải nhận diện gắn với danh mục và trạng thái, ảnh/placeholder rõ, tiêu đề chiến dịch là chủ thể. Tiến độ huy động được thể hiện khác trục mốc thực hiện.
- **Evidence Labels:** trạng thái luôn đi cùng nhãn bằng chữ. Hình dạng, vị trí, màu bổ trợ việc đọc; không dùng riêng màu để nói “Đã duyệt”.
- **Whitespace & Division:** tạo nhịp bằng phân cấp chữ, khoảng thở và đường phân cách có chức năng; không tô nền một màu khác cho mọi block.

Bắt buộc phân biệt **path mang thông tin** với **progress bar huy động tiền**. Path không được làm người xem hiểu rằng nhận tiền đồng nghĩa hoàn thành milestone.

## 4. Hero của B trông ra sao?

**Giữ composition Hero v0.2.1** đã duyệt và dải 01–02–03 ngay sau Hero.

- **Tầng 1 — thương hiệu / lời mời:** headline tiếng Việt sans đậm, gọn và có nhịp 2–3 dòng theo khung đã có; trọng tâm là *cộng đồng tạo tác động có thể theo dõi*, không dùng khẩu hiệu khẳng định kết quả đã xác minh.
- **Tầng 2 — giải thích:** một đoạn copy ngắn, dễ đọc, cho biết nền tảng giúp khám phá chiến dịch và theo dõi minh bạch; không cạnh tranh thị giác với headline.
- **Tầng 3 — hành động:** một CTA chính rất dễ nhận diện (ví dụ “Khám phá chiến dịch”), một đường dẫn phụ (ví dụ “Xem cách hoạt động”). CTA có trạng thái focus rõ; không tạo nút giả thanh toán.
- **Vùng visual giữ vị trí đã chốt:** dùng đồ họa **Rise Path** hoặc ảnh có nguồn gốc hợp lệ; dấu nối thể hiện các bước tham gia đã có, không bịa số người, lượt đóng góp hay xác nhận xã hội.
- **Dải 01–02–03:** tiếp tục ba bước của baseline, đánh số và chữ nổi bật, nối bằng một trục dễ đọc. Không sử dụng các số 01–03 đó để trang trí lặp lại vô nghĩa ở Story.

**Mục tiêu của Hero:** khi nhìn 3 giây, thấy *một nền tảng cộng đồng* và một hành động chính. Không phải dashboard, trình chiếu KPI hay bộ card thương mại.

## 5. Campaign Card của B — quy tắc ở cấp thành phần

### 5.1 Card chính — Hybrid A+C

**Bất biến:** ảnh trái **37,5%**, nội dung phải **62,5%** (Desktop 1440px).

**Thứ bậc phải dễ quét:** ảnh/placeholder có nhãn → danh mục và trạng thái → tiêu đề chiến dịch → mô tả ngắn → đơn vị thực hiện (nếu dữ liệu API hợp lệ) → **đã đóng góp ròng mô phỏng / mục tiêu / tiến độ huy động** → liên kết xem chi tiết.

**Điểm đặc trưng B:** đường danh mục ngắn và có điểm kết thúc rõ, nhịp typography mạnh giữa tiêu đề–số liệu, và một đường phân định “câu chuyện chiến dịch” với “tình hình huy động”. Dấu nối không được chồng lên ảnh, progress bar hoặc CTA.

**Khi thiếu dữ liệu:** hiện “Chưa có dữ liệu” hoặc trạng thái phù hợp từ API; không thay null bằng 0%, không tự dựng lời chứng thực, avatar người ủng hộ, số dự án đã hoàn thành.

**Hình ảnh:** ưu tiên ảnh có quyền sử dụng và nguồn được ghi nhận. Nếu không có: khung giữ tỷ lệ, tên lĩnh vực và ký hiệu hình học trừu tượng; phải cho biết đây là placeholder, không giả ảnh dự án.

### 5.2 Hai card Compact A+C

**Bất biến:** hai card cạnh nhau; hàng trên ảnh trái **xấp xỉ 38%**, nội dung phải **xấp xỉ 62%**; hàng tài chính và progress bar **chiếm toàn chiều ngang phía dưới**.

Rút gọn visual signature: chỉ dùng một dải danh mục nhỏ và một điểm nhấn tiêu đề, không sao chép tất cả hoa văn của card chính. Đảm bảo tiêu đề dài 1–3 dòng vẫn đọc được, số tiền không bị cắt và toàn card không thành nút trang trí vô nghĩa.

### 5.3 Khác biệt bắt buộc so với “AI card”

Card phải thể hiện rõ **lĩnh vực, người thực hiện khi có dữ liệu, nhu cầu, tình trạng huy động và đường dẫn tìm hiểu**. Không có floating-glass card, shadow đồng loạt, icon ngẫu nhiên, nhãn “verified” tự đặt hay progress bar màu đẹp nhưng không có nguồn số.

## 6. Tám Category — một hệ nhận diện, không phải tám template

**Bất biến:** 8 danh mục Backend **cấp 1**, bố cục **2 hàng × 4 cột**. Mỗi tile gồm **số thứ tự 01–08 + tên + mô tả ngắn + một đường màu nhận diện**, luôn có nhãn chữ.

| Số | Backend code | Nhãn chính | Mã màu đang có trong HTML tham chiếu — **CHƯA CHỐT** |
|---|---|---|---|
| 01 | EDUCATION | Giáo dục | `#386AB0` |
| 02 | COMMUNITY | Cộng đồng | `#2C8064` |
| 03 | FOOD_SECURITY | Lương thực | `#BE692E` |
| 04 | WATER_SANITATION | Nước sạch | `#22849A` |
| 05 | SCHOLARSHIP | Học bổng | `#8063AB` |
| 06 | INFRASTRUCTURE | Cơ sở vật chất | `#78634C` |
| 07 | HEALTH | Y tế | `#AE505A` |
| 08 | OTHER | Khác | `#69716B` |

Các HEX trên chỉ là **giá trị đang xuất hiện trong nguồn HTML bản thô**, để làm đầu vào thử nghiệm; **chưa là token cuối**. Khi thử nghiệm phải kiểm tra độ phân biệt, tương phản, trạng thái hover/focus/selected và trường hợp không nhận biết màu. Giữ line + số + nhãn; không thêm “subcategory” vì chưa có trong contract.

**Cách dùng đúng:** màu line như một dấu chỉ hướng, không phủ nền màu toàn tile; active có trạng thái chữ/viền được nhận biết rõ chứ không chỉ đổi màu.

## 7. Line / Path / Connection — dùng ở đâu?

| Khu vực | Được dùng để làm gì | Không được làm gì |
|---|---|---|
| Hero / 01–02–03 | Nối ba bước đồng hành theo thứ tự | Vẽ mạng nút không tương ứng bước thực |
| 8 Categories | Dải line định danh từng lĩnh vực | Dùng màu làm tín hiệu duy nhất |
| Discovery / Card | Gắn danh mục, điều hướng tới câu chuyện | Biến line thành progress bar huy động giả |
| Story | Nối logic vấn đề → giải pháp → thực hiện | Lặp lại số 01–03 để trang trí |
| Milestones | Nối các mốc có trạng thái rõ bằng chữ | Coi đã huy động tiền = mốc đã hoàn thành |
| Transparency | Nối kế hoạch → gửi minh chứng → xét duyệt nội bộ | Biểu thị internal review như kiểm chứng độc lập |
| Footer | Một điểm kết tinh nhận diện nếu thực sự cần | Rải network wallpaper khắp nền |

**Quy tắc tiết chế:** mỗi khu vực tối đa một hệ line mang ý nghĩa chính, không vừa timeline vừa đường chạy vòng vừa decor trùng lặp. Nút, đoạn nối và đường kết thúc phải tương ứng thông tin cụ thể; không có dữ liệu thì không có “đường tiến độ” giả.

## 8. Palette — hướng xanh đậm, cam, tương phản mạnh

**Ứng viên nghiên cứu từ v0.8.0, KHÔNG phải màu được duyệt:**

| Vai trò | Giá trị nghiên cứu | Ý định |
|---|---|---|
| Brand ink / CTA đậm | `#163C32` | Đậm, vững, tin cậy |
| Warm canvas | `#F4EFE2` | Nền ấm, không pastel nhạt tràn lan |
| Energetic accent | `#B24731` | Điểm nhấn hành động/đồ họa |
| Muted body text | `#4E6058` | Phân cấp phụ có tương phản |
| Plain surface | `#FFFFFF` | Vùng thông tin cần sạch, dễ đọc |

Bản nghiên cứu trước đã tính các cặp trên: chữ ink/nền 10,59:1; muted/nền 5,83:1; cam/nền 4,77:1; trắng/CTA xanh 12,16:1. Đây chỉ là **tỷ lệ của bốn cặp màu**, chưa chứng minh toàn trang đạt WCAG.

**Nguyên tắc:** xanh đậm là “mực nhận diện”, cam là “tín hiệu năng lượng”, kem là “không gian đọc”; không lạm dụng cả ba trên mọi card. Không có gradient, màu mint, glassmorphism hay shadow mặc định nếu chưa chứng minh công dụng.

## 9. Typography — Sans đậm nhưng có phân cấp

**Ứng viên ưu tiên:** Be Vietnam Pro. Phải kiểm tra bằng **font thực sự được tải**, không nghiệm thu ảnh fallback Noto Sans.

- **Display:** thử weight 700–800, tiết chế chiều ngang/letter-spacing, giữ dấu tiếng Việt rõ; mục tiêu mang cá tính của B, không phải viết tất cả chữ in hoa.
- **Section headline:** 600–700; có đối lập thị giác với body mà không phải tô màu nền.
- **Card title:** 600–700; đọc được tiêu đề dài tới ba dòng, không dùng ellipsis để che thông tin chính trong preview nghiên cứu.
- **Body / mô tả:** 400–500; ưu tiên dễ đọc, line-height đủ thoáng; không giảm cỡ chữ để cưỡng ép layout.
- **Metadata / trạng thái:** 600–700, nhãn ngắn và rõ, thêm từ ngữ trạng thái thay vì chỉ màu.

**Thang kích cỡ để THỬ, không phải token:** Hero khoảng 60–72px; section headline 34–44px; card lớn 24–30px; compact 18–22px; body 16–18px; metadata 13–14px. Chỉ hợp lệ nếu không làm sai geometry v0.7.1b; điều chỉnh đề xuất hoặc ghi blocker thay vì tự phá layout.

Bộ stress: “Giáo dục”, “Cơ sở vật chất”, tiêu đề 1/2/3 dòng, VND và phân cách số, dấu tiếng Việt “ắ ằ ẵ ặ ễ ộ ờ ữ”, đoạn ngắn/vừa/dài, responsive runtime hiện tại không được làm hỏng (không mở scope mobile redesign).

## 10. Shape / Border / Depth / Image Direction

**Shape:** ưu tiên cấu trúc nét rõ và bán kính có mục đích. Không dùng cùng radius lớn cho Hero, 8 tiles, card, badges và Footer. Footer giữ full-width, bo **hai góc trên 56px theo bản thô** — **chưa phải token phong cách cuối**.

**Border / shadow:** đường viền làm rõ vùng nội dung; bóng, nếu thực sự cần, chỉ hỗ trợ một điểm phân cấp và phải có lý do. Tránh shadow “elevation” mặc định của dashboard. Không tự thêm grain, grid, gradient hay đường trang trí dưới mỗi tiêu đề.

**Image direction:** ưu tiên ảnh tư liệu thực sự có quyền sử dụng, mô tả trung thực nội dung chiến dịch, không ảnh stock giả cảm xúc. Xem xét góc chụp ngang tầm người, có ngữ cảnh và hành động thật; crop giữ người và đối tượng chính. Chỉ dùng illustration trừu tượng có thông điệp rõ khi không có ảnh; **không làm tranh giả dữ liệu minh chứng**. Chưa phê duyệt bất kỳ ảnh cụ thể nào.

**Iconography:** tối giản, cùng độ dày nét; icon giúp hiểu lĩnh vực/trạng thái, không thay chữ. Không lạm dụng emoji hoặc biểu tượng trái tim/ban tay như trang từ thiện mẫu.

## 11. Cách B đi qua Story → Milestones → Transparency → Footer

- **Story:** giữ nhịp v0.7.1b (vấn đề → giải pháp → tiến độ); chữ kể chuyện mang sức nặng, hình ảnh có nguồn. Không lặp tiền và status ở nhiều khu vực chỉ để tăng mật độ.
- **Milestones:** dùng Rise Path như **timeline thực**. Mốc đã hoàn thành, đang thực hiện, dự kiến phải có nhãn chữ và trạng thái phù hợp. Con số giải ngân trong seed luôn ghi **mô phỏng**; yêu cầu đang xét duyệt phải tách với đã giải ngân.
- **Transparency:** diễn đạt ba trạng thái **kế hoạch → minh chứng đã gửi → xét duyệt nội bộ** bằng cấu trúc rõ và icon/line tiết chế. Không viết “đã được xác minh độc lập” nếu không có cơ chế. Giữ nút mở giải thích có `aria-controls`, `aria-expanded`, `aria-hidden` đồng bộ khi Enter/Space.
- **Footer:** nền đậm có thể tạo cảm giác kết thúc hành trình; giữ full-bleed, chỉ bo hai góc trên theo bản thô. Không biến Footer thành card nổi hoặc thêm CTA sai nghiệp vụ.

## 12. Những lỗi cần loại trước khi gọi là “B thật sự”

1. Hero có 3–4 màu nền, blob 3D và ảnh minh họa người không có nguồn.
2. Hơn nửa số section được bọc trong một card bo tròn/shadow giống nhau.
3. Category trở thành tám ô pastel khác nhau mà thiếu nhãn dễ quét.
4. Campaign Card trông như sản phẩm e-commerce, tên chiến dịch và tài chính khó thấy.
5. Dùng nút/node/đường kết nối tùy ý không mô tả sự kiện, trạng thái hay điều hướng.
6. Mọi progress đều cùng màu/kiểu, gây nhầm huy động với hoàn thành công việc.
7. Số tiền, đơn vị, minh chứng hoặc lời chứng thực được AI tự đặt như sự thật.
8. Chỉ đổi palette A → B mà vẫn giữ nguyên toàn bộ ngôn ngữ đồ họa.
9. Copy tiếng Việt quá nhỏ; các dấu và tiêu đề dài vỡ hàng.
10. Dùng font fallback để khẳng định Be Vietnam Pro đã PASS.
11. Màu cam xuất hiện ở mọi CTA, badge, timeline và đường kẻ, mất vai trò nhấn mạnh.
12. UI đánh đồng nội bộ duyệt hồ sơ với kiểm chứng độc lập.
13. HTML có thêm `h1`/`main` hoặc disclosure quay lại lỗi `aria-hidden`.
14. Dựng lại Homepage từ ảnh AI, làm lệch 37,5/62,5 và thứ tự section.
15. Tự sửa source React, tokens.css hay mở redesign mobile khi mới đang nghiên cứu Art Direction.

## 13. Yêu cầu bàn giao cho Codex / Designer

**Giai đoạn hiện tại chỉ làm nghiên cứu B v0.8.1, KHÔNG dựng Homepage High-Fidelity.** Tài liệu tối thiểu phải có:

- **Hai biến thể B thực sự khác biệt** về ngữ pháp đồ họa (ví dụ *B1 — Bold Editorial Path* và *B2 — Community Graphic System*), cùng content, category, A+C ratio, đọc trên Desktop 1440px. Nếu chỉ khác màu: FAIL.
- **Một moodboard riêng cho B** và nguồn tham khảo có provenance/quyền sử dụng; nêu điều học được, không sao chép layout/ảnh/logo.
- **Bảng font tiếng Việt đã tải thật**, ảnh chụp chữ dài, số tiền và dấu tiếng Việt, kèm thông tin font/build hoặc blocker.
- **Ma trận color/contrast** cho CTA, body, focus, progress, 8 category line và trạng thái; ghi rõ mọi màu là PENDING_APPROVAL.
- **Crops đối chiếu** Hero, Categories, Card, Story, Milestones, Transparency, Footer cùng ghi chú “giữ nguyên geometry / phần nào chỉ thay skin”.
- **QA:** viewport 1440×900 DPR1; kiểm tra scrollWidth, JS errors/network, hành vi keyboard/disclosure và sự phân biệt funding/milestone/evidence.
- **Report verdict** PASS / FAIL / BLOCKED, ranh giới quyết định và một khuyến nghị dựa trên bằng chứng.

**Không được:** merge PR, thay v0.7.1b, chạm frontend/backend, xuất các giá trị màu/font thành runtime token, tự đánh dấu APPROVED, hoặc tuyên bố đã làm High-Fidelity.

**Owner gate kế tiếp:** chọn một biến thể B1/B2 và duyệt *cụ thể* màu, font, category accent, image direction. Chỉ khi có quyết định đó mới được phép mở một nhiệm vụ High-Fidelity Desktop riêng biệt.
