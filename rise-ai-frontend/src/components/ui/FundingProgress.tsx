import { amountLabel, progressLabel, progressValue } from '@/lib/campaign-display';

export function FundingProgress({
  raised, target, currency = 'VND', compact = false,
}: { raised?: number; target?: number; currency?: string; compact?: boolean }) {
  const value = progressValue(raised, target);
  const percent = progressLabel(raised, target);
  return <div>
    <div className="mb-2 flex items-end justify-between gap-2">
      <div>
        <p className="text-xs text-muted">Đã ghi nhận (mô phỏng)</p>
        <p className={'font-bold text-brand-ink ' + (compact ? 'text-lg' : 'text-2xl')}>{amountLabel(raised, currency)}</p>
      </div>
      <p className="text-sm font-bold text-brand">{percent}</p>
    </div>
    <progress className="funding-progress" value={value} max={100} aria-label="Tiến độ huy động mô phỏng"/>
    <p className="mt-2 text-xs text-muted">Mục tiêu: {amountLabel(target, currency)}</p>
  </div>;
}
