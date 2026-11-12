import { useAuth } from '@/auth/auth-context';
import { Container } from '@/components/layout/Container';
import { Link } from 'react-router';
import { ArrowRight, ArrowUpRight, BookOpenCheck, Building2, HandHeart, ShieldCheck, Sparkles } from 'lucide-react';
import { StatusMessage } from '@/components/ui/StatusMessage';

export function WorkspacePage() {
  const { user } = useAuth();
  return <div className="rise-deep-page rise-workspace-page">
    <div className="rise-deep-topline" aria-hidden="true"><span>RISE AI / KHÔNG GIAN CÁ NHÂN</span><span>11 · HÀNH TRÌNH CỦA BẠN</span></div>
    <Container className="deep-inner py-10 sm:py-14">
      <div className="rise-workspace-hero">
        <div><p className="rise-workspace-kicker"><Sparkles size={16} aria-hidden="true"/> XIN CHÀO, THÀNH VIÊN RISE AI</p>
          <h1>Hành trình của bạn,<br/><em>tiếp nối từ đây.</em></h1>
          <p>Xin chào <strong>{user?.fullName ?? user?.email ?? 'thành viên'}</strong>. Chọn không gian phù hợp với vai trò để xem các dữ liệu và thao tác do Backend quản lý.</p>
        </div>
        <div className="rise-workspace-stamp" aria-hidden="true"><BookOpenCheck size={43}/><span>ONE COMMUNITY<br/>MANY JOURNEYS</span></div>
      </div>
      <div className="rise-workspace-section-head"><div><p>CHỌN LỐI ĐI</p><h2>Không gian làm việc</h2></div><span>{(user?.roles ?? []).length} vai trò được cấp</span></div>
      <div className="rise-workspace-grid">
        {user?.roles?.includes('DONOR') ? <Link to="/donor" aria-label="Quản lý đóng góp của tôi" className="rise-workspace-card rise-workspace-donor">
          <div className="rise-workspace-card-top"><span>01 / DONOR</span><ArrowUpRight size={20} aria-hidden="true"/></div>
          <span className="rise-workspace-card-icon"><HandHeart size={29} aria-hidden="true"/></span>
          <h3>Quản lý đóng góp của tôi</h3><p>Xem lịch sử, yêu cầu hoàn tiền mô phỏng và theo dõi những phản ánh bạn đã gửi.</p>
          <span className="rise-workspace-card-action">Mở hành trình <ArrowRight size={17} aria-hidden="true"/></span>
        </Link> : null}
        {user?.roles?.includes('CAMPAIGN_CREATOR') ? <Link to="/creator" aria-label="Quản lý chiến dịch Creator" className="rise-workspace-card rise-workspace-creator">
          <div className="rise-workspace-card-top"><span>02 / CREATOR</span><ArrowUpRight size={20} aria-hidden="true"/></div>
          <span className="rise-workspace-card-icon"><Building2 size={29} aria-hidden="true"/></span>
          <h3>Quản lý chiến dịch Creator</h3><p>Vận hành tổ chức, chiến dịch, cột mốc và minh chứng được lưu trên Backend.</p>
          <span className="rise-workspace-card-action">Đến không gian Creator <ArrowRight size={17} aria-hidden="true"/></span>
        </Link> : null}
        {user?.roles?.includes('ADMIN') ? <Link to="/admin" aria-label="Trung tâm quản trị" className="rise-workspace-card rise-workspace-admin">
          <div className="rise-workspace-card-top"><span>03 / ADMIN</span><ArrowUpRight size={20} aria-hidden="true"/></div>
          <span className="rise-workspace-card-icon"><ShieldCheck size={29} aria-hidden="true"/></span>
          <h3>Trung tâm quản trị</h3><p>Đánh giá hồ sơ, xử lý quyết định và truy vết lịch sử mô phỏng với quyền Admin.</p>
          <span className="rise-workspace-card-action">Vào trung tâm <ArrowRight size={17} aria-hidden="true"/></span>
        </Link> : null}
        {!user?.roles?.includes('DONOR') && !user?.roles?.includes('CAMPAIGN_CREATOR') && !user?.roles?.includes('ADMIN') ?
          <StatusMessage title="Không có khu vực phù hợp với vai trò" detail="Tài khoản chưa được Backend cấp quyền cho các không gian hiện tại."/> : null}
      </div>
      <div className="rise-workspace-footer-note"><ShieldCheck size={23} aria-hidden="true"/>
        <div><strong>Mô phỏng có kiểm soát</strong><p>Không có thanh toán hay giải ngân tiền thật. Backend xác thực phân quyền cho từng yêu cầu. Các khu vực bạn thấy phản ánh vai trò hiện tại của tài khoản.</p></div>
        <Link to="/campaigns">Khám phá chiến dịch <ArrowUpRight size={16} aria-hidden="true"/></Link>
      </div>
    </Container>
  </div>;
}
