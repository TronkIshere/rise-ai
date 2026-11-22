import { useQuery } from '@tanstack/react-query';
import { ArrowRight, ArrowUpRight, BookOpenCheck, Building2, HandHeart, ShieldCheck, Sparkles } from 'lucide-react';
import { adminStatistics } from '@/api/admin';
import { WorkspaceAdminPortrait, WorkspaceHeroIllustration, WorkspaceIllustration } from '@/components/ui/WorkspaceIllustration';
import { Button } from '@/components/ui/Button';
import { useB2Desktop } from '@/lib/useB2Desktop';
import { amountLabel } from '@/lib/campaign-display';
import { useAuth } from '@/auth/auth-context';
import { Container } from '@/components/layout/Container';
import { Link } from 'react-router';
import { StatusMessage } from '@/components/ui/StatusMessage';

export function WorkspacePage() {
  const { user } = useAuth();
  const desktop = useB2Desktop();
  return desktop && user?.roles?.includes('ADMIN') ? <AdminWorkspace /> : <RoleWorkspace />;
}

function RoleWorkspace() {
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
          <div className="rise-workspace-card-top"><span>01 / NGƯỜI ĐÓNG GÓP</span><ArrowUpRight size={20} aria-hidden="true"/></div>
          <span className="rise-workspace-card-icon"><HandHeart size={29} aria-hidden="true"/></span>
          <h3>Quản lý đóng góp của tôi</h3><p>Xem lịch sử, yêu cầu hoàn tiền mô phỏng và theo dõi những phản ánh bạn đã gửi.</p>
          <span className="rise-workspace-card-action">Mở hành trình <ArrowRight size={17} aria-hidden="true"/></span>
        </Link> : null}
        {user?.roles?.includes('CAMPAIGN_CREATOR') ? <Link to="/creator" aria-label="Quản lý chiến dịch Creator" className="rise-workspace-card rise-workspace-creator">
          <div className="rise-workspace-card-top"><span>02 / NGƯỜI TẠO CHIẾN DỊCH</span><ArrowUpRight size={20} aria-hidden="true"/></div>
          <span className="rise-workspace-card-icon"><Building2 size={29} aria-hidden="true"/></span>
          <h3>Quản lý chiến dịch Creator</h3><p>Vận hành tổ chức, chiến dịch, cột mốc và minh chứng được lưu trên Backend.</p>
          <span className="rise-workspace-card-action">Đến không gian Creator <ArrowRight size={17} aria-hidden="true"/></span>
        </Link> : null}
        {user?.roles?.includes('ADMIN') ? <Link to="/admin" aria-label="Trung tâm quản trị" className="rise-workspace-card rise-workspace-admin">
          <div className="rise-workspace-card-top"><span>03 / QUẢN TRỊ VIÊN</span><ArrowUpRight size={20} aria-hidden="true"/></div>
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

const campaignStates = [
  ['ACTIVE', 'Đang hoạt động'],
  ['PENDING_REVIEW', 'Chờ xét duyệt'],
  ['PAUSED', 'Tạm dừng'],
  ['COMPLETED', 'Đã hoàn thành'],
  ['DRAFT', 'Bản nháp'],
  ['APPROVED', 'Đã duyệt'],
  ['REJECTED', 'Từ chối'],
  ['CANCELLED', 'Đã hủy'],
] as const;

function AdminWorkspace() {
  const { user } = useAuth();
  const stats = useQuery({ queryKey: ['admin', user?.id, 'statistics'], queryFn: adminStatistics });
  const summary = stats.data;
  const finance = [
    { label: 'Đóng góp đã xác nhận', value: summary?.totalConfirmedContributions, kind: 'ledger' },
    { label: 'Số dư khả dụng', value: summary?.totalAvailable, kind: 'wallet' },
    { label: 'Đã giải ngân', value: summary?.totalDisbursed, kind: 'gift' },
    { label: 'Đã hoàn tiền', value: summary?.totalRefunded, kind: 'refund' },
    { label: 'Đang dành riêng', value: summary?.totalReserved, kind: 'lock' },
  ] as const;
  const tasks = [
    ['Đóng góp chờ xác nhận', summary?.pendingDonations],
    ['Yêu cầu giải ngân mở', summary?.openDisbursementRequests],
    ['Yêu cầu hoàn tiền mở', summary?.openRefundRequests],
    ['Tranh chấp đang mở', summary?.openDisputes],
  ] as const;
  const shortcuts = [
    ['/admin/escrow', 'Đối soát nguồn quỹ', 'Đối chiếu số liệu và sổ giao dịch.', 'scale'],
    ['/admin/monitoring', 'Cảnh báo cần xem xét', 'Tín hiệu tham khảo để người có quyền kiểm tra.', 'alert'],
    ['/admin/audit', 'Nhật ký hoạt động', 'Tra cứu lịch sử thao tác quản trị.', 'history'],
  ] as const;
  const hasValue = (value?: number): value is number => typeof value === 'number' && Number.isFinite(value);
  const number = (value?: number) => hasValue(value) ? new Intl.NumberFormat('vi-VN').format(value) : '—';
  const hasStats = finance.some(item => hasValue(item.value)) || tasks.some(([, value]) => hasValue(value))
    || campaignStates.some(([state]) => hasValue(summary?.campaignsByStatus?.[state]));

  return <div className="rise-deep-page rise-workspace-page b2-admin-workspace">
    <Container className="deep-inner">
      <header className="b2-admin-intro">
        <div>
          <p><ShieldCheck size={17} aria-hidden="true" /> Không gian quản trị</p>
          <h1>Quản trị <em>nguồn lực</em></h1>
          <p>Giữ vững niềm tin và sự minh bạch cho cộng đồng cùng Rise AI.</p>
        </div>
        <WorkspaceHeroIllustration />
        <div className="b2-admin-profile"><WorkspaceAdminPortrait /><div><strong>{user?.fullName ?? user?.email ?? 'Quản trị viên'}</strong><Link to="/admin">Trung tâm quản trị <ArrowUpRight size={16} aria-hidden="true" /></Link></div></div>
      </header>
      <section className="b2-admin-finance" aria-labelledby="workspace-finance">
        <div className="b2-admin-panel-head"><WorkspaceIllustration kind="ledger" /><div><h2 id="workspace-finance">Tổng quan tài chính</h2><p>Số liệu mô phỏng · Không chuyển tiền thật</p></div></div>
        {stats.isPending && <StatusMessage title="Đang tải số liệu quản trị…" />}
        {stats.isError && <div className="b2-admin-feedback"><StatusMessage error title="Không tải được số liệu quản trị" detail={stats.error.message} /><Button variant="outline" loading={stats.isFetching} onClick={() => void stats.refetch()}>Tải lại số liệu</Button></div>}
        {stats.isSuccess && !hasStats && <StatusMessage title="Chưa có số liệu quản trị" />}
        {stats.isSuccess && <dl className="b2-admin-balances">
          {finance.map(({ label, value, kind }) => <div key={label}>
            <WorkspaceIllustration kind={kind} />
            <div><dt>{label}</dt><dd>{hasValue(value) ? amountLabel(value, summary?.currency) : '—'}</dd></div>
          </div>)}
        </dl>}
        {stats.isSuccess && finance.some(item => !hasValue(item.value)) && <p className="b2-unknown-note">— Chưa có số liệu cho chỉ mục này.</p>}
      </section>
      <div className="b2-admin-overview">
        <section aria-labelledby="workspace-tasks">
          <h2 id="workspace-tasks">Công việc cần xử lý</h2>
          <div className="b2-admin-list"><WorkspaceIllustration kind="checklist" />
          {stats.isSuccess ? <dl>{tasks.map(([label, total]) => <div key={label}><dt>{label}</dt><dd>{number(total)}</dd></div>)}</dl> : <p>Số liệu sẽ hiển thị khi tổng quan được tải.</p>}</div>
        </section>
        <section aria-labelledby="workspace-campaigns">
          <h2 id="workspace-campaigns">Tình hình chiến dịch</h2>
          <div className="b2-admin-list"><WorkspaceIllustration kind="megaphone" />
          {stats.isSuccess && campaignStates.some(([state]) => hasValue(summary?.campaignsByStatus?.[state]))
            ? <dl>{campaignStates.filter(([state]) => hasValue(summary?.campaignsByStatus?.[state])).map(([state, label]) => <div key={state}><dt>{label}</dt><dd>{number(summary?.campaignsByStatus?.[state])}</dd></div>)}</dl>
            : <p>{stats.isSuccess ? 'Chưa có số liệu trạng thái chiến dịch.' : 'Số liệu sẽ hiển thị khi tổng quan được tải.'}</p>}</div>
        </section>
      </div>
      <section className="b2-admin-transparency" aria-labelledby="workspace-transparency">
        <h2 id="workspace-transparency"><ShieldCheck size={23} aria-hidden="true" /> Minh bạch &amp; Kiểm soát</h2>
        <div>{shortcuts.map(([to, title, description, kind]) => <Link key={to} to={to}>
          <WorkspaceIllustration kind={kind} /><span><strong>{title}</strong><small>{description}</small></span><ArrowRight size={20} aria-hidden="true" />
        </Link>)}</div>
      </section>
      {(user?.roles?.includes('DONOR') || user?.roles?.includes('CAMPAIGN_CREATOR')) && <nav className="b2-admin-roles" aria-label="Không gian theo vai trò">
        {user.roles.includes('DONOR') && <Link to="/donor">Quản lý đóng góp của tôi <ArrowUpRight size={16} aria-hidden="true" /></Link>}
        {user.roles.includes('CAMPAIGN_CREATOR') && <Link to="/creator">Quản lý chiến dịch Creator <ArrowUpRight size={16} aria-hidden="true" /></Link>}
      </nav>}
    </Container>
  </div>;
}
