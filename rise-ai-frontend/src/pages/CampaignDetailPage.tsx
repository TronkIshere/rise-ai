import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { ArrowLeft, ArrowUpRight, CalendarDays, CheckCircle2, HeartHandshake, MapPin, ShieldCheck, UsersRound } from 'lucide-react';
import { ApiError } from '@/api/client';
import { getCampaignBySlug, listCampaignMilestones } from '@/api/campaigns';
import type { CampaignResponse } from '@/api/contracts';
import { Container } from '@/components/layout/Container';
import { CampaignChainPanel, ChainBadge } from '@/pages/OnchainViews';
import { CampaignMedia } from '@/components/ui/CampaignMedia';
import { FundingProgress } from '@/components/ui/FundingProgress';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { amountLabel, categoryLabel, dateLabel, milestoneLabel, usableCampaignImageUrl, statusLabel } from '@/lib/campaign-display';

function DetailSection({ heading, children }: { heading: string; children?: string }) {
  if (!children?.trim()) return null;
  return <section className="campaign-flow-story-section">
    <h2>{heading}</h2>
    <p>{children}</p>
  </section>;
}

function DetailGallery({ campaign }: { campaign: CampaignResponse }) {
  const images = (campaign.galleryImageUrls ?? [])
    .map(usableCampaignImageUrl).filter((url): url is string => Boolean(url))
    .filter((url, index, all) => all.indexOf(url) === index).slice(0, 6);
  if (!images.length) return null;
  return <section className="campaign-flow-gallery" aria-labelledby="detail-gallery-heading">
    <h2 id="detail-gallery-heading">Hình ảnh chiến dịch</h2>
    <p>Ảnh được cung cấp trong hồ sơ chiến dịch, không thay thế việc thẩm định minh chứng.</p>
    <div className="campaign-flow-gallery-grid">
      {images.map((src, i) => <CampaignMedia key={src} src={src} alt={'Hình ảnh chiến dịch ' + (i + 1)} className="campaign-flow-gallery-image" />)}
    </div>
  </section>;
}

function DetailMilestones({ campaign }: { campaign: CampaignResponse }) {
  const campaignId = campaign.id;
  const milestones = useQuery({
    queryKey: ['campaign-milestones', campaignId],
    queryFn: () => listCampaignMilestones(campaignId!),
    enabled: Boolean(campaignId),
  });
  const entries = [...(milestones.data ?? [])].sort((a, b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0));
  return <section className="campaign-flow-milestones" aria-labelledby="detail-milestones-heading">
    <div className="campaign-flow-heading-row">
      <span className="campaign-flow-heading-icon" aria-hidden="true"><CheckCircle2 size={22}/></span>
      <div><h2 id="detail-milestones-heading">Hành trình theo cột mốc</h2>
        <p>Thông tin tiến độ được đồng bộ từ Backend, không phải minh chứng đã được xác thực độc lập.</p></div>
    </div>
    {!campaignId ? <StatusMessage title="Chưa công bố cột mốc" detail="Hồ sơ chưa có mã chiến dịch." /> : null}
    {campaignId && milestones.isPending ? <StatusMessage title="Đang tải cột mốc…" /> : null}
    {campaignId && milestones.isError ? <div className="campaign-flow-status"><StatusMessage error title="Không thể tải cột mốc" detail={milestones.error.message}/>
      <button type="button" onClick={() => void milestones.refetch()}>Thử tải lại cột mốc</button></div> : null}
    {campaignId && milestones.isSuccess && entries.length === 0 ? <StatusMessage title="Chưa có cột mốc" detail="Chưa có lộ trình công khai trong hệ thống." /> : null}
    {campaignId && milestones.isSuccess && entries.length > 0 ? <ol className="campaign-flow-milestone-list">
      {entries.map((item, index) => <li key={item.id ?? index} className="campaign-flow-milestone">
        <span className="campaign-flow-milestone-number">{String(item.sequenceNumber ?? index + 1).padStart(2, '0')}</span>
        <div className="campaign-flow-milestone-body">
          <div className="campaign-flow-milestone-head"><h3>{item.title ?? 'Cột mốc đang cập nhật'}</h3>
            <span>{milestoneLabel(item.milestoneStatus)}</span></div>
          {item.description ? <p className="campaign-flow-milestone-description">{item.description}</p> : null}
          <div className="campaign-flow-milestone-details">
            <span>Kinh phí dự kiến: {amountLabel(item.plannedAmount, campaign.currency)}</span>
            <span>Giải ngân mô phỏng: {amountLabel(item.releasedAmount, campaign.currency)}</span>
            {item.dueAt ? <span>Dự kiến: {dateLabel(item.dueAt)}</span> : null}
          </div>
        </div>
      </li>)}
    </ol> : null}
  </section>;
}

