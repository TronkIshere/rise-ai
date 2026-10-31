import { ArrowLeft, CalendarDays, CheckCircle2, HeartHandshake, MapPin, ShieldCheck, UsersRound } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { getCampaignBySlug, listCampaignMilestones } from '@/api/campaigns';
import { Container } from '@/components/layout/Container';
import { CampaignMedia } from '@/components/ui/CampaignMedia';
import { FundingProgress } from '@/components/ui/FundingProgress';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { amountLabel, categoryLabel, dateLabel, milestoneLabel, statusLabel } from '@/lib/campaign-display';

function NarrativeSection({ heading, content }: { heading: string; content?: string }) {
  if (!content?.trim()) return null;
  return <section className="border-b border-border pb-8 last:border-0" aria-label={heading}>
    <h2 className="text-2xl font-extrabold text-brand-ink">{heading}</h2>
    <p className="mt-4 whitespace-pre-line text-sm leading-8 text-muted">{content}</p>
  </section>;
}

export function CampaignDetailPage() {
  const { slug } = useParams();
  const campaign = useQuery({
    queryKey: ['campaign', slug],
    queryFn: () => getCampaignBySlug(slug!),
    enabled: Boolean(slug),
  });
  const campaignId = campaign.data?.id;
  const milestones = useQuery({
    queryKey: ['campaign-milestones', campaignId],
    queryFn: () => listCampaignMilestones(campaignId!),
    enabled: Boolean(campaignId),
  });

  if (!slug) return <Container className="py-16"><StatusMessage error title="Đường dẫn chiến dịch không hợp lệ"/></Container>;

  return <>
    <section className="border-b border-border bg-soft-mint">
      <Container className="py-8">
        <Link to="/campaigns" className="inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-brand hover:underline"><ArrowLeft size={17} aria-hidden="true"/> Trở về danh sách</Link>
      </Container>
    </section>
    <Container className="py-10 sm:py-14" >
      {campaign.isPending ? <StatusMessage title="Đang tải nội dung chiến dịch…" /> : null}
      {campaign.isError ? <div className="space-y-4"><StatusMessage error title="Không thể tải chiến dịch" detail={campaign.error.message}/><Link to="/campaigns" className="text-sm font-bold text-brand underline">Xem các chiến dịch khác</Link></div> : null}
      {campaign.isSuccess ? <>
        <div className="mb-7 flex flex-wrap items-center gap-3 text-xs font-bold">
          <span className="rounded-full bg-soft-mint px-3 py-2 text-brand">{categoryLabel(campaign.data.category)}</span>
          <span className="rounded-full border border-border bg-surface px-3 py-2 text-muted">{statusLabel(campaign.data.campaignStatus)}</span>
          <span className="rounded-full bg-soft-sand px-3 py-2 text-warning">Bản mô phỏng</span>
        </div>
        <h1 className="max-w-4xl text-4xl font-extrabold leading-tight tracking-tight text-brand-ink sm:text-5xl">{campaign.data.title ?? 'Chưa có tiêu đề'}</h1>
        {campaign.data.shortDescription ? <p className="mt-5 max-w-3xl text-base leading-8 text-muted">{campaign.data.shortDescription}</p> : null}
        <div className="mt-6 flex flex-wrap gap-x-6 gap-y-3 text-sm text-muted">
          {campaign.data.location ? <span className="inline-flex items-center gap-2"><MapPin size={16} aria-hidden="true"/>{campaign.data.location}</span> : null}
          {campaign.data.publishedAt ? <span className="inline-flex items-center gap-2"><CalendarDays size={16} aria-hidden="true"/>Công bố: {dateLabel(campaign.data.publishedAt)}</span> : null}
          {campaign.data.organization?.name ? <span className="inline-flex items-center gap-2"><HeartHandshake size={16} aria-hidden="true"/>{campaign.data.organization.name}</span> : null}
        </div>
        <div className="mt-9 grid gap-8 lg:grid-cols-[minmax(0,1fr)_350px] lg:items-start">
          <div>
            <CampaignMedia src={campaign.data.coverImageUrl} alt={'Ảnh đại diện: ' + (campaign.data.title ?? 'Chiến dịch')} className="h-64 rounded-card sm:h-96" />
            <div className="mt-10 space-y-8">
              <NarrativeSection heading="Câu chuyện chiến dịch" content={campaign.data.fullDescription} />
              <NarrativeSection heading="Vấn đề cần giải quyết" content={campaign.data.problemStatement} />
              <NarrativeSection heading="Giải pháp đề xuất" content={campaign.data.solutionDescription} />
              <NarrativeSection heading="Tác động mong muốn" content={campaign.data.impactGoals} />
              {campaign.data.beneficiaryDescription ? <NarrativeSection heading="Những người được hỗ trợ" content={campaign.data.beneficiaryDescription} /> : null}
            </div>

            <section className="mt-12" aria-labelledby="milestones-title">
              <div className="flex items-center gap-3"><span className="flex size-11 items-center justify-center rounded-xl bg-soft-mint text-brand"><CheckCircle2 size={23} aria-hidden="true"/></span><div><h2 id="milestones-title" className="text-2xl font-extrabold text-brand-ink">Lộ trình theo cột mốc</h2><p className="mt-1 text-xs text-muted">Thông tin trạng thái từ Backend Rise AI</p></div></div>
              <div className="mt-7">
                {!campaignId ? <StatusMessage title="Chưa có dữ liệu cột mốc" detail="Backend chưa cung cấp mã chiến dịch."/> : null}
                {campaignId && milestones.isPending ? <StatusMessage title="Đang tải cột mốc…" /> : null}
                {campaignId && milestones.isError ? <StatusMessage error title="Không thể tải cột mốc" detail={milestones.error.message}/> : null}
                {campaignId && milestones.isSuccess && milestones.data.length === 0 ? <StatusMessage title="Chưa công bố cột mốc" detail="Chưa có dữ liệu lộ trình cho chiến dịch này." /> : null}
                {campaignId && milestones.isSuccess && milestones.data.length > 0 ? <ol className="space-y-4">
                  {[...milestones.data].sort((a,b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0)).map((milestone, index) => <li key={milestone.id ?? index} className="rounded-card border border-border bg-surface p-6">
                    <div className="flex flex-wrap items-start gap-4">
                      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-soft-mint text-sm font-extrabold text-brand">{String(milestone.sequenceNumber ?? index + 1).padStart(2, '0')}</span>
                      <div className="min-w-0 flex-1"><div className="flex flex-wrap items-center justify-between gap-2">
                        <h3 className="text-lg font-bold text-brand-ink">{milestone.title ?? 'Cột mốc chưa có tiêu đề'}</h3>
                        <span className="rounded-full bg-soft-sand px-3 py-1.5 text-xs font-semibold text-warning">{milestoneLabel(milestone.milestoneStatus)}</span>
                      </div>
                      {milestone.description ? <p className="mt-3 whitespace-pre-line text-sm leading-7 text-muted">{milestone.description}</p> : null}
                      <div className="mt-4 flex flex-wrap gap-x-6 gap-y-2 text-xs text-muted">
                        <span>Kinh phí dự kiến: {amountLabel(milestone.plannedAmount, campaign.data.currency)}</span>
                        <span>Đã giải ngân (mô phỏng): {amountLabel(milestone.releasedAmount, campaign.data.currency)}</span>
                        {milestone.dueAt ? <span>Dự kiến: {dateLabel(milestone.dueAt)}</span> : null}
                      </div></div>
                    </div>
                  </li>)}
                </ol> : null}
              </div>
            </section>
          </div>

          <aside className="space-y-5 lg:sticky lg:top-28" aria-label="Tóm tắt chiến dịch">
            <div className="rounded-card border border-border bg-surface p-7 shadow-sm">
              <p className="text-xs font-extrabold uppercase tracking-[.13em] text-brand">Tiến độ tài trợ</p>
              <div className="mt-5"><FundingProgress raised={campaign.data.funding?.netRaised} target={campaign.data.targetAmount} currency={campaign.data.currency}/></div>
              <div className="mt-6 grid grid-cols-2 gap-4 border-t border-border pt-5 text-sm">
                <div><p className="text-xs text-muted">Đã giải ngân (mô phỏng)</p><p className="mt-2 font-bold text-brand-ink">{amountLabel(campaign.data.funding?.totalDisbursed, campaign.data.currency)}</p></div>
                <div><p className="text-xs text-muted">Người thụ hưởng dự kiến</p><p className="mt-2 font-bold text-brand-ink">{campaign.data.beneficiaryCount == null ? 'Chưa cập nhật' : new Intl.NumberFormat('vi-VN').format(campaign.data.beneficiaryCount)}</p></div>
              </div>
              <div className="mt-6 rounded-xl bg-soft-sand p-4 text-xs leading-6 text-warning">
                Giao dịch và dòng tiền trong Rise AI hiện chỉ là mô phỏng. FE-02 chỉ ghi nhận yêu cầu đóng góp mô phỏng, không thanh toán tiền thật.
              </div>
              {campaign.data.campaignStatus === 'ACTIVE' && campaign.data.acceptingDonations
                ? <Link to={'/campaigns/' + encodeURIComponent(slug) + '/contribute'} className="mt-6 inline-flex min-h-11 w-full items-center justify-center rounded-xl bg-brand px-5 py-3 text-center text-sm font-bold text-white hover:bg-brand-hover">
                    Ghi nhận đóng góp mô phỏng
                  </Link>
                : <p className="mt-5 text-xs text-muted">Chiến dịch hiện chưa tiếp nhận đóng góp.</p>}
              <Link to="/campaigns" className="mt-4 inline-flex min-h-11 w-full items-center justify-center rounded-xl border border-border px-5 py-3 text-sm font-bold text-brand hover:bg-soft-mint">Khám phá chiến dịch khác</Link>
            </div>
            {campaign.data.organization?.name ? <div className="rounded-card border border-border bg-surface p-6">
              <p className="text-xs font-bold uppercase tracking-wide text-muted">Đơn vị triển khai</p>
              <p className="mt-3 text-lg font-bold text-brand-ink">{campaign.data.organization.name}</p>
              {campaign.data.organization.verificationStatus === 'VERIFIED' ? <p className="mt-3 flex items-center gap-2 text-xs text-brand"><ShieldCheck size={16} aria-hidden="true" />Đã xác minh trong hệ thống mô phỏng</p> : null}
              <p className="mt-3 text-xs leading-6 text-muted">Trạng thái xác minh trong ứng dụng không thay thế việc thẩm định pháp lý ngoài đời thực.</p>
            </div> : null}
            <div className="flex items-start gap-3 rounded-card bg-soft-mint p-5 text-xs leading-6 text-brand-ink"><UsersRound className="mt-1 shrink-0 text-brand" size={19} aria-hidden="true"/><p>AI Monitoring hỗ trợ phát hiện dấu hiệu cần xem xét. Mọi quyết định phê duyệt và giải ngân thuộc về người quản trị.</p></div>
          </aside>
        </div>
      </> : null}
    </Container>
  </>;
}
