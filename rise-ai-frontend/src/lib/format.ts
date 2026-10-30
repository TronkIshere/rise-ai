export function formatVnd(amount: number | undefined): string {
  if (typeof amount !== 'number' || !Number.isFinite(amount)) return 'Chưa cập nhật';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(amount);
}