function DetailContent({ campaign, slug }: { campaign: CampaignResponse; slug: string }) {
  const active = campaign.campaignStatus === 'ACTIVE' && campaign.acceptingDonations === true;
  const title = campaign.title?.trim() || 'Hồ sơ chiến dịch chưa có tiêu đề';
  return <>
    <div className="campaign-flow-status-tags">
      <span>{categoryLabel(campaign.category)}</span>
      <span>{statusLabel(campaign.campaignStatus)}</span>
      <span className="campaign-flow-tag-warm">GIAO DỊCH MÔ PHỎNG</span>
      <ChainBadge />
    </div>
    <h1 className="campaign-flow-title">{title}</h1>
    {campaign.shortDescription ? <p className="campaign-flow-intro">{campaign.shortDescription}</p> : null}
    <div className="campaign-flow-facts">
      {campaign.location ? <span><MapPin size={17} aria-hidden="true"/>{campaign.location}</span> : null}
      {campaign.publishedAt ? <span><CalendarDays size={17} aria-hidden="true"/>Công bố: {dateLabel(campaign.publishedAt)}</span> : null}
      {campaign.organization?.name ? <span><HeartHandshake size={17} aria-hidden="true"/>{campaign.organization.name}</span> : null}
    </div>
    <div className="campaign-flow-columns">
      <div className="campaign-flow-primary">
        <div className="campaign-flow-cover">
          <CampaignMedia
            src={usableCampaignImageUrl(campaign.coverImageUrl)}
            alt={'Ảnh đại diện: ' + title}
            className="campaign-flow-cover-image"
            renderCaption={(hasImage) => <span className="campaign-flow-cover-caption">
              {hasImage ? 'ẢNH TỪ HỒ SƠ / KHÔNG THAY THẾ MINH CHỨNG' : 'MINH HỌA / CHƯA CÓ ẢNH ĐẠI DIỆN'}
            </span>}
          />
        </div>
        <div className="campaign-flow-story">
          <div className="campaign-flow-eyebrow">CÂU CHUYỆN &amp; TÁC ĐỘNG</div>
          <DetailSection heading="Câu chuyện chiến dịch">{campaign.fullDescription}</DetailSection>
          <DetailSection heading="Vấn đề cần giải quyết">{campaign.problemStatement}</DetailSection>
          <DetailSection heading="Giải pháp đề xuất">{campaign.solutionDescription}</DetailSection>
          <DetailSection heading="Tác động mong muốn">{campaign.impactGoals}</DetailSection>
          <DetailSection heading="Những người được hỗ trợ">{campaign.beneficiaryDescription}</DetailSection>
          {!campaign.fullDescription && !campaign.problemStatement && !campaign.solutionDescription && !campaign.impactGoals && !campaign.beneficiaryDescription
            ? <p className="campaign-flow-muted">Nội dung chi tiết đang được cập nhật từ Backend.</p> : null}
        </div>
        <DetailGallery campaign={campaign} />
        <DetailMilestones campaign={campaign} />
        {campaign.id ? <CampaignChainPanel campaignId={campaign.id} /> : null}
      </div>
      <aside className="campaign-flow-aside" aria-label="Tiến độ và hành động">
        <div className="campaign-flow-summary">
          <p className="campaign-flow-eyebrow">HỒ SƠ TÀI TRỢ / MÔ PHỎNG</p>
          <h2>Đồng hành cùng chiến dịch</h2>
          <FundingProgress raised={campaign.funding?.netRaised} target={campaign.targetAmount} currency={campaign.currency} />
          <div className="campaign-flow-summary-stats">
            <div><small>Đã giải ngân (mô phỏng)</small><strong>{amountLabel(campaign.funding?.totalDisbursed, campaign.currency)}</strong></div>
            <div><small>Người thụ hưởng dự kiến</small>
              <strong>{campaign.beneficiaryCount == null ? 'Chưa cập nhật' : new Intl.NumberFormat('vi-VN').format(campaign.beneficiaryCount)}</strong></div>
          </div>
          <p className="campaign-flow-disclaimer">Đây là hệ thống mô phỏng. Không nhận hoặc chuyển tiền thật. Mọi khoản đóng góp chỉ được ghi nhận qua Backend và chờ Admin xác nhận.</p>
          {active ? <Link className="campaign-flow-cta" to={'/campaigns/' + encodeURIComponent(slug) + '/contribute'}>
            Ghi nhận đóng góp mô phỏng <ArrowUpRight size={18} aria-hidden="true"/>
          </Link> : <p className="campaign-flow-unavailable" role="status">Chiến dịch hiện chưa tiếp nhận đóng góp.</p>}
          <Link className="campaign-flow-cta-secondary" to="/campaigns">Khám phá chiến dịch khác</Link>
        </div>
        {campaign.organization?.name ? <section className="campaign-flow-organization">
          <p className="campaign-flow-eyebrow">ĐƠN VỊ TRIỂN KHAI</p>
          <h2>{campaign.organization.name}</h2>
          {campaign.organization.verificationStatus === 'VERIFIED' ? <p className="campaign-flow-verified"><ShieldCheck size={17} aria-hidden="true"/>Đã xác minh trong hệ thống</p> : <p>Trạng thái xác minh chưa được công bố.</p>}
          <p>Trạng thái nội bộ không thay thế việc xác minh pháp lý.</p>
        </section> : null}
        <div className="campaign-flow-ai-note"><UsersRound size={22} aria-hidden="true"/>
          <p>AI chỉ cung cấp cảnh báo hỗ trợ. Các quyết định phê duyệt, giải ngân và xử lý khiếu nại thuộc về con người.</p>
        </div>
      </aside>
    </div>
  </>;
}

