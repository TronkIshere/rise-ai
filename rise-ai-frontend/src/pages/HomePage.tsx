import { ArrowRight, ArrowUpRight, HeartHandshake, SearchCheck, ShieldCheck, Sprout, Waypoints } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { listActiveCampaigns } from '@/api/campaigns';
import { Container } from '@/components/layout/Container';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { CampaignCard } from './CampaignCard';

const process = [
  { step: '01', icon: HeartHandshake, title: 'Khám phá & đồng hành', body: 'Xem mục tiêu, tổ chức thực hiện và tình trạng chiến dịch trước khi tham gia.' },
  { step: '02', icon: Waypoints, title: 'Theo dõi từng cột mốc', body: 'Kế hoạch sử dụng ngân sách được chia theo giai đoạn và cập nhật trạng thái.' },
  { step: '03', icon: SearchCheck, title: 'Giám sát & đối chiếu', body: 'Các bước phê duyệt do con người thực hiện; AI chỉ đưa ra cảnh báo tham khảo.' },
];

export function HomePage() {
  const featured = useQuery({
    queryKey: ['public-campaigns', 'featured', 3],
    queryFn: () => listActiveCampaigns({ size: 3 }),
  });

  return <>
    <section className="relative isolate overflow-hidden bg-brand-ink text-white">
      <div className="pointer-events-none absolute -right-32 -top-36 size-[34rem] rounded-full border border-white/10" aria-hidden="true" />
      <div className="pointer-events-none absolute -right-10 -top-12 size-[24rem] rounded-full border border-white/10" aria-hidden="true" />
      <Container className="relative grid items-center gap-14 py-20 lg:grid-cols-[1.13fr_.87fr] lg:gap-16 lg:py-28">
        <div>
          <span className="inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-4 py-2 text-xs font-semibold tracking-wide text-white">
            <Sprout size={16} aria-hidden="true" /> CÙNG NUÔI DƯỠNG NIỀM TIN
          </span>
          <h1 className="mt-7 max-w-3xl text-4xl font-extrabold leading-tight tracking-tight sm:text-5xl lg:text-6xl">
            Khi lòng tốt có <span className="text-soft-mint">thể được theo dõi</span>, niềm tin sẽ lớn lên.
          </h1>
          <p className="mt-7 max-w-xl text-base leading-8 text-white/80">
            Khám phá những chiến dịch cộng đồng, tìm hiểu kế hoạch sử dụng nguồn lực và theo dõi tiến độ từng cột mốc trên Rise AI.
          </p>
          <div className="mt-9 flex flex-wrap gap-3">
            <Link to="/campaigns" className="inline-flex min-h-12 items-center gap-2 rounded-xl bg-white px-6 py-3 text-sm font-bold text-brand-ink transition-colors hover:bg-soft-mint">
              Khám phá chiến dịch <ArrowRight size={17} aria-hidden="true"/>
            </Link>
            <a href="#how-it-works" className="inline-flex min-h-12 items-center gap-2 rounded-xl border border-white/30 px-6 py-3 text-sm font-semibold text-white hover:bg-white/10">
              Rise AI hoạt động thế nào?
            </a>
          </div>
          <p className="mt-7 text-xs leading-6 text-white/65">Bản demo mô phỏng · Không thu hoặc chuyển tiền thật · AI không tự duyệt giải ngân</p>
        </div>
        <div className="relative mx-auto w-full max-w-lg" aria-label="Quy trình minh bạch của Rise AI">
          <div className="absolute -inset-4 rotate-3 rounded-[2rem] border border-white/15" aria-hidden="true"/>
          <div className="relative rounded-[1.8rem] border border-white/20 bg-white/10 p-6 shadow-2xl backdrop-blur-sm sm:p-8">
            <div className="flex items-center justify-between border-b border-white/15 pb-5">
              <div className="flex items-center gap-3"><span className="flex size-11 items-center justify-center rounded-xl bg-white/15"><HeartHandshake aria-hidden="true"/></span><span className="font-bold">Hành trình minh bạch</span></div>
              <span className="rounded-full bg-soft-mint px-3 py-1 text-xs font-bold text-brand-ink">Quy trình mô phỏng</span>
            </div>
            <ol className="mt-6 space-y-5">
              {process.map(({ step, title, body }) => <li key={step} className="flex gap-4">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-soft-mint font-bold text-brand-ink">{step}</span>
                <div><h2 className="font-bold">{title}</h2><p className="mt-1 text-sm leading-6 text-white/70">{body}</p></div>
              </li>)}
            </ol>
            <div className="mt-7 flex items-center gap-3 rounded-xl bg-white/10 p-4 text-sm text-white/85">
              <ShieldCheck size={21} className="shrink-0 text-soft-mint" aria-hidden="true" />
              Quyết định cuối cùng thuộc về người quản trị có thẩm quyền.
            </div>
          </div>
        </div>
      </Container>
    </section>

    <section className="bg-surface py-16 sm:py-20" aria-labelledby="campaigns-heading">
      <Container>
        <div className="flex flex-wrap items-end justify-between gap-5">
          <div>
            <p className="text-xs font-extrabold uppercase tracking-[.17em] text-brand">Những hành trình đang tiếp diễn</p>
            <h2 id="campaigns-heading" className="mt-3 text-3xl font-extrabold tracking-tight text-brand-ink sm:text-4xl">Chiến dịch nổi bật</h2>
            <p className="mt-3 max-w-xl text-sm leading-7 text-muted">Dữ liệu chiến dịch được tải trực tiếp từ Backend Rise AI.</p>
          </div>
          <Link to="/campaigns" className="inline-flex min-h-11 items-center gap-2 font-bold text-brand hover:underline">Xem tất cả <ArrowUpRight size={17} aria-hidden="true" /></Link>
        </div>
        <div className="mt-9" aria-live="polite">
          {featured.isPending ? <StatusMessage title="Đang tải chiến dịch…" detail="Thông tin được cập nhật từ Backend." /> : null}
          {featured.isError ? <StatusMessage error title="Chưa tải được chiến dịch" detail={featured.error.message} /> : null}
          {featured.isSuccess && !featured.data.data?.length ? <StatusMessage title="Chưa có chiến dịch công khai" detail="Các chiến dịch được công bố sẽ xuất hiện tại đây." /> : null}
          {featured.isSuccess && !!featured.data.data?.length ? (
            <div className="grid items-stretch gap-6 md:grid-cols-2 xl:grid-cols-3">
              {featured.data.data.map((campaign) => <CampaignCard key={campaign.id ?? campaign.slug} campaign={campaign}/>)}
            </div>
          ) : null}
        </div>
      </Container>
    </section>

    <section id="how-it-works" className="border-y border-border bg-canvas py-16 sm:py-20" aria-labelledby="process-heading">
      <Container>
        <div className="mx-auto max-w-2xl text-center">
          <p className="text-xs font-extrabold uppercase tracking-[.17em] text-brand">Minh bạch là một quá trình</p>
          <h2 id="process-heading" className="mt-3 text-3xl font-extrabold text-brand-ink sm:text-4xl">Mỗi bước đều có ý nghĩa</h2>
          <p className="mt-4 text-sm leading-7 text-muted">Một hành trình từ mục tiêu công khai tới báo cáo kết quả. Đây là mô tả cách hệ thống vận hành, không phải chứng nhận về giao dịch thực tế.</p>
        </div>
        <div className="mt-11 grid gap-5 md:grid-cols-3">
          {process.map(({ step, icon: Icon, title, body }) => <div key={step} className="rounded-card border border-border bg-surface p-7">
            <div className="flex items-center justify-between"><span className="flex size-12 items-center justify-center rounded-2xl bg-soft-mint text-brand"><Icon size={23} aria-hidden="true"/></span><span className="text-sm font-extrabold text-muted">{step}</span></div>
            <h3 className="mt-6 text-xl font-bold text-brand-ink">{title}</h3>
            <p className="mt-3 text-sm leading-7 text-muted">{body}</p>
          </div>)}
        </div>
      </Container>
    </section>

    <section className="bg-surface py-16 sm:py-20">
      <Container>
        <div className="flex flex-col items-start justify-between gap-7 rounded-[1.75rem] bg-soft-mint p-8 sm:p-12 lg:flex-row lg:items-center">
          <div><h2 className="text-2xl font-bold text-brand-ink sm:text-3xl">Mỗi chiến dịch là một câu chuyện.</h2><p className="mt-3 max-w-xl text-sm leading-7 text-muted">Tìm hiểu tác động mong muốn, kế hoạch theo cột mốc và kết quả ghi nhận trong bản mô phỏng.</p></div>
          <Link to="/campaigns" className="inline-flex min-h-12 shrink-0 items-center gap-2 rounded-xl bg-brand px-6 py-3 text-sm font-bold text-white hover:bg-brand-hover">Tìm chiến dịch phù hợp <ArrowRight size={16} aria-hidden="true"/></Link>
        </div>
      </Container>
    </section>
  </>;
}
