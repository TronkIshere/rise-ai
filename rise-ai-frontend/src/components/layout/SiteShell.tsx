import { useState } from 'react';
import { HeartHandshake, LogOut } from 'lucide-react';
import { Link, NavLink, Outlet } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { Button } from '@/components/ui/Button';
import { Container } from './Container';

export function SiteShell() {
  const { user, logout } = useAuth();
  const [warning, setWarning] = useState('');
  return (
    <div className="flex min-h-screen flex-col bg-canvas text-copy">
      <header className="border-b border-border bg-surface">
        <Container className="flex min-h-18 flex-wrap items-center justify-between gap-3 py-3">
          <Link to="/" className="flex items-center gap-2 font-bold text-brand-ink" aria-label="Rise AI — trang chủ">
            <span className="flex size-10 items-center justify-center rounded-xl bg-brand text-white">
              <HeartHandshake aria-hidden="true" size={21} />
            </span>
            <span>Rise AI</span>
          </Link>
          <nav className="flex items-center gap-3 text-sm" aria-label="Điều hướng chính">
            <NavLink to="/campaigns" className="font-medium hover:text-brand">Chiến dịch</NavLink>
            {user ? <NavLink to="/workspace" className="font-medium hover:text-brand">Cá nhân</NavLink> : <NavLink to="/login" className="font-medium hover:text-brand">Đăng nhập</NavLink>}
            {user ? <Button variant="outline" onClick={() => void logout().catch(() => setWarning('Phiên trên máy đã kết thúc, nhưng chưa xác minh việc thu hồi token ở Backend.'))}>
              <LogOut aria-hidden="true" size={16}/> <span className="hidden sm:inline">Đăng xuất</span>
            </Button> : null}
          </nav>
        </Container>
      </header>
      {warning ? <Container className="py-3"><p role="alert" className="text-sm text-warning">{warning}</p></Container> : null}
      <main id="main-content" className="grow"><Outlet /></main>
      <footer className="border-t border-border bg-surface">
        <Container className="flex flex-wrap justify-between gap-2 py-6 text-xs text-muted">
          <p>Rise AI — Từ thiện minh bạch.</p>
          <p>Bản mô phỏng · Không giao dịch tiền thật · AI chỉ hỗ trợ đánh giá</p>
        </Container>
      </footer>
    </div>
  );
}
