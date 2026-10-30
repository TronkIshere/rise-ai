type Props = { title: string; detail?: string; error?: boolean };
export function StatusMessage({ title, detail, error = false }: Props) {
  return <div role={error ? 'alert' : 'status'} className="rounded-card border border-border bg-surface p-6">
    <p className={'font-semibold ' + (error ? 'text-danger' : 'text-copy')}>{title}</p>
    {detail ? <p className="mt-2 text-sm leading-6 text-muted">{detail}</p> : null}
  </div>;
}
