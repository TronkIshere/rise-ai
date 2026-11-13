# RISE AI — DESIGN_GUARDRAILS

**Trạng thái:** Quy tắc do chủ dự án xác lập để kiểm soát concept, thiết kế và nghiệm thu UI.  
**Phạm vi:** Rise AI — ưu tiên Desktop 1440px, các trang và thành phần được giao thiết kế.  
**Mục đích:** Ngăn những sai phạm đã lặp lại giữa các phiên thiết kế; không phải giấy phép sửa mã hay thay đổi những phần đã khóa.

> **NGUYÊN TẮC BẮT BUỘC SỐ 1: CẤM CHỦ THỂ VÀ BACKGROUND CÙNG MÀU HOẶC KHÓ PHÂN BIỆT VỀ THỊ GIÁC.**  
> Chủ thể phải có độ tương phản và thứ bậc rõ ràng với **background trực tiếp của chính nó**.

## 1. Tương phản, màu nền và phân lớp

- Chủ thể (chữ, biểu mẫu, nút, khu vực thao tác, hình ảnh chính, dữ liệu quan trọng) **không được trùng màu hoặc thiếu tương phản** với background chứa nó.
- **Không** diễn giải quy tắc trên thành “cấm các bề mặt khác màu”, “cấm card” hoặc “cả trang phải có một background duy nhất”.
- Mỗi section **có thể có background riêng**, chuyển tiếp, texture, landscape hoặc lớp hình ảnh nếu có lý do thị giác và giữ được tương phản bên trong.
- Card/khối nền khác màu **được phép** khi hữu ích cho việc đọc, thao tác hoặc nhóm dữ liệu; **không được** dùng như công thức mặc định để nhồi mọi thông tin vào các ô.
- Tương phản phải được kiểm tra trên ảnh render thực tế, không chỉ nhìn tên màu/token hoặc kiểm tra CSS; nhất là typography nhỏ, trạng thái nút, biểu mẫu và số tiền.
- Không hy sinh khả năng đọc để đạt cảm giác “hài hòa” hoặc để mọi thành phần chìm vào nền.

## 2. Không SaaS hóa hoặc trang trí wireframe

- Không lặp lại mẫu **banner + lưới card + badge/tem màu + footer** cho mọi trang.
- Không mặc định mỗi section là một khung bo góc, có border, shadow và gradient.
- Không dùng hình tròn mờ, kính mờ, gradient ngẫu nhiên, icon lớn hay nhãn màu để thay cho bố cục có chủ đích.
- Thiết kế bằng typography, tỷ lệ, nhịp điệu, khoảng cách, cấu trúc thông tin và những lớp thị giác có ý nghĩa; tránh những khoảng trống lớn chỉ vì không sử dụng hết cột.
- Mỗi trang phải có cá tính phù hợp chức năng; không biến toàn bộ sản phẩm thành bộ dashboard đổi màu.

## 3. Bản sắc Rise AI: thiện nguyện trước, thiên nhiên sau

- Rise AI là sản phẩm về **thiện nguyện cộng đồng, minh bạch, trách nhiệm giải trình và tác động xã hội**.
- Không được tự đồng nhất Rise AI với du lịch, rừng núi hoặc một thương hiệu bảo vệ thiên nhiên.
- Art Direction B2 và phong cảnh có thể làm chất liệu hỗ trợ; **không lạm dụng ảnh thiên nhiên trên các trang kỹ thuật, tài chính hoặc quản trị**.
- Ảnh/hình minh họa chỉ xuất hiện nếu phục vụ câu chuyện, nhiệm vụ hay ngữ cảnh; không nhồi ảnh để lấp khoảng trống.
- Không sao chép hình thức của Homepage máy móc lên màn hình tác nghiệp; kế thừa ngôn ngữ thương hiệu và thiết kế theo mục đích từng trang.

## 4. Dữ liệu và chức năng phải có nguồn thật

