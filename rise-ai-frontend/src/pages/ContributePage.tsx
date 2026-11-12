import { useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ArrowUpRight, CheckCircle2, HeartHandshake, LockKeyhole, ShieldAlert } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { ApiError } from '@/api/client';
import { createDonation } from '@/api/donor';
import { getCampaignBySlug } from '@/api/campaigns';
import type { CreateDonationRequest, DonationResponse, PaymentMethod } from '@/api/contracts';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { FundingProgress } from '@/components/ui/FundingProgress';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { amountLabel, categoryLabel } from '@/lib/campaign-display';
import { donationLabel, paymentMethods } from '@/lib/donor-display';

function ContributionFlow({ slug }: { slug: string }) {
  const { user } = useAuth();
  const cache = useQueryClient();
  const campaign = useQuery({
    queryKey: ['campaign', slug], queryFn: () => getCampaignBySlug(slug), enabled: Boolean(slug),
  });
  const [amount, setAmount] = useState('100000');
  const [method, setMethod] = useState<PaymentMethod>('SIMULATED_BANK_TRANSFER');
  const [message, setMessage] = useState('');
  const [anonymous, setAnonymous] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);
  const [result, setResult] = useState<DonationResponse | null>(null);
  const [validation, setValidation] = useState('');
  // An identical retry after a network timeout must reuse its server idempotency key.
  const pending = useRef<{ signature: string; key: string } | null>(null);
  const create = useMutation({
    mutationFn: ({ campaignId, payload, key }: { campaignId: string; payload: CreateDonationRequest; key: string }) =>
      createDonation(campaignId, payload, key),
    retry: false,
    onSuccess(data) {
      setResult(data);
      pending.current = null;
      void cache.invalidateQueries({ queryKey: ['donor', user?.id] });
      void cache.invalidateQueries({ queryKey: ['campaign', slug] });
      void cache.invalidateQueries({ queryKey: ['public-campaigns'] });
    },
  });
  const current = campaign.data;
  const allowed = current?.campaignStatus === 'ACTIVE' && current.acceptingDonations === true;
  const rawMinimum = current?.minimumDonationAmount ?? 1;
  const minimum = Number.isFinite(rawMinimum) && rawMinimum > 0 ? Math.ceil(rawMinimum) : 1;

  function updateForm(callback: () => void) {
    setValidation('');
    if (create.isError) create.reset();
    callback();
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!current?.id || !allowed || !user?.roles?.includes('DONOR') || create.isPending || result) return;
    const value = Number(amount);
    if (!Number.isSafeInteger(value) || value < minimum || value <= 0) {
      setValidation('Số tiền mô phỏng phải là số nguyên, tối thiểu ' + amountLabel(minimum, current.currency) + '.');
      return;
    }
    if (!acknowledged) {
      setValidation('Vui lòng xác nhận bạn hiểu đây chỉ là đóng góp mô phỏng.');
      return;
    }
    setValidation('');
    const payload: CreateDonationRequest = {
      amount: value, anonymous, currency: current.currency ?? 'VND',
      donorMessage: message.trim(), paymentMethod: method,
    };
    const signature = current.id + JSON.stringify(payload);
    if (!pending.current || pending.current.signature !== signature) {
      pending.current = { signature, key: crypto.randomUUID() };
    }
    try {
      await create.mutateAsync({ campaignId: current.id, payload, key: pending.current.key });
    } catch { /* Preserve the idempotency key. Error is visible below the form. */ }
  }

  return <div className="campaign-flow campaign-contribution-flow">
    <div className="campaign-flow-topband"><Container>
      <Link className="campaign-flow-back" to={'/campaigns/' + encodeURIComponent(slug)}>
        <ArrowLeft size={17} aria-hidden="true"/> Về hồ sơ chiến dịch
      </Link>
    </Container></div>
    <Container className="campaign-flow-container">
      <div className="campaign-flow-status-tags"><span>GHI NHẬN MÔ PHỎNG</span><span className="campaign-flow-tag-warm">KHÔNG THANH TOÁN THẬT</span></div>
      <h1 className="campaign-flow-title">Đồng hành cùng một sáng kiến</h1>
      <p className="campaign-flow-intro">Tạo yêu cầu đóng góp mô phỏng để theo dõi tiến độ trên Rise AI. Không nhập thông tin thẻ, không chuyển tiền và không phát sinh giao dịch thực tế.</p>
      <div className="campaign-flow-contribution-grid">
        <div className="campaign-flow-donation-paper">
          <div className="campaign-flow-eyebrow">BƯỚC 01 / KIỂM TRA CHIẾN DỊCH</div>
          {campaign.isPending ? <StatusMessage title="Đang tải thông tin chiến dịch…" /> : null}
          {campaign.isError ? <div className="campaign-flow-retry" role="alert">
            <StatusMessage error title="Không thể tải chiến dịch" detail={campaign.error.message}/>
            <button type="button" onClick={() => void campaign.refetch()}>Thử tải lại</button>
          </div> : null}
          {campaign.isSuccess && current ? <>
            <div className="campaign-flow-contribution-heading">
              <span className="campaign-flow-category">{categoryLabel(current.category)}</span>
              <h2>{current.title?.trim() || 'Chiến dịch đang cập nhật tên'}</h2>
              <p>Mục tiêu mô phỏng: {amountLabel(current.targetAmount, current.currency)}</p>
            </div>
            {result ? <div className="campaign-flow-success" role="status">
              <CheckCircle2 size={33} aria-hidden="true" />
              <h2>Đã gửi yêu cầu đóng góp mô phỏng</h2>
              <p>Mã yêu cầu: <strong>{result.donationCode ?? result.id ?? 'Chưa cập nhật'}</strong></p>
              <p>Trạng thái Backend: <strong>{donationLabel(result.donationStatus)}</strong></p>
              <p>Yêu cầu không đồng nghĩa đã xác nhận. Chỉ quản trị viên được xử lý và xác nhận trên Backend.</p>
              <div className="campaign-flow-actions"><Link to="/donor">Xem lịch sử đóng góp <ArrowUpRight size={16} aria-hidden="true"/></Link>
                <Link to={'/campaigns/' + encodeURIComponent(slug)}>Về hồ sơ chiến dịch</Link></div>
            </div> : !allowed ? <div className="campaign-flow-retry" role="status"><StatusMessage
              title="Chiến dịch hiện không nhận đóng góp"
              detail="Backend không cho phép tạo yêu cầu tại trạng thái này. Hãy chọn một chiến dịch ACTIVE đang tiếp nhận đóng góp."/>
              <Link to="/campaigns">Xem chiến dịch khác</Link></div>
            : <form className="campaign-flow-donation-form" onSubmit={(event) => void submit(event)}>
              <div className="campaign-flow-eyebrow">BƯỚC 02 / GHI NHẬN YÊU CẦU</div>
              <Input id="donation-amount" label={'Số tiền mô phỏng (' + (current.currency ?? 'VND') + ')'}
                type="number" inputMode="numeric" min={minimum} max={Number.MAX_SAFE_INTEGER} step="1"
                required value={amount} onChange={(e) => updateForm(() => setAmount(e.target.value))}/>
              <p className="campaign-flow-field-help">Tối thiểu: {amountLabel(minimum, current.currency)}. Đây là con số ghi nhận trong mô phỏng.</p>
              <div><label className="campaign-flow-label" htmlFor="donation-method">Phương thức giả lập</label>
                <select id="donation-method" value={method} onChange={(e) => updateForm(() => setMethod(e.target.value as PaymentMethod))} className="campaign-flow-field">
                  {paymentMethods.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
                </select></div>
              <div><label className="campaign-flow-label" htmlFor="donation-message">Lời nhắn (không bắt buộc)</label>
                <textarea id="donation-message" rows={3} maxLength={1000} value={message}
                  onChange={(e) => updateForm(() => setMessage(e.target.value))} className="campaign-flow-field"/>
                <p className="campaign-flow-field-help">{message.length} / 1000 ký tự</p></div>
              <label className="campaign-flow-checkbox"><input type="checkbox" checked={anonymous} onChange={(e) => updateForm(() => setAnonymous(e.target.checked))}/>
                <span>Ẩn tên trên danh sách đóng góp công khai.</span></label>
              <label className="campaign-flow-checkbox campaign-flow-consent"><input type="checkbox" checked={acknowledged}
                onChange={(e) => updateForm(() => setAcknowledged(e.target.checked))}/>
                <span>Tôi hiểu đây là yêu cầu mô phỏng, không chuyển tiền thật và cần Admin xác nhận trên Backend.</span></label>
              {validation ? <p role="alert" className="campaign-flow-error">{validation}</p> : null}
              {create.isError ? <div role="alert" className="campaign-flow-error">
                <p>Không ghi nhận được yêu cầu: {create.error instanceof ApiError && create.error.status === 401
                  ? 'Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.'
                  : create.error.message}</p>
                <p>Bạn có thể thử lại với cùng mã yêu cầu để tránh tạo trùng.</p></div> : null}
              <Button type="submit" className="campaign-flow-submit"
                loading={create.isPending} disabled={!user?.roles?.includes('DONOR') || !acknowledged || Boolean(result)}>
                <LockKeyhole size={17} aria-hidden="true"/> Gửi yêu cầu mô phỏng
              </Button>
              <p className="campaign-flow-field-help" role="note">Không nhập dữ liệu ngân hàng, thẻ hoặc thông tin thanh toán thực.</p>
            </form>}
          </> : null}
        </div>
        <aside className="campaign-flow-contribution-aside" aria-label="Tóm tắt và lưu ý an toàn">
          <span className="campaign-flow-heading-icon" aria-hidden="true"><HeartHandshake size={23}/></span>
          <h2>Hành trình đóng góp minh bạch</h2>
          {current ? <div className="campaign-flow-aside-progress"><FundingProgress raised={current.funding?.netRaised} target={current.targetAmount} currency={current.currency}/></div> : null}
          <div className="campaign-flow-safety"><ShieldAlert size={23} aria-hidden="true"/><p>Đây không phải cổng thanh toán. Khoản đóng góp sẽ ở trạng thái chờ Admin xác nhận. Có thể hủy khi còn PENDING; yêu cầu hoàn tiền chỉ áp dụng theo chính sách với khoản đã xác nhận.</p></div>
          <Link className="campaign-flow-cta-secondary" to="/campaigns">Xem thêm sáng kiến cộng đồng</Link>
        </aside>
      </div>
    </Container>
  </div>;
}

/** Keyed by slug so result/idempotency state never leaks across campaigns. */
export function ContributePage() {
  const { slug } = useParams();
  if (!slug) return <Container className="py-16"><StatusMessage error title="Đường dẫn chiến dịch không hợp lệ"/>
    <Link to="/campaigns" className="mt-6 inline-block text-brand underline">Xem chiến dịch khác</Link></Container>;
  return <ContributionFlow key={slug} slug={slug}/>;
}
