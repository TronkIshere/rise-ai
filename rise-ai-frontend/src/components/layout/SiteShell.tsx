import { HeartHandshake, LogOut, Menu } from 'lucide-react';
import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { Button } from '@/components/ui/Button';
import { Container } from './Container';

export function SiteShell() {
  const { user, logout } = useAuth();
  const [warning, setWarning] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  return (
    <div className="flex min-h-screen flex-col bg-canvas text-copy">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-xl focus:bg-surface focus:p-3 focus:text-brand">Bỏ qua điều hướng</a>
      <div className="border-b border-brand/10 bg-soft-mint px-4 py-2 text-center text-xs font-medium text-brand-ink">
        Rise AI là môi trường mô phỏng. Không tiếp nhận hoặc chuyển tiền thật.
      </div>
      <header className="sticky top-0 z-40 border-b border-border bg-surface/95 backdrop-blur-md">
        <Container className="flex min-h-18 items-center justify-between gap-4 py-3">
          <Link to="/" className="flex items-center gap-2.5" aria-label="Rise AI — trang chủ" onClick={() => setMenuOpen(false)}>
            <span className="flex size-10 items-center justify-center rounded-xl bg-brand text-white"><HeartHandshake size={23} aria-hidden="true"/></span>
            <span><strong className="block text-lg font-extrabold leading-5 text-brand-ink">Rise AI</strong><span className="block text-[10px] font-medium tracking-wide text-muted">TỪ THIỆN MINH BẠCH</span></span>
          </Link>
          <button type="button" className="flex min-h-11 min-w-11 items-center justify-center rounded-xl border border-border text-brand lg:hidden" aria-label={menuOpen ? 'Đóng điều hướng' : 'Mở điều hướng'} aria-expanded={menuOpen} aria-controls="site-menu" onClick={() => setMenuOpen(!menuOpen)}><Menu size={21} aria-hidden="true"/></button>
          <nav id="site-menu" className={(menuOpen ? 'flex ' : 'hidden ') + 'absolute left-0 right-0 top-full flex-col gap-3 border-b border-border bg-surface p-4 shadow-lg lg:static lg:flex lg:flex-row lg:items-center lg:gap-7 lg:border-0 lg:p-0 lg:shadow-none'} aria-label="Điều hướng chính">
            <NavLink to="/" end onClick={() => setMenuOpen(false)} className={({isActive}) => 'inline-flex min-h-11 items-center text-sm font-semibold hover:text-brand ' + (isActive ? 'text-brand' : 'text-copy')}>Trang chủ</NavLink>
            <NavLink to="/campaigns" onClick={() => setMenuOpen(false)} className={({isActive}) => 'inline-flex min-h-11 items-center text-sm font-semibold hover:text-brand ' + (isActive ? 'text-brand' : 'text-copy')}>Chiến dịch</NavLink>
            {user ? <NavLink to="/workspace" onClick={() => setMenuOpen(false)} className={({isActive}) => 'inline-flex min-h-11 items-center text-sm font-semibold hover:text-brand ' + (isActive ? 'text-brand' : 'text-copy')}>Cá nhân</NavLink> : <Link to="/login" onClick={() => setMenuOpen(false)} className="inline-flex min-h-11 items-center rounded-xl bg-brand px-5 text-sm font-bold text-white hover:bg-brand-hover">Đăng nhập</Link>}
            {user ? <Button variant="outline" onClick={() => { setMenuOpen(false); void logout().catch(() => setWarning('Đã kết thúc phiên trên máy nhưng chưa xác minh thu hồi phiên ở Backend.')); }}>
              <LogOut size={16} aria-hidden="true"/> Đăng xuất
            </Button> : null}
          </nav>
        </Container>
      </header>
      {warning ? <Container className="py-3"><p role="alert" className="text-sm text-warning">{warning}</p></Container> : null}
      <main id="main-content" className="grow"><Outlet /></main>
      <footer className="border-t border-border bg-brand-ink text-white">
        <Container className="grid gap-8 py-12 sm:grid-cols-[1fr_auto]">
          <div className="max-w-md">
            <Link to="/" className="flex items-center gap-2.5 text-xl font-extrabold"><HeartHandshake size={26} aria-hidden="true"/> Rise AI</Link>
            <p className="mt-4 text-sm leading-7 text-white/75">Hướng tới hành trình đóng góp có thể theo dõi, đối chiếu và đánh giá công khai.</p>
          </div>
          <div className="flex flex-col items-start gap-3 text-sm font-medium text-white/85">
            <Link to="/campaigns" className="hover:underline">Khám phá chiến dịch</Link>
            <Link to="/" className="hover:underline">Trang chủ</Link>
          </div>
          <p className="text-xs leading-6 text-white/65 sm:col-span-2">© Rise AI · Mô phỏng nội bộ · Không thanh toán tiền thật · AI chỉ cung cấp cảnh báo hỗ trợ quyết định.</p>
        </Container>
      </footer>
    </div>
  );
}
