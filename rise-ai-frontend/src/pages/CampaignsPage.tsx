import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { listActiveCampaigns } from '@/api/campaigns';
import { Container } from '@/components/layout/Container';
import { Card } from '@/components/ui/Card';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { formatVnd } from '@/lib/format';

export function CampaignsPage() {
  const query = useQuery({ queryKey: ['campaigns', 'ACTIVE', 0], queryFn: listActiveCampaigns });
  return <Container className="py-14">
    <h1 className="text-3xl font-bold">Chiến dịch đang hoạt động</h1>
    <p className="mt-3 text-sm text-muted">Thông tin được tải từ Backend, không dùng dữ liệu chiến dịch giả.</p>
    <div className="mt-8">
      {query.isPending ? <StatusMessage title="Đang tải chiến dịch…" /> : null}
      {query.isError ? <StatusMessage error title="Không tải được chiến dịch" detail={query.error.message} /> : null}
      {query.isSuccess && !query.data.data?.length ? <StatusMessage title="Chưa có chiến dịch" detail="Chiến dịch sẽ xuất hiện khi Backend công bố dữ liệu." /> : null}
      {query.isSuccess && query.data.data?.length ? <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {query.data.data.map((item) => <Card key={item.id ?? item.slug ?? item.title}>
          <h2 className="text-lg font-bold">{item.title ?? 'Chưa có tiêu đề'}</h2>
          <p className="mt-3 line-clamp-3 text-sm leading-6 text-muted">{item.shortDescription ?? 'Chưa có mô tả'}</p>
          <p className="mt-4 text-sm font-bold text-brand">Mục tiêu mô phỏng: {formatVnd(item.targetAmount)}</p>
          {item.slug ? <Link to={'/campaigns/' + encodeURIComponent(item.slug)} className="mt-4 inline-block text-sm font-semibold text-brand underline underline-offset-4">Xem chi tiết</Link> : null}
        </Card>)}
      </div> : null}
    </div>
  </Container>;
}
