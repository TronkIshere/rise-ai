import { Search, SlidersHorizontal, ChevronLeft, ChevronRight, RotateCcw } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import type { FormEvent } from 'react';
import { listActiveCampaigns } from '@/api/campaigns';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { categoryOptions } from '@/lib/campaign-display';
import { CampaignCard } from './CampaignCard';

export function CampaignsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const search = (searchParams.get('q') ?? '').trim().slice(0, 120);
  const selected = searchParams.get('category') ?? '';
  const category = categoryOptions.find((item) => item.value === selected)?.value;
  const pageParam = Number(searchParams.get('page') ?? '0');
  const page = Number.isInteger(pageParam) && pageParam >= 0 ? Math.min(pageParam, 10000) : 0;

  const campaigns = useQuery({
    queryKey: ['public-campaigns', 'search', search, category, page],
    queryFn: () => listActiveCampaigns({ q: search, category, page, size: 9 }),
  });

  function updateParams(next: { q?: string; category?: string; page?: number }) {
    const values = new URLSearchParams();
    const q = next.q ?? search;
    const cat = next.category ?? category;
    const pageValue = next.page ?? 0;
    if (q.trim()) values.set('q', q.trim().slice(0, 120));
    if (cat) values.set('category', cat);
    if (pageValue > 0) values.set('page', String(pageValue));
    setSearchParams(values);
  }
  function searchSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = String(new FormData(event.currentTarget).get('q') ?? '');
    updateParams({ q: query, page: 0 });
  }
  const totalPages = campaigns.data?.totalPages ?? 0;
  const totalResults = campaigns.data?.totalElements;
  const hasFilters = Boolean(search || category);

  return <>
    <section className="border-b border-border bg-soft-mint">
      <Container className="py-12 sm:py-16">
        <Link to="/" className="text-xs font-bold text-brand hover:underline">Trang chủ / Khám phá</Link>
        <div className="mt-5 flex flex-wrap items-start justify-between gap-5">
          <div>
            <p className="text-xs font-extrabold uppercase tracking-[.17em] text-brand">Tìm điều bạn quan tâm</p>
            <h1 className="mt-3 text-4xl font-extrabold tracking-tight text-brand-ink sm:text-5xl">Khám phá chiến dịch</h1>
            <p className="mt-4 max-w-2xl text-sm leading-7 text-muted">Tìm hiểu những hoạt động đang kêu gọi, mục tiêu tài trợ và tiến độ được ghi nhận từ hệ thống Rise AI.</p>
          </div>
          <span className="rounded-full border border-border bg-surface px-4 py-2 text-xs font-semibold text-brand-ink">Giao dịch mô phỏng</span>
        </div>
      </Container>
    </section>
    <Container className="py-10 sm:py-14">
      <div className="rounded-card border border-border bg-surface p-4 shadow-sm sm:p-6">
        <div className="flex items-center gap-2 text-sm font-bold text-brand-ink"><SlidersHorizontal size={18} aria-hidden="true"/> Tìm kiếm và lọc</div>
        <div className="mt-5 grid gap-4 lg:grid-cols-[1fr_230px]">
          <form onSubmit={searchSubmit} className="flex flex-wrap gap-2" role="search">
            <div className="relative min-w-44 flex-1">
              <Search className="pointer-events-none absolute left-3 top-3.5 text-muted" size={18} aria-hidden="true"/>
              <label htmlFor="campaign-query" className="sr-only">Tìm theo tên hoặc nội dung chiến dịch</label>
              <input
                key={search}
                id="campaign-query"
                name="q"
                type="search"
                maxLength={120}
                defaultValue={search}
                placeholder="Tên hoặc nội dung chiến dịch..."
                className="min-h-12 w-full rounded-xl border border-border bg-canvas py-3 pl-10 pr-3 text-sm placeholder:text-muted focus:border-brand"
              />
            </div>
            <Button type="submit">Tìm kiếm</Button>
          </form>
          <div>
            <label htmlFor="campaign-category" className="sr-only">Lọc theo lĩnh vực</label>
            <select id="campaign-category" value={category ?? ''} onChange={(e) => updateParams({ category: e.target.value, page: 0 })}
              className="min-h-12 w-full rounded-xl border border-border bg-canvas px-4 text-sm font-medium text-copy focus:border-brand">
              <option value="">Tất cả lĩnh vực</option>
              {categoryOptions.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
            </select>
          </div>
        </div>
        {hasFilters ? <button type="button" onClick={() => setSearchParams(new URLSearchParams())} className="mt-4 inline-flex min-h-10 items-center gap-2 text-xs font-bold text-brand hover:underline">
          <RotateCcw size={14} aria-hidden="true"/> Xóa bộ lọc
        </button> : null}
      </div>

      <div className="mt-10 flex flex-wrap items-center justify-between gap-3">
        <div><h2 className="text-2xl font-extrabold text-brand-ink">Chiến dịch đang hoạt động</h2>
          <p className="mt-1 text-sm text-muted" aria-live="polite">
            {campaigns.isSuccess && typeof totalResults === 'number' ? new Intl.NumberFormat('vi-VN').format(totalResults) + ' kết quả' : 'Dữ liệu công khai từ Backend'}
          </p>
        </div>
        <p className="text-xs text-muted">Chỉ hiển thị chiến dịch ACTIVE</p>
      </div>
      <div className="mt-6" aria-live="polite" aria-busy={campaigns.isPending}>
        {campaigns.isPending ? <StatusMessage title="Đang tìm chiến dịch…" detail="Kết quả đang được tải từ Backend Rise AI."/> : null}
        {campaigns.isError ? <div className="space-y-3"><StatusMessage error title="Không thể tải chiến dịch" detail={campaigns.error.message}/><Button variant="outline" onClick={() => void campaigns.refetch()}>Thử lại</Button></div> : null}
        {campaigns.isSuccess && !campaigns.data.data?.length ? <div className="space-y-4"><StatusMessage title={hasFilters ? 'Không tìm thấy chiến dịch phù hợp' : 'Chưa có chiến dịch công khai'} detail="Thử từ khóa hoặc lĩnh vực khác; không có dữ liệu giả được thêm vào kết quả." />{hasFilters ? <Button variant="outline" onClick={() => setSearchParams(new URLSearchParams())}>Xem toàn bộ chiến dịch</Button> : null}</div> : null}
        {campaigns.isSuccess && !!campaigns.data.data?.length ? <div className="grid items-stretch gap-6 md:grid-cols-2 xl:grid-cols-3">
          {campaigns.data.data.map((item) => <CampaignCard key={item.id ?? item.slug} campaign={item}/>)}
        </div> : null}
      </div>

      {campaigns.isSuccess && totalPages > 1 ? (
        <nav className="mt-10 flex flex-wrap items-center justify-center gap-4" aria-label="Phân trang chiến dịch">
          <Button variant="outline" disabled={page <= 0} onClick={() => updateParams({ page: page - 1 })}><ChevronLeft size={17} aria-hidden="true"/> Trước</Button>
          <span className="text-sm font-semibold text-muted">Trang {page + 1} / {totalPages}</span>
          <Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => updateParams({ page: page + 1 })}>Sau <ChevronRight size={17} aria-hidden="true"/></Button>
        </nav>
      ) : null}
    </Container>
  </>;
}
