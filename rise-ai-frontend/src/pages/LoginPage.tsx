import { useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { ApiError } from '@/api/client';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';

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
      setError(reason instanceof ApiError ? reason.message : 'Không đăng nhập được. Vui lòng kiểm tra thông tin.');
    } finally {
      setBusy(false);
    }
  }

  return <Container className="flex min-h-[65vh] items-center justify-center py-12">
    <Card className="w-full max-w-md p-8">
      <h1 className="text-3xl font-bold">Đăng nhập Rise AI</h1>
      <p className="mt-3 text-sm text-muted">Tài khoản được quản lý bởi Backend.</p>
      <form className="mt-8 grid gap-5" onSubmit={(event) => void submit(event)}>
        <Input id="email" label="Email" type="email" required autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Input id="password" label="Mật khẩu" type="password" required autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        {error ? <p role="alert" className="text-sm text-danger">{error}</p> : null}
        <Button type="submit" loading={busy} disabled={!email.trim() || !password}>Đăng nhập</Button>
      </form>
      <p className="mt-5 text-xs text-muted">Bản FE-00 lưu phiên trong bộ nhớ. Khi tải lại trang, cần đăng nhập lại.</p>
    </Card>
  </Container>;
}
