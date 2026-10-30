import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { getCampaignBySlug } from '@/api/campaigns';
import { Container } from '@/components/layout/Container';
import { Card } from '@/components/ui/Card';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { formatVnd } from '@/lib/format';

export function CampaignDetailPage() {
  const { slug } = useParams();
  const query = useQuery({
    queryKey: ['campaign', slug],
    queryFn: () => getCampaignBySlug(slug!),
    enabled: Boolean(slug),
  });
  return <Container className="py-14">
    <Link to="/campaigns" className="text-sm font-semibold text-brand underline underline-offset-4">← Danh sách chiến dịch</Link>
    {query.isPending ? <div className="mt-8"><StatusMessage title="Đang tải chi tiết…" /></div> : null}
    {query.isError ? <div className="mt-8"><StatusMessage error title="Không tải được chiến dịch" detail={query.error.message} /></div> : null}
    {query.isSuccess ? <>
      <p className="mt-8 text-sm font-semibold text-brand">{query.data.category ?? 'Chiến dịch từ thiện'}</p>
      <h1 className="mt-3 text-3xl font-bold sm:text-5xl">{query.data.title ?? 'Chưa có tiêu đề'}</h1>
      <p className="mt-5 max-w-3xl leading-7 text-muted">{query.data.shortDescription ?? 'Chưa có mô tả'}</p>
      <Card className="mt-8 max-w-xl">
        <p className="text-sm text-muted">Mục tiêu tài trợ (mô phỏng)</p>
        <p className="mt-2 text-3xl font-bold text-brand">{formatVnd(query.data.targetAmount)}</p>
        <p className="mt-4 text-sm text-muted">Trạng thái: {query.data.campaignStatus ?? 'Chưa xác định'}</p>
        <p className="mt-4 text-xs text-muted">FE-00 không thực hiện thanh toán hoặc xác thực tài liệu thực.</p>
      </Card>
    </> : null}
  </Container>;
}
