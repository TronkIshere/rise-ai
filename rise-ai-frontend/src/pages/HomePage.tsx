import { ArrowRight, ClipboardCheck, HeartHandshake, ShieldCheck } from 'lucide-react';
import { Link } from 'react-router';
import { Container } from '@/components/layout/Container';
import { Card } from '@/components/ui/Card';

const values = [
  { Icon: HeartHandshake, title: 'Đóng góp có mục đích', body: 'Theo dõi chiến dịch và cột mốc được công bố.' },
  { Icon: ClipboardCheck, title: 'Giải ngân theo tiến độ', body: 'Bằng chứng và các bước phê duyệt được ghi nhận.' },
  { Icon: ShieldCheck, title: 'Giám sát có trách nhiệm', body: 'AI gợi ý dấu hiệu bất thường; con người ra quyết định.' },
];

export function HomePage() {
  return <>
    <section className="border-b border-border bg-gradient-to-br from-soft-mint via-surface to-soft-sand">
      <Container className="py-20 sm:py-28">
        <p className="text-sm font-bold text-brand">MINH BẠCH TRONG TỪNG ĐÓNG GÓP</p>
        <h1 className="mt-5 max-w-4xl text-4xl font-bold tracking-tight text-brand-ink sm:text-6xl">
          Niềm tin bắt đầu từ những điều có thể kiểm chứng.
        </h1>
        <p className="mt-6 max-w-2xl leading-8 text-muted">
          Rise AI hỗ trợ quản lý chiến dịch từ thiện, theo dõi tiến độ và giám sát giải ngân theo cột mốc.
        </p>
        <Link to="/campaigns" className="mt-8 inline-flex min-h-11 items-center gap-2 rounded-xl bg-brand px-5 py-2 text-sm font-semibold text-white hover:bg-brand-hover">
          Khám phá chiến dịch <ArrowRight size={16} aria-hidden="true"/>
        </Link>
        <p className="mt-5 text-xs text-muted">Giao dịch hiện chỉ là mô phỏng, không chuyển tiền thật.</p>
      </Container>
    </section>
    <Container className="py-16 sm:py-20">
      <h2 className="text-3xl font-bold">Cách Rise AI hỗ trợ minh bạch</h2>
      <div className="mt-8 grid gap-4 md:grid-cols-3">
        {values.map(({ Icon, title, body }) => <Card key={title}>
          <Icon size={27} className="text-brand" aria-hidden="true"/>
          <h3 className="mt-5 text-lg font-bold">{title}</h3>
          <p className="mt-2 text-sm leading-7 text-muted">{body}</p>
        </Card>)}
      </div>
    </Container>
  </>;
}