- **CẤM bịa** số tiền, KPI, số lượng hồ sơ, phần trăm, tăng trưởng, lịch sử xử lý, tên người, tổ chức, phản hồi, kết luận, cảnh báo hoặc minh chứng rồi trình bày như dữ liệu ứng dụng.
- **CẤM bịa** tab, module, route, nút, liên kết, chức năng upload, thông báo hay thao tác chưa được xác minh từ mã/contract hiện hành.
- Concept hình ảnh có thể dùng dữ liệu **minh họa được ghi rõ là minh họa** để thảo luận bố cục; **không** dùng chúng làm bằng chứng Backend đã hỗ trợ. Mỗi thành phần phải được đối chiếu trước khi chốt/triển khai.
- Nếu nguồn dữ liệu không tồn tại: bỏ thành phần đó, hoặc ghi đúng là đề xuất ngoài phạm vi và chờ chủ dự án quyết định. Không lén thay bằng số giả.
- Nếu dữ liệu trống/chưa tải/lỗi: thể hiện trạng thái tương ứng, không tự gán 0 hoặc “mọi thứ bình thường”.
- Phải phân biệt **đã có Backend API**, **đã tích hợp ở Frontend** và **mới là concept**; ba trạng thái này không tương đương.
- Không lôi tên biến, response field, UUID thô, JSON/debug label hay thuật ngữ kỹ thuật nội bộ lên UI khi người dùng cần thông tin nghiệp vụ bằng tiếng Việt.

## 5. Đặc biệt nghiêm ngặt với tiền và quyền quyết định

- Tách rõ: **đóng góp đã xác nhận, giải ngân, hoàn tiền, số tiền dành riêng, số dư khả dụng, kết quả đối soát**; không gọi chung tất cả là “tiền đã sử dụng”.
- Số tiền phải có đơn vị, nhãn nghĩa, trạng thái và nguồn; không tự tạo biểu đồ xu hướng nếu không có chuỗi dữ liệu tương ứng.
- Rise AI hiện mô phỏng tài chính: UI phải phân biệt mô phỏng với chuyển tiền thật, không ngầm khẳng định đã thanh toán/giải ngân thực tế.
- Cảnh báo AI chỉ có tính khuyến nghị, **không phải kết luận sai phạm**. Quyết định nghiệp vụ thuộc người có quyền và Backend.
- Không thêm thao tác phê duyệt tự động, tự xử lý tài chính hay đường tắt vượt qua phân quyền.

## 6. Không nhầm vai trò của trang

- Xác minh **route, dữ liệu, quyền, hành động hiện có** trước khi đề xuất concept.
- Không biến trang chọn vai trò **/workspace** thành bản sao dashboard **/admin**, và cũng không biến trang cần dữ liệu vận hành thành trang mục lục chỉ có vài link khi yêu cầu đã xác định khác.
- Không tự vẽ “trung tâm quản lý” hoặc tập hợp module mới chỉ để làm đầy giao diện.
- Với tài khoản nhiều vai trò, giữ đúng quyền DONOR / CAMPAIGN_CREATOR / ADMIN và đường dẫn đang tồn tại.
- Phạm vi nào chưa được thống nhất hoặc có nguy cơ chồng lấn màn hình đã duyệt thì dừng tại bước kiểm tra kiến trúc thông tin, không vẽ hoặc code theo suy đoán.

## 7. Bảo vệ nguồn thiết kế đã duyệt

- Đối chiếu **Homepage B2 v1.3.2** tại: docs/design/art-direction/approved/b2-development-master-v1.3.2/.
- Kế thừa Top Navigation, Footer, typography, card/art direction và các quyết định đã được duyệt ở đúng phạm vi; không chỉ mượn màu xanh lá rồi dựng hệ thống mới.
- **Top Bar N1.1 — Development Master v1.4 chưa có nguồn commit/file xác thực**: không được tự nhận đây là chuẩn đã kiểm chứng. Dùng nguồn v1.3.2 đã xác minh cho đến khi có chứng cứ mới.
- Không sửa chồng lên Homepage, Campaign Discovery, Campaign/Contribution hay các Dashboard đã khóa chỉ để “đồng bộ” các trang mới.
- Tuân thủ tài liệu cũ tại docs/design/UI_GUARDRAILS.md và rise-ai-frontend/AGENTS.md; văn bản này bổ sung các yêu cầu thẩm mỹ chủ dự án đã nói rõ, không thay thế ràng buộc bảo mật, nghiệp vụ hoặc hợp đồng API.
- Thiết kế review **Desktop 1440px**; không mở thêm Mobile UI. Giữ khả năng hoạt động/responsive sẵn có nếu phải chạm mã.