export function CampaignDetailPage() {
  const { slug } = useParams();
  const campaign = useQuery({
    queryKey: ['campaign', slug],
    queryFn: () => getCampaignBySlug(slug!),
    enabled: Boolean(slug),
  });
  const missing = campaign.error instanceof ApiError && campaign.error.status === 404;
  return <div className="campaign-flow campaign-detail-flow">
    <div className="campaign-flow-topband"><Container>
      <Link className="campaign-flow-back" to="/campaigns"><ArrowLeft size={17} aria-hidden="true"/> Trở về Campaign Discovery</Link>
    </Container></div>
    <Container className="campaign-flow-container">
      {!slug ? <StatusMessage error title="Đường dẫn chiến dịch không hợp lệ" /> : null}
      {slug && campaign.isPending ? <StatusMessage title="Đang tải hồ sơ chiến dịch…" /> : null}
      {slug && campaign.isError ? <div className="campaign-flow-retry" role="alert">
        <StatusMessage error title={missing ? 'Không tìm thấy chiến dịch' : 'Không thể tải chiến dịch'}
          detail={missing ? 'Hồ sơ không tồn tại hoặc chưa được công bố.' : campaign.error.message}/>
        <div className="campaign-flow-actions"><Link to="/campaigns">Quay về danh sách</Link>
          {!missing ? <button type="button" onClick={() => void campaign.refetch()}>Thử tải lại</button> : null}</div>
      </div> : null}
      {slug && campaign.isSuccess ? <DetailContent campaign={campaign.data} slug={slug} /> : null}
    </Container>
  </div>;
}
