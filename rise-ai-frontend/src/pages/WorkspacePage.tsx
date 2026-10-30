import { useAuth } from '@/auth/auth-context';
import { Container } from '@/components/layout/Container';
import { StatusMessage } from '@/components/ui/StatusMessage';

export function WorkspacePage() {
  const { user } = useAuth();
  return <Container className="py-14">
    <h1 className="text-3xl font-bold">Không gian cá nhân</h1>
    <p className="mt-3 text-muted">Xin chào {user?.fullName ?? user?.email ?? 'thành viên'}.</p>
    <div className="mt-8">
      <StatusMessage title="Module đang được phát triển" detail="FE-00 chỉ xác nhận luồng đăng nhập và route bảo vệ. Dashboard Donor, Creator và Admin chưa triển khai." />
    </div>
  </Container>;
}
