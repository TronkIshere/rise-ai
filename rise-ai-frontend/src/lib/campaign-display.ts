import { formatVnd } from './format';

const categories: Record<string, string> = {
  EDUCATION: 'Giáo dục',
  COMMUNITY: 'Cộng đồng',
  FOOD_SECURITY: 'Lương thực',
  WATER_SANITATION: 'Nước sạch',
  SCHOLARSHIP: 'Học bổng',
  INFRASTRUCTURE: 'Cơ sở vật chất',
  HEALTH: 'Y tế',
  OTHER: 'Khác',
};
const milestones: Record<string, string> = {
  PLANNED: 'Dự kiến',
  READY: 'Sẵn sàng',
  IN_PROGRESS: 'Đang thực hiện',
  EVIDENCE_SUBMITTED: 'Đã gửi minh chứng',
  UNDER_REVIEW: 'Đang xem xét',
  PARTIALLY_DISBURSED: 'Giải ngân một phần',
  DISBURSED: 'Đã giải ngân',
  DISPUTED: 'Đang có tranh chấp',
  COMPLETED: 'Đã hoàn thành',
  CANCELLED: 'Đã hủy',
};

export const categoryOptions = [
  { value: 'EDUCATION', label: 'Giáo dục' },
  { value: 'COMMUNITY', label: 'Cộng đồng' },
  { value: 'FOOD_SECURITY', label: 'Lương thực' },
  { value: 'WATER_SANITATION', label: 'Nước sạch' },
  { value: 'SCHOLARSHIP', label: 'Học bổng' },
  { value: 'INFRASTRUCTURE', label: 'Cơ sở vật chất' },
  { value: 'HEALTH', label: 'Y tế' },
  { value: 'OTHER', label: 'Khác' },
] as const;

export function categoryLabel(value?: string): string {
  return value ? (categories[value] ?? 'Chiến dịch') : 'Chiến dịch';
}
export function milestoneLabel(value?: string): string {
  return value ? (milestones[value] ?? 'Chưa xác định') : 'Chưa xác định';
}
export function statusLabel(value?: string): string {
  const labels: Record<string, string> = {
    ACTIVE: 'Đang kêu gọi',
    PAUSED: 'Tạm dừng',
    COMPLETED: 'Hoàn thành',
  };
  return value ? (labels[value] ?? 'Trạng thái: ' + value) : 'Chưa xác định';
}
export function progressValue(raised?: number, target?: number): number {
  if (typeof raised !== 'number' || typeof target !== 'number' || !Number.isFinite(raised) || !Number.isFinite(target) || target <= 0) return 0;
  return Math.min(100, Math.max(0, (raised / target) * 100));
}
export function progressLabel(raised?: number, target?: number): string {
  if (typeof raised !== 'number' || typeof target !== 'number' || target <= 0 || !Number.isFinite(raised) || !Number.isFinite(target)) return 'Chưa có số liệu';
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Math.max(0, raised / target * 100)) + '%';
}
export function amountLabel(amount?: number, currency?: string): string {
  if (currency && currency !== 'VND') {
    return typeof amount === 'number' && Number.isFinite(amount)
      ? new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(amount) + ' ' + currency
      : 'Chưa cập nhật';
  }
  return formatVnd(amount);
}
export function dateLabel(value?: string): string {
  if (!value) return 'Chưa cập nhật';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Chưa cập nhật' : new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(date);
}
export function safeImageUrl(value?: string): string | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value, window.location.origin);
    if (url.protocol === 'https:' || url.protocol === 'http:') return url.href;
  } catch { return undefined; }
  return undefined;
}

export function usableCampaignImageUrl(value?: string): string | undefined {
  const url = safeImageUrl(value);
  // DemoDataSeeder uses this dedicated placeholder provider for campaign media.
  if (url && new URL(url).hostname.replace(/\.$/, '') !== 'placehold.co') return url;
  return undefined;
}
