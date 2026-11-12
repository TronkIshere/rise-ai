import { Link } from 'react-router';
import { ArrowLeft, BookOpen, Compass } from 'lucide-react';
import { Container } from '@/components/layout/Container';

export function NotFoundPage() {
  return <div className="rise-deep-page rise-notfound-page">
    <div className="rise-deep-topline" aria-hidden="true"><span>RISE AI / ĐIỀU HƯỚNG</span><span>TRANG CHƯA TỒN TẠI</span></div>
    <Container className="deep-inner py-16">
      <section className="rise-notfound-paper" aria-labelledby="rise-404-title">
        <div className="rise-notfound-illustration" aria-hidden="true">
          <span className="rise-notfound-ring"><Compass size={88}/></span>
          <span className="rise-notfound-orbit orbit-a"/><span className="rise-notfound-orbit orbit-b"/>
        </div>
        <div><p className="rise-notfound-kicker"><BookOpen size={17} aria-hidden="true"/> TRANG KHÔNG TỒN TẠI</p>
          <p className="rise-notfound-code">404<span>.</span></p>
          <h1 id="rise-404-title">Lối đi này<br/><em>chưa có trên bản đồ.</em></h1>
          <p>Đường dẫn bạn mở không tồn tại trong Rise AI. Hãy trở về trang chủ hoặc xem các chiến dịch đang được công bố.</p>
          <div className="rise-notfound-actions">
            <Link to="/" className="rise-notfound-primary"><ArrowLeft size={17} aria-hidden="true"/> Quay về trang chủ</Link>
            <Link to="/campaigns" className="rise-notfound-secondary">Khám phá chiến dịch</Link>
          </div>
        </div>
      </section>
    </Container>
  </div>;
}
