import { ArrowUpRight, MapPin } from 'lucide-react';
import { Link } from 'react-router';
import type { CampaignResponse } from '@/api/contracts';
import { CampaignMedia } from '@/components/ui/CampaignMedia';
import { FundingProgress } from '@/components/ui/FundingProgress';
import { categoryLabel } from '@/lib/campaign-display';

export function CampaignCard({ campaign }: { campaign: CampaignResponse }) {
  const href = campaign.slug ? '/campaigns/' + encodeURIComponent(campaign.slug) : null;
  return (
    <article className="group flex h-full flex-col overflow-hidden rounded-card border border-border bg-surface shadow-sm transition-all duration-200 hover:-translate-y-1 hover:shadow-lg">
      <div className="relative h-48 overflow-hidden sm:h-52">
        <CampaignMedia src={campaign.coverImageUrl} alt={campaign.title ?? 'Ảnh chiến dịch'} className="h-full w-full transition-transform duration-300 group-hover:scale-[1.03]" />
        <span className="absolute left-4 top-4 rounded-full border border-white/80 bg-surface/95 px-3 py-1.5 text-xs font-semibold text-brand-ink shadow-sm">
          {categoryLabel(campaign.category)}
        </span>
      </div>
      <div className="flex flex-1 flex-col p-6">
        {campaign.location ? <p className="flex items-center gap-1.5 text-xs text-muted"><MapPin size={13} aria-hidden="true" />{campaign.location}</p> : null}
        <h3 className="mt-3 line-clamp-2 text-xl font-bold leading-snug text-brand-ink">{campaign.title ?? 'Chiến dịch chưa có tiêu đề'}</h3>
        <p className="mt-3 line-clamp-2 min-h-12 text-sm leading-6 text-muted">{campaign.shortDescription ?? 'Nội dung đang được cập nhật.'}</p>
        <div className="mt-auto pt-6">
          <FundingProgress raised={campaign.funding?.netRaised} target={campaign.targetAmount} currency={campaign.currency} compact />
          {href ? <Link to={href} className="mt-5 inline-flex min-h-11 items-center gap-2 font-semibold text-brand hover:underline">
            Khám phá chiến dịch <ArrowUpRight size={16} aria-hidden="true" />
          </Link> : <span className="mt-5 inline-block text-xs text-muted">Chưa có đường dẫn chi tiết</span>}
        </div>
      </div>
    </article>
  );
}
