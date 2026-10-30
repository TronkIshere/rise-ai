import type { InputHTMLAttributes } from 'react';
type Props = InputHTMLAttributes<HTMLInputElement> & { label: string; id: string };
export function Input({ label, id, className = '', ...props }: Props) {
  return <div>
    <label htmlFor={id} className="mb-2 block text-sm font-semibold text-copy">{label}</label>
    <input id={id} className={'min-h-11 w-full rounded-xl border border-border bg-surface px-3 py-2 outline-offset-2 focus:border-brand ' + className} {...props} />
  </div>;
}
