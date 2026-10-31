import type { ReactNode } from 'react';
import { Link } from 'react-router';
import type { UserRole } from '@/api/contracts';
import { useAuth } from './auth-context';

export function RequireRole({ role, children }: { role: UserRole; children: ReactNode }) {
  const { user } = useAuth();
  if (!user?.roles?.includes(role)) return <div className="mx-auto w-full max-w-6xl px-4 py-20 sm:px-6">
    <h1 className="text-2xl font-bold text-brand-ink">Tài khoản không có quyền truy cập</h1>
    <p className="mt-3 text-sm text-muted">Chức năng này dành cho tài khoản có vai trò {role}. Backend sẽ kiểm tra quyền ở mọi yêu cầu.</p>
    <Link className="mt-6 inline-block text-sm font-semibold text-brand underline" to="/workspace">Trở về không gian cá nhân</Link>
  </div>;
  return <>{children}</>;
}
