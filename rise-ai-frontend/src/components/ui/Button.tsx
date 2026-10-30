import type { ButtonHTMLAttributes } from 'react';
type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'outline';
  loading?: boolean;
};
export function Button({ className = '', variant = 'primary', loading = false, disabled, children, ...props }: Props) {
  const styles = variant === 'primary'
    ? 'bg-brand text-white hover:bg-brand-hover'
    : 'border border-border bg-surface text-copy hover:bg-subtle';
  return <button
    className={'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-5 py-2 text-sm font-semibold transition-colors disabled:opacity-50 ' + styles + ' ' + className}
    disabled={disabled || loading}
    {...props}
  >
    {loading ? 'Đang xử lý…' : children}
  </button>;
}