## 8. Quy trình kiểm soát bắt buộc

**Đọc nguồn và nghiệp vụ → xác minh kiến trúc thông tin → concept → ảnh/mẫu trực quan → chủ dự án duyệt → chốt thiết kế → được phép triển khai React → QA ảnh React thực tế → reviewer độc lập duyệt.**

- **Chưa có concept/mẫu được chủ dự án duyệt thì không nhảy thẳng sang code.**
- Chủ dự án duyệt concept **không đồng nghĩa** đã duyệt React hoặc mở rộng toàn bộ các trang.
- Không tự thông báo PASS vì build thành công, không overlap, có đúng màu thương hiệu hoặc tái sử dụng component.
- Ảnh QA phải là UI React render thực tế; đối chiếu chuẩn đã duyệt ở 1440 × 900, DPR 1 khi nằm trong phạm vi nghiệm thu.
- Ghi rõ PASS / NEEDS REVISION / FAIL, bằng chứng, điểm sai và phạm vi ảnh hưởng. Sai thì sửa **đúng nguyên nhân**, không vá bằng cách đổi màu/card hoặc lại bịa module.
- Ưu tiên gom thay đổi thành một gói và commit có ý nghĩa; tránh commit nhỏ lẻ, chồng scope, tự ý merge hoặc đụng nhánh PR khác khi chưa được phép.

## 9. Checklist tự kiểm trước mỗi lần xuất concept

Trả lời **CÓ** cho tất cả câu hỏi liên quan, nếu **KHÔNG** thì phải sửa trước khi trình:

1. Chủ thể có **khác màu và đủ tương phản** so với background trực tiếp không?
2. Có tránh cả hai cực đoan “mọi thứ trong card” và “cả trang buộc một nền” không?
3. Bố cục có giá trị riêng cho công việc của người dùng, không phải template SaaS thay màu không?
4. Ảnh/artwork có đúng ngữ cảnh **thiện nguyện**, không lạm dụng thiên nhiên hoặc trang trí vô ích không?
5. Mỗi con số, trạng thái, tab, route và nút đã được kiểm chứng hoặc đánh dấu rõ là minh họa chưa?
6. Dữ liệu tiền, kiểm toán, cảnh báo và quyền quyết định có được diễn đạt đúng, không gây hiểu nhầm không?
7. Nội dung UI có ngôn ngữ hướng người dùng, không phơi bày kỹ thuật Backend không?
8. Header/Footer và các trang đã khóa có được giữ nguyên đúng phạm vi không?
9. Đã có **chủ dự án duyệt concept** trước khi triển khai chưa?
10. QA có ảnh React thật và reviewer độc lập trước khi tuyên bố nghiệm thu chưa?

## 10. Hướng dẫn bắt buộc cho AI/Codex khi làm Rise AI

- **Đọc toàn bộ file này trước mọi yêu cầu vẽ concept, đánh giá, chỉnh UI hoặc hiện thực hóa React.**
- Nếu nội dung trao đổi gần đây mâu thuẫn hoặc thiếu căn cứ: kiểm tra lại quyết định cụ thể với chủ dự án; **không tự biến một lời nhắc thành lệnh cấm cực đoan**.
- Không tự tuyên bố đã lưu vào ChatGPT Memory vĩnh viễn. Repository là nguồn lưu trữ có thể truy xuất và review; chỉ sau khi tích hợp vào nhánh làm việc thì nó mới tự hiện diện trong checkout của nhánh đó.
