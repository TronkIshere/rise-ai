import { Link } from 'react-router';
import { Container } from '@/components/layout/Container';
export function NotFoundPage() {
  return <Container className="py-20">
    <h1 className="text-3xl font-bold">Không tìm thấy trang</h1>
    <Link to="/" className="mt-5 inline-block text-brand underline">Quay về trang chủ</Link>
  </Container>;
}
