import { useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CheckCircle2, ShieldAlert } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { createDonation } from '@/api/donor';
import { getCampaignBySlug } from '@/api/campaigns';
import type { CreateDonationRequest, DonationResponse, PaymentMethod } from '@/api/contracts';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { amountLabel } from '@/lib/campaign-display';
import { donationLabel, paymentMethods } from '@/lib/donor-display';

export function ContributePage() {
  const { slug } = useParams();
  const { user } = useAuth();
  const cache = useQueryClient();
  const campaign = useQuery({
    queryKey: ['campaign', slug], queryFn: () => getCampaignBySlug(slug!), enabled: Boolean(slug),
  });
  const [amount, setAmount] = useState('100000');
  const [method, setMethod] = useState<PaymentMethod>('SIMULATED_BANK_TRANSFER');
  const [message, setMessage] = useState('');
  const [anonymous, setAnonymous] = useState(false);
  const [result, setResult] = useState<DonationResponse | null>(null);
  const [validation, setValidation] = useState('');
  // Preserve idempotency key across retries of the same payload (e.g. after a connection loss).
  const pending = useRef<{ signature: string; key: string } | null>(null);
  const create = useMutation({
    mutationFn: ({ campaignId, payload, key }: { campaignId: string; payload: CreateDonationRequest; key: string }) =>
      createDonation(campaignId, payload, key),
    onSuccess(data) {
      setResult(data);
      pending.current = null;
      void cache.invalidateQueries({ queryKey: ['donor', user?.id] });
      void cache.invalidateQueries({ queryKey: ['campaign', slug] });
      void cache.invalidateQueries({ queryKey: ['public-campaigns'] });
    },
  });
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!campaign.data?.id || !campaign.data.acceptingDonations || create.isPending) return;
    const value = Number(amount);
    const minimum = campaign.data.minimumDonationAmount ?? 1;
    if (!Number.isSafeInteger(value) || value < minimum || value <= 0) {
      setValidation('Số tiền mô phỏng phải là số nguyên, tối thiểu ' + amountLabel(minimum, campaign.data.currency) + '.');
      return;
    }
    setValidation('');
    const payload: CreateDonationRequest = {
      amount: value, anonymous, currency: campaign.data.currency ?? 'VND',
      donorMessage: message.trim(), paymentMethod: method,
    };
    const signature = campaign.data.id + JSON.stringify(payload);
    if (!pending.current || pending.current.signature !== signature) pending.current = { signature, key: crypto.randomUUID() };
    try {
      await create.mutateAsync({ campaignId: campaign.data.id, payload, key: pending.current.key });
    } catch { /* Error panel below; retry reuses idempotency key. */ }
  }
  return <Container className="py-10 sm:py-14">
    <Link to={slug ? '/campaigns/' + encodeURIComponent(slug) : '/campaigns'} className="inline-flex items-center gap-2 text-sm font-bold text-brand hover:underline"><ArrowLeft size={17} aria-hidden="true"/> Về chiến dịch</Link>
    <div className="mt-8 max-w-3xl">
      <p className="text-xs font-extrabold uppercase tracking-widest text-brand">DONOR · BẢN DEMO</p>
      <h1 className="mt-3 text-3xl font-extrabold text-brand-ink sm:text-4xl">Đóng góp mô phỏng</h1>
      <p className="mt-4 text-sm leading-7 text-muted">Đây không phải trang thanh toán. Không chuyển tiền, không lưu thông tin thẻ; khoản đóng góp mới chỉ được ghi ở trạng thái chờ Admin xác nhận.</p>
    </div>
    <div className="mt-8 grid gap-8 lg:grid-cols-[minmax(0,1fr)_320px]">
      <div className="rounded-card border border-border bg-surface p-6 sm:p-8">
        {campaign.isPending ? <StatusMessage title="Đang tải chiến dịch…"/> : null}
        {campaign.isError ? <StatusMessage error title="Không tải được chiến dịch" detail={campaign.error.message}/> : null}
        {campaign.isSuccess ? <>
          <h2 className="text-xl font-bold text-brand-ink">{campaign.data.title ?? 'Chiến dịch'}</h2>
          <p className="mt-2 text-xs text-muted">Mục tiêu: {amountLabel(campaign.data.targetAmount, campaign.data.currency)}</p>
          {campaign.data.campaignStatus !== 'ACTIVE' || !campaign.data.acceptingDonations ? <div className="mt-6"><StatusMessage title="Chiến dịch hiện không nhận đóng góp" detail="Backend xác định điều kiện tiếp nhận. Không thể gửi yêu cầu mới." /></div>
          : result ? <div className="mt-7 rounded-xl border border-border bg-soft-mint p-6" role="status">
            <CheckCircle2 size={28} className="text-brand" aria-hidden="true"/>
            <h3 className="mt-3 text-lg font-bold text-brand-ink">Đã ghi nhận yêu cầu mô phỏng</h3>
            <p className="mt-3 text-sm leading-7">Mã: {result.donationCode ?? result.id ?? 'Đang cập nhật'}</p>
            <p className="text-sm">Trạng thái Backend: <strong>{donationLabel(result.donationStatus)}</strong></p>
            <p className="mt-3 text-xs leading-6 text-muted">Không có khoản thanh toán thực tế nào được thực hiện. Việc xác nhận chỉ do Admin thao tác trên Backend.</p>
            <div className="mt-5"><Link to="/donor" className="inline-flex min-h-11 items-center rounded-xl bg-brand px-5 text-sm font-semibold text-white">Xem lịch sử đóng góp</Link></div>
          </div> : <form className="mt-7 grid gap-5" onSubmit={(event) => void submit(event)}>
            <Input id="donation-amount" label="Số tiền ghi nhận (VND)" type="number" inputMode="numeric" min={campaign.data.minimumDonationAmount ?? 1} step="1" required value={amount} onChange={e=>setAmount(e.target.value)}/>
            <div>
              <label className="mb-2 block text-sm font-semibold" htmlFor="donation-method">Phương thức giả lập</label>
              <select id="donation-method" value={method} onChange={e=>setMethod(e.target.value as PaymentMethod)} className="min-h-11 w-full rounded-xl border border-border bg-surface px-3 text-sm">
                {paymentMethods.map(item=><option key={item.value} value={item.value}>{item.label}</option>)}
              </select>
            </div>
            <div>
              <label className="mb-2 block text-sm font-semibold" htmlFor="donation-message">Lời nhắn (tùy chọn)</label>
              <textarea id="donation-message" maxLength={1000} rows={3} value={message} onChange={e=>setMessage(e.target.value)} className="w-full rounded-xl border border-border bg-surface p-3 text-sm"/>
            </div>
            <label className="flex items-start gap-3 text-sm text-copy"><input type="checkbox" checked={anonymous} onChange={e=>setAnonymous(e.target.checked)} className="mt-1 accent-brand" /> Ẩn tên người đóng góp trên danh sách công khai</label>
            {validation ? <p role="alert" className="text-sm text-danger">{validation}</p> : null}
            {create.isError ? <p role="alert" className="text-sm text-danger">{create.error.message} — có thể thử lại cùng yêu cầu.</p> : null}
            <Button type="submit" loading={create.isPending} disabled={!user?.roles?.includes('DONOR')}>Ghi nhận đóng góp mô phỏng</Button>
          </form>}
        </> : null}
      </div>
      <aside className="h-fit rounded-card bg-soft-sand p-6">
        <ShieldAlert className="text-warning" size={27} aria-hidden="true"/>
        <h2 className="mt-3 text-lg font-bold text-brand-ink">Lưu ý quan trọng</h2>
        <p className="mt-3 text-sm leading-7 text-muted">Không có giao dịch ngân hàng, thẻ hay ví điện tử thật. Sau khi gửi, khoản đóng góp sẽ ở trạng thái chờ xác nhận. Bạn có thể hủy khi vẫn còn PENDING; yêu cầu hoàn tiền chỉ dành cho khoản đã được xác nhận.</p>
      </aside>
    </div>
  </Container>;
}
