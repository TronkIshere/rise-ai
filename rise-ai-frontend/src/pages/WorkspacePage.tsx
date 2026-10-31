import { useAuth } from '@/auth/auth-context';
import { Container } from '@/components/layout/Container';
import { Link } from 'react-router';
import { HandHeart } from 'lucide-react';
import { StatusMessage } from '@/components/ui/StatusMessage';

export function WorkspacePage() {
  const { user } = useAuth();
  return <Container className="py-14">
    <h1 className="text-3xl font-bold">Không gian cá nhân</h1>
    <p className="mt-3 text-muted">Xin chào {user?.fullName ?? user?.email ?? 'thành viên'}.</p>
    <div className="mt-8">
      {user?.roles?.includes('DONOR') ? <Link to="/donor" className="inline-flex min-h-12 items-center gap-2 rounded-xl bg-brand px-6 text-sm font-bold text-white"><HandHeart size={18} aria-hidden="true"/> Quản lý đóng góp của tôi</Link> : null}
      {!user?.roles?.includes('DONOR') ? <StatusMessage title="Không gian dành cho vai trò khác đang phát triển" detail="FE-03–FE-05 sẽ triển khai Creator và Admin; FE-02 chỉ phục vụ DONOR." /> : null}
    </div>
  </Container>;
}
