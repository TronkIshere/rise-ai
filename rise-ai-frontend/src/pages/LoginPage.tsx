import { useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router';
import { ArrowRight, HandHeart, HeartHandshake, LockKeyhole, Mail, ShieldCheck, Sprout, Users } from 'lucide-react';
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
          <h1><span>Kết nối lòng tốt</span><span>Lan tỏa tác động</span><em>vì <strong>cộng đồng</strong></em></h1>
          <p>Nền tảng thiện nguyện minh bạch, kết nối những tấm lòng nhân ái với các dự án tạo tác động tích cực cho xã hội.</p>
          <ul className="b2-login-values">
            <li><HandHeart aria-hidden="true" /><span><strong>Ủng hộ</strong> những dự án ý nghĩa</span></li>
            <li><Users aria-hidden="true" /><span><strong>Đồng hành</strong> cùng cộng đồng</span></li>
            <li><Sprout aria-hidden="true" /><span><strong>Tạo tác động</strong> bền vững và minh bạch</span></li>
          </ul>
        </div>
        <div className="b2-login-photo-strip" role="img" aria-label="Minh họa chăm sóc sức khỏe, niềm vui trẻ em và hỗ trợ tại cộng đồng">
          <svg className="b2-login-clip-defs" aria-hidden="true" focusable="false" width="0" height="0">
            <defs>
              <clipPath id="login-paper-edge" clipPathUnits="objectBoundingBox"><path d="M .28 0 H 1 V .68 C .95 .75 .89 .77 .82 .79 C .73 .82 .68 .88 .64 .93 C .61 .97 .55 .99 .42 1 L .2 1 C .15 .91 .04 .83 .025 .63 C .008 .45 .065 .26 .14 .14 C .18 .08 .23 .035 .28 0 Z" /></clipPath>
              <clipPath id="login-care-scene" clipPathUnits="objectBoundingBox"><path d="M .12 0 H 1 V .63 C .94 .89 .82 1 .67 .91 C .47 .78 .27 .87 .05 1 C .02 .67 .03 .26 .12 0 Z" /></clipPath>
              <clipPath id="login-joy-scene" clipPathUnits="objectBoundingBox"><path d="M .03 .52 C .18 .1 .36 .02 .56 .11 C .73 .18 .83 .27 1 .02 C .96 .33 .99 .66 .83 .86 C .68 1 .52 .83 .37 .87 C .18 .9 .08 1 0 1 Z" /></clipPath>
              <clipPath id="login-field-scene" clipPathUnits="objectBoundingBox"><path d="M 0 1 C .04 .64 .16 .35 .36 .23 C .53 .1 .67 .25 .83 .27 C .92 .28 .97 .22 1 .08 L .98 .76 C .87 .96 .77 1 .59 1 Z" /></clipPath>
            </defs>
          </svg>
          <div className="b2-login-photo b2-login-photo-care"><img src="/b2-home/campaign-learning.jpg" alt="" /></div>
          <div className="b2-login-photo b2-login-photo-joy"><img src="/b2-home/hero-community.jpg" alt="" /></div>
          <div className="b2-login-photo b2-login-photo-field"><img src="/b2-home/campaign-hope.jpg" alt="" /></div>
        </div>
        <div className="b2-login-meadow" aria-hidden="true" />
        <svg className="b2-login-thread" viewBox="0 0 900 165" preserveAspectRatio="none" aria-hidden="true" focusable="false">
          <path d="M-25 158c118-87 208-57 310-29 80 21 126-61 98-84-15-12-38 6-39 30-10-19-31-25-41-12-19 31 52 83 109 64 92-31 145-65 271-39 71 15 99 30 222 1" />
        </svg>
      </section>
      <section className="rise-entry-paper" aria-labelledby="rise-login-title">
        <div className="b2-login-sheet">
          <HeartHandshake className="b2-login-emblem" size={53} aria-hidden="true" />
          <h2 id="rise-login-title">Chào mừng trở lại</h2>
          <p className="rise-entry-copy">Đăng nhập để tiếp tục hành trình thiện nguyện cùng Rise AI.</p>
          <form className="rise-entry-form" onSubmit={(event) => void submit(event)}>
            <div className="b2-login-field"><Mail size={18} aria-hidden="true" />
            <Input id="email" label="Địa chỉ email" type="email" required autoComplete="username" placeholder="Nhập email của bạn"
              value={email} onChange={(e) => setEmail(e.target.value)} /></div>
            <div className="b2-login-field"><LockKeyhole size={18} aria-hidden="true" />
            <Input id="password" label="Mật khẩu" type="password" required autoComplete="current-password" placeholder="Nhập mật khẩu"
              value={password} onChange={(e) => setPassword(e.target.value)} /></div>
            {error ? <p role="alert" className="rise-entry-error">{error}</p> : null}
            <Button type="submit" className="rise-entry-submit" loading={busy} disabled={!email.trim() || !password}>
              Đăng nhập <ArrowRight size={18} aria-hidden="true"/>
            </Button>
          </form>
          <div className="rise-entry-security"><ShieldCheck size={20} aria-hidden="true"/>
            <p><strong>Phiên đăng nhập.</strong> Khi tải lại trang, bạn cần đăng nhập lại.</p></div>
          <p className="rise-entry-legal">Rise AI là môi trường mô phỏng, không tiếp nhận hoặc chuyển tiền thật.</p>
        </div>
      </section>
    </Container>
  </div>;
}
