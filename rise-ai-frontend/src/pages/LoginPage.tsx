import { useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router';
import { ArrowRight, HandHeart, HeartHandshake, ShieldCheck, Sprout, Users } from 'lucide-react';
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
        <div className="b2-login-story">
          <p className="rise-entry-seal">Rise AI</p>
          <h1>Kết nối lòng tốt<br />Lan tỏa tác động<br /><em>vì cộng đồng</em></h1>
          <p>Nền tảng thiện nguyện minh bạch, kết nối những tấm lòng nhân ái với các dự án tạo tác động tích cực cho xã hội.</p>
          <ul className="b2-login-values">
            <li><HandHeart aria-hidden="true" /><span><strong>Ủng hộ</strong> những dự án ý nghĩa</span></li>
            <li><Users aria-hidden="true" /><span><strong>Đồng hành</strong> cùng cộng đồng</span></li>
            <li><Sprout aria-hidden="true" /><span><strong>Tạo tác động</strong> bền vững và minh bạch</span></li>
          </ul>
        </div>
        <div className="b2-login-photo-strip" role="img" aria-label="Ảnh minh họa hoạt động thiện nguyện">
          <img src="/b2-home/campaign-learning.jpg" alt="" />
          <img src="/b2-home/hero-community.jpg" alt="" />
          <img src="/b2-home/campaign-hope.jpg" alt="" />
        </div>
      </section>
      <section className="rise-entry-paper" aria-labelledby="rise-login-title">
        <HeartHandshake className="b2-login-emblem" size={53} aria-hidden="true" />
        <h2 id="rise-login-title">Chào mừng trở lại</h2>
        <p className="rise-entry-copy">Đăng nhập để tiếp tục hành trình thiện nguyện cùng Rise AI.</p>
        <form className="rise-entry-form" onSubmit={(event) => void submit(event)}>
          <Input id="email" label="Địa chỉ email" type="email" required autoComplete="username" placeholder="Nhập email của bạn"
            value={email} onChange={(e) => setEmail(e.target.value)} />
          <Input id="password" label="Mật khẩu" type="password" required autoComplete="current-password" placeholder="Nhập mật khẩu"
            value={password} onChange={(e) => setPassword(e.target.value)} />
          {error ? <p role="alert" className="rise-entry-error">{error}</p> : null}
          <Button type="submit" className="rise-entry-submit" loading={busy} disabled={!email.trim() || !password}>
            Đăng nhập <ArrowRight size={18} aria-hidden="true"/>
          </Button>
        </form>
        <div className="rise-entry-security"><ShieldCheck size={20} aria-hidden="true"/>
          <p><strong>Phiên đăng nhập.</strong> Khi tải lại trang, bạn cần đăng nhập lại.</p></div>
        <p className="rise-entry-legal">Rise AI là môi trường mô phỏng, không tiếp nhận hoặc chuyển tiền thật.</p>
      </section>
    </Container>
  </div>;
}
