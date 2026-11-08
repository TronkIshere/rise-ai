import { Link } from 'react-router';
import type { CampaignResponse } from '@/api/contracts';
import { CampaignMedia } from '@/components/ui/CampaignMedia';
import { amountLabel, categoryLabel, progressLabel, progressValue, safeImageUrl } from '@/lib/campaign-display';
import { isSyntheticPlaceholderMedia } from '@/lib/b2-campaign-media';

function campaignLink(campaign: CampaignResponse): string | undefined {
  return campaign.slug ? `/campaigns/${encodeURIComponent(campaign.slug)}` : undefined;
}

function approvedCampaignCover(campaign: CampaignResponse): string | undefined {
  const source = safeImageUrl(campaign.coverImageUrl);
  // The demo seeder uses placehold.co; it is neither artwork nor evidence
  // provided by a campaign. Use the approved B2 concept fallback instead.
  return source && !isSyntheticPlaceholderMedia(source) ? source : undefined;
}

function FundingMeter({ campaign, compact = false }: { campaign: CampaignResponse; compact?: boolean }) {
  const raised = campaign.funding?.netRaised;
  const target = campaign.targetAmount;
  const hasNumbers = typeof raised === 'number' && Number.isFinite(raised) && typeof target === 'number' && Number.isFinite(target) && target > 0;
  return <>
    <div className={compact ? 'compact-number' : 'funding-values'}>
      <b>{hasNumbers ? amountLabel(raised, campaign.currency) : 'Chưa có số liệu'}</b>
      <span>{hasNumbers ? '/ ' + amountLabel(target, campaign.currency) : 'Tiến độ chưa cập nhật'}</span>
      {compact && hasNumbers ? <strong>{progressLabel(raised, target)}</strong> : null}
    </div>
    {hasNumbers ? <progress className="b2-api-progress" max={100} value={progressValue(raised, target)} aria-label="Tiến độ huy động mô phỏng" /> : null}
    {!compact && hasNumbers ? <div className="progress-label"><b>{progressLabel(raised, target)} mục tiêu</b><span>Không phải tiến độ triển khai</span></div> : null}
  </>;
}

function ApprovedMedia({ campaign, fallback }: { campaign: CampaignResponse; fallback: 'campaign-hope.jpg' | 'campaign-library.jpg' | 'campaign-learning.jpg' }) {
  const src = approvedCampaignCover(campaign);
  return src
    ? <CampaignMedia src={src} alt={campaign.title || 'Ảnh chiến dịch từ hệ thống'} className="b2-campaign-media" />
    : <img src={'/b2-home/' + fallback} alt="Hình ảnh concept minh họa, không phải bằng chứng hoặc ảnh chiến dịch" loading="lazy" />;
}

function MainCampaign({ campaign }: { campaign: CampaignResponse }) {
  const to = campaignLink(campaign);
  return <article className="campaign-main campaign-card">
    <div className="campaign-main-media">
      <ApprovedMedia campaign={campaign} fallback="campaign-hope.jpg" />
      <span className="media-mark">{approvedCampaignCover(campaign) ? 'ẢNH TRONG HỒ SƠ CHIẾN DỊCH' : <>CONCEPT IMAGE<br />KHÔNG PHẢI MINH CHỨNG</>}</span>
    </div>
    <div className="campaign-main-body">
      <div className="campaign-meta"><span className="tag tag-peach">{categoryLabel(campaign.category)}</span><span className="tag tag-border">ACTIVE · TỪ BACKEND</span></div>
      <h3>{campaign.title || 'Chiến dịch chưa có tiêu đề'}</h3>
      <p className="campaign-para">{campaign.shortDescription || 'Nội dung đang được cập nhật.'}</p>
      <dl className="campaign-keyfacts">
        <div><dt>Đơn vị thực hiện</dt><dd>{campaign.organization?.name || 'Chưa cập nhật'}</dd></div>
        <div><dt>Đối tượng thụ hưởng</dt><dd>{campaign.beneficiaryDescription || (typeof campaign.beneficiaryCount === 'number' ? `${campaign.beneficiaryCount} người dự kiến` : 'Chưa cập nhật')}</dd></div>
      </dl>
      <div className="funding-panel"><span className="money-caption">TIẾN ĐỘ ĐÓNG GÓP · MÔ PHỎNG</span><FundingMeter campaign={campaign} /></div>
      {to ? <Link className="card-link" to={to}>Xem hồ sơ chiến dịch <span aria-hidden="true">→</span></Link> : <span className="card-link">Chưa có liên kết chiến dịch</span>}
    </div>
  </article>;
}

function CompactCampaign({ campaign, index }: { campaign: CampaignResponse; index: number }) {
  const to = campaignLink(campaign);
  return <article className="campaign-compact campaign-card">
    <div className="compact-top">
      <div className="compact-media"><ApprovedMedia campaign={campaign} fallback={index === 0 ? 'campaign-library.jpg' : 'campaign-learning.jpg'} /><span>{approvedCampaignCover(campaign) ? 'ẢNH HỒ SƠ CHIẾN DỊCH' : 'ẢNH MINH HỌA'}</span></div>
      <div className="compact-copy">
        <span className={'tag ' + (index === 0 ? 'tag-warm' : 'tag-blue')}>{categoryLabel(campaign.category)} · ACTIVE</span>
        <h3>{to ? <Link to={to}>{campaign.title || 'Chiến dịch chưa có tiêu đề'}</Link> : campaign.title || 'Chiến dịch chưa có tiêu đề'}</h3>
        <p>{campaign.shortDescription || 'Đang cập nhật thông tin.'}</p>
        <small>{campaign.organization?.name || 'Đơn vị chưa cập nhật'}</small>
      </div>
    </div>
    <div className="compact-funding"><FundingMeter campaign={campaign} compact /><span className="compact-legal">ĐÓNG GÓP MÔ PHỎNG · KHÔNG PHẢI GIẢI NGÂN</span></div>
  </article>;
}

export function B2CampaignCards({ campaigns }: { campaigns: CampaignResponse[] }) {
  const [first, ...others] = campaigns;
  if (!first) return null;
  return <>
    <MainCampaign campaign={first} />
    <div className="campaign-compacts">
      {others.slice(0, 2).map((campaign, i) => <CompactCampaign key={campaign.id || campaign.slug || i} campaign={campaign} index={i} />)}
      {others.length < 2 ? <div className="b2-no-more-campaigns">Chưa có thêm chiến dịch công khai trong danh mục này.</div> : null}
    </div>
  </>;
}
