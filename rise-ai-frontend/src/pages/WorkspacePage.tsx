import { useAuth } from '@/auth/auth-context';
import { Container } from '@/components/layout/Container';
import { Link } from 'react-router';
import { HandHeart, Building2, ShieldCheck } from 'lucide-react';
import { StatusMessage } from '@/components/ui/StatusMessage';

export function WorkspacePage() {
  const { user } = useAuth();
  return <Container className="py-14">
    <h1 className="text-3xl font-bold">Không gian cá nhân</h1>
    <p className="mt-3 text-muted">Xin chào {user?.fullName ?? user?.email ?? 'thành viên'}.</p>
    <div className="mt-8">
      {user?.roles?.includes('DONOR') ? <Link to="/donor" className="inline-flex min-h-12 items-center gap-2 rounded-xl bg-brand px-6 text-sm font-bold text-white"><HandHeart size={18} aria-hidden="true"/> Quản lý đóng góp của tôi</Link> : null}
      {user?.roles?.includes('CAMPAIGN_CREATOR') ? <Link to="/creator" className="ml-3 inline-flex min-h-12 items-center gap-2 rounded-xl bg-brand px-6 text-sm font-bold text-white"><Building2 size={18} aria-hidden="true"/> Quản lý chiến dịch Creator</Link> : null}
      {user?.roles?.includes('ADMIN') ? <Link to="/admin" className="ml-3 inline-flex min-h-12 items-center gap-2 rounded-xl bg-brand px-6 text-sm font-bold text-white"><ShieldCheck size={18} aria-hidden="true"/> Trung tâm quản trị</Link> : null}
      {!user?.roles?.includes('DONOR') && !user?.roles?.includes('CAMPAIGN_CREATOR') && !user?.roles?.includes('ADMIN') ? <StatusMessage title="Không gian dành cho vai trò khác đang phát triển" detail="Các khu vực tài khoản đang được phát triển." /> : null}
    </div>
  </Container>;
}
