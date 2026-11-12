import { useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router';
import { ArrowRight, HeartHandshake, ShieldCheck, Sprout } from 'lucide-react';
import { useAuth } from '@/auth/auth-context';
import { ApiError } from '@/api/client';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export function LoginPage() {
  const { user, login } = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const from = (location.state as { from?: string } | null)?.from;
  const destination = typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') ? from : '/workspace';
  if (user) return <Navigate to={destination} replace />;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await login({ email: email.trim(), password });
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : 'Không đăng nhập được. Vui lòng kiểm tra thông tin.' );
    } finally {
      setBusy(false);
    }
  }

  return <div className="rise-deep-page rise-entry-page">
    <div className="rise-deep-topline" aria-hidden="true"><span>RISE AI / TÀI KHOẢN</span><span>10 · BẮT ĐẦU HÀNH TRÌNH</span></div>
    <Container className="deep-inner rise-entry-layout">
      <section className="rise-entry-art" aria-label="Giới thiệu Rise AI">
        <div className="rise-entry-seal"><Sprout size={22} aria-hidden="true"/> MỘT CỘNG ĐỒNG · NHIỀU HÀNH TRÌNH</div>
        <h1>Minh bạch bắt đầu từ <em>niềm tin.</em></h1>
        <p>Đăng nhập để theo dõi đóng góp, vận hành chiến dịch hoặc kiểm duyệt các bước thực hiện — tất cả trong một không gian chung.</p>
        <div className="rise-entry-journey" aria-label="Ba chặng trải nghiệm">
          <div><span>01</span><strong>Khám phá</strong><small>Những sáng kiến cộng đồng</small></div>
          <div><span>02</span><strong>Đồng hành</strong><small>Ghi nhận đóng góp mô phỏng</small></div>
          <div><span>03</span><strong>Theo dõi</strong><small>Dữ liệu và tiến độ rõ ràng</small></div>
        </div>
        <div className="rise-entry-landscape" aria-hidden="true">
          <span className="rise-entry-hill one"/><span className="rise-entry-hill two"/>
          <span className="rise-entry-hill three"/><span className="rise-entry-sun"/>
          <span className="rise-entry-leaf left"/><span className="rise-entry-leaf right"/>
        </div>
      </section>
      <section className="rise-entry-paper" aria-labelledby="rise-login-title">
        <div className="rise-entry-paper-header"><span className="rise-entry-icon"><HeartHandshake size={27} aria-hidden="true"/></span>
          <span className="rise-entry-side-label">TÀI KHOẢN CỦA BẠN / RISE AI</span></div>
        <p className="rise-entry-step">CHÀO MỪNG TRỞ LẠI</p>
        <h2 id="rise-login-title">Đăng nhập Rise AI</h2>
        <p className="rise-entry-copy">Sử dụng tài khoản do Backend của hệ thống quản lý để tiếp tục hành trình.</p>
        <form className="rise-entry-form" onSubmit={(event) => void submit(event)}>
          <Input id="email" label="Địa chỉ email" type="email" required autoComplete="username"
            value={email} onChange={(e) => setEmail(e.target.value)} />
          <Input id="password" label="Mật khẩu" type="password" required autoComplete="current-password"
            value={password} onChange={(e) => setPassword(e.target.value)} />
          {error ? <p role="alert" className="rise-entry-error">{error}</p> : null}
          <Button type="submit" className="rise-entry-submit" loading={busy} disabled={!email.trim() || !password}>
            Đăng nhập <ArrowRight size={18} aria-hidden="true"/>
          </Button>
        </form>
        <div className="rise-entry-security"><ShieldCheck size={20} aria-hidden="true"/>
          <p><strong>Bảo vệ phiên đăng nhập.</strong> Token chỉ lưu trong bộ nhớ ứng dụng, không lưu vào Local Storage. Khi tải lại trang, bạn cần đăng nhập lại.</p></div>
        <p className="rise-entry-legal">Rise AI là môi trường mô phỏng, không tiếp nhận hoặc chuyển tiền thật.</p>
      </section>
    </Container>
  </div>;
}
