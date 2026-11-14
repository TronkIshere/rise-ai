import type { ReactNode } from 'react';
import { AlertTriangle, Check, Clock3, Info } from 'lucide-react';
import { WorkspaceIllustration } from '@/components/ui/WorkspaceIllustration';

export function AdminMasthead({ title, eyebrow, art, children }: {
  title: string;
  eyebrow: string;
  art?: Parameters<typeof WorkspaceIllustration>[0]['kind'];
  children?: ReactNode;
}) {
  return <header className="admin-ops-mast">
    <div><p className="admin-ops-eyebrow">{eyebrow}</p><h1>{title}</h1>{children}</div>
    {art && <WorkspaceIllustration kind={art} />}
  </header>;
}

const tones: Record<string, 'success' | 'pending' | 'critical' | 'info'> = {
  ACCEPTED: 'success', APPROVED: 'success', VERIFIED: 'success', CONFIRMED: 'success', CONSISTENT: 'success',
  RELEASED: 'success', RECORDED: 'success', PUBLISHED: 'success', SENT: 'success', ACTIVE: 'success', COMPLETED: 'success',
  PENDING: 'pending', PENDING_REVIEW: 'pending', REQUESTED: 'pending', PROPOSED: 'pending', SUBMITTED: 'pending',
  UNDER_REVIEW: 'pending', NEEDS_MORE_EVIDENCE: 'pending', OPEN: 'pending', RETRYING: 'pending', MEDIUM: 'pending',
  FAILED: 'critical', REJECTED: 'critical', INCONSISTENT: 'critical', DISPUTED: 'critical', CRITICAL: 'critical', HIGH: 'critical',
  INFO: 'info', LOW: 'info', IN_FLIGHT: 'info', ACKNOWLEDGED: 'info', CONCERN_CONFIRMED: 'info',
};
const icons = { success: Check, pending: Clock3, critical: AlertTriangle, info: Info };
export function AdminState({ value, children }: { value?: string; children: ReactNode }) {
  const tone = value ? tones[value] ?? 'neutral' : 'neutral';
  const Icon = tone === 'neutral' ? Info : icons[tone];
  return <span className="admin-ops-state" data-tone={tone}><Icon size={17} aria-hidden="true" />{children}</span>;
}
