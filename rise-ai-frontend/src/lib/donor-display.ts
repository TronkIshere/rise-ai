import type { DisputeStatus, DisputeType, DonationStatus, PaymentMethod, RefundStatus } from '@/api/contracts';

const donations: Record<DonationStatus,string> = {
  PENDING: 'Chờ quản trị viên xác nhận',
  CONFIRMED: 'Đã xác nhận mô phỏng',
  FAILED: 'Không được xác nhận',
  CANCELLED: 'Đã hủy',
  REFUND_PENDING: 'Đang yêu cầu hoàn tiền',
  PARTIALLY_REFUNDED: 'Đã hoàn một phần (mô phỏng)',
  REFUNDED: 'Đã ghi nhận hoàn tiền (mô phỏng)',
};
const refunds: Record<RefundStatus,string> = {
  REQUESTED: 'Đang chờ xem xét', APPROVED: 'Đã phê duyệt (mô phỏng)',
  REJECTED: 'Bị từ chối', CANCELLED: 'Đã hủy yêu cầu', RECORDED: 'Đã ghi nhận vào sổ mô phỏng',
};
const disputes: Record<DisputeStatus,string> = {
  OPEN: 'Đã mở', UNDER_REVIEW: 'Đang xem xét',
  RESOLVED_UPHELD: 'Kết luận chấp nhận', RESOLVED_DISMISSED: 'Kết luận bác bỏ', WITHDRAWN: 'Đã rút',
};
export const disputeTypes: { value: DisputeType; label: string }[] = [
  { value: 'MISUSE_OF_FUNDS', label: 'Nghi vấn sử dụng sai nguồn lực' },
  { value: 'MISSING_EVIDENCE', label: 'Thiếu minh chứng' },
  { value: 'MISLEADING_INFORMATION', label: 'Thông tin chưa chính xác' },
  { value: 'DELIVERY_NOT_RECEIVED', label: 'Chưa ghi nhận kết quả triển khai' },
  { value: 'OTHER', label: 'Vấn đề khác' },
];
export const paymentMethods: { value: PaymentMethod; label: string }[] = [
  { value: 'SIMULATED_BANK_TRANSFER', label: 'Chuyển khoản (mô phỏng)' },
  { value: 'SIMULATED_CARD', label: 'Thẻ (mô phỏng)' },
  { value: 'SIMULATED_E_WALLET', label: 'Ví điện tử (mô phỏng)' },
];
export function donationLabel(value?: DonationStatus) { return value ? donations[value] : 'Chưa xác định'; }
export function refundLabel(value?: RefundStatus) { return value ? refunds[value] : 'Chưa xác định'; }
export function disputeLabel(value?: DisputeStatus) { return value ? disputes[value] : 'Chưa xác định'; }
export function disputeTypeLabel(value?: DisputeType) { return disputeTypes.find(x=>x.value === value)?.label ?? 'Khác'; }
export function refundableAmount(amount?: number, refunded?: number): number {
  if (typeof amount !== 'number' || !Number.isFinite(amount)) return 0;
  return Math.max(0, amount - (typeof refunded === 'number' && Number.isFinite(refunded) ? refunded : 0));
}
export function canRefund(status?: DonationStatus): boolean { return status === 'CONFIRMED' || status === 'PARTIALLY_REFUNDED'; }
export function isCountedDonation(status?: DonationStatus): boolean {
  return status === 'CONFIRMED' || status === 'REFUND_PENDING' || status === 'PARTIALLY_REFUNDED' || status === 'REFUNDED';
}
