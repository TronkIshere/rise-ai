# Riser AI — Quyết định thiết kế Categories C3.1

**Ngày chốt:** 2026-11-07
**Phạm vi:** Chỉ thiết kế card Categories (8 danh mục, desktop 1440px), kế thừa C3 Kinetic Outline và bản tinh chỉnh Typography C3.1.
**Trạng thái:** **ĐƯỢC CHỐT BỞI PRODUCT OWNER — mẫu card Categories.** Không đồng nghĩa duyệt toàn Homepage, các mẫu Milestones / Transparency, hay cho phép triển khai React.

## Đặc điểm được chốt
- Giữ bố cục Categories 4×2 và 8 danh mục gốc; mỗi card có đặc trưng riêng qua mảng màu và minh họa liên quan lĩnh vực.
- Dùng nền section có độ đậm từ C3; card có viền xanh rừng đậm (~3px), bóng lệch cạnh kiểu kinetic, hover nhấc nhẹ và trạng thái chọn rõ.
- Không có vòng tròn trắng mờ sau icon; các cặp màu trong một card phải tương phản.
- C3.1: Tên danh mục khoảng 21px, đậm, sử dụng màu nhận diện riêng, ưu tiên font Be Vietnam Pro; mô tả là cấp phụ rõ ràng.
- Tận dụng đúng HTML/CSS và hình minh họa trong `index.html` thuộc gói này; không tự thiết kế lại hoặc làm các card của section khác theo phong cách này.

## Ranh giới thực thi
- Chốt mẫu **không đồng nghĩa đã áp dụng** lên `B2 Development Master v1.1`. Chỉ tích hợp vào Homepage sau khi người dùng yêu cầu cụ thể.
- Các section đã được tạm chốt khác được bảo toàn; không sửa Backend/React/GitHub/PR/commit trong quyết định này.
- Các bản thử C, C2, C3 và C3.1 trước đó chỉ là lịch sử; bản gói này là chuẩn tham chiếu của **Categories Cards C3.1**.
- Dùng ảnh `after_1440.png` để so sánh trực quan, `index.html` là nguồn có thể chỉnh sửa tiếp.

**Nguồn duyệt:** Người dùng: “Chốt bản card này” sau khi xem C3.1 Typography Preview.