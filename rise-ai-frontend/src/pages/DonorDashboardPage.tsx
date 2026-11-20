import { useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertCircle, ArrowUpRight, HandHeart, MessageCircleWarning, ReceiptText, RotateCcw } from 'lucide-react';
import { Link } from 'react-router';
import { cancelDonation, cancelRefund, getCampaignById, myDisputes, myDonations, myRefunds, openDispute, requestRefund } from '@/api/donor';
import type { CreateRefundRequest, DisputeType, DonationResponse } from '@/api/contracts';
import { useAuth } from '@/auth/auth-context';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { DonationReceiptNote } from '@/pages/OnchainViews';
import { Input } from '@/components/ui/Input';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { amountLabel, dateLabel } from '@/lib/campaign-display';
import { canRefund, disputeLabel, disputeTypeLabel, disputeTypes, donationLabel, isCountedDonation, refundLabel, refundableAmount } from '@/lib/donor-display';

type Tab = 'donations' | 'refunds' | 'disputes';
function QueryState({ query, empty, retry }: { query: { isPending: boolean; isError: boolean; error: Error | null }; empty: string; retry: ()=>void }) {
  if (query.isPending) return <StatusMessage title="Đang tải dữ liệu cá nhân…" />;
  if (query.isError) return <div className="space-y-3"><StatusMessage error title="Không tải được dữ liệu" detail={query.error?.message}/><Button variant="outline" onClick={retry}>Thử lại</Button></div>;
  return <StatusMessage title={empty} detail="Dữ liệu được tải từ Backend, không thêm bản ghi giả." />;
}

const donorTabs = [
  { id: 'donations', icon: HandHeart, label: 'Đóng góp' },
  { id: 'refunds', icon: ReceiptText, label: 'Hoàn tiền' },
  { id: 'disputes', icon: MessageCircleWarning, label: 'Phản ánh' },
] as const;

export function DonorDashboardPage() {
  const { user } = useAuth();
  const client = useQueryClient();
  const userKey = ['donor', user?.id] as const;
  const [tab, setTab] = useState<Tab>('donations');
  const [donationPage, setDonationPage] = useState(0);
  const [refundPage, setRefundPage] = useState(0);
  const [disputePage, setDisputePage] = useState(0);
  const [selectedDonation, setSelectedDonation] = useState<DonationResponse | null>(null);
  const [refundAmount, setRefundAmount] = useState('');
  const [refundReason, setRefundReason] = useState('');
  const [selectedCampaign, setSelectedCampaign] = useState('');
  const [disputeTitle, setDisputeTitle] = useState('');
  const [disputeDescription, setDisputeDescription] = useState('');
  const [disputeType, setDisputeType] = useState<DisputeType>('MISSING_EVIDENCE');
  const [notice, setNotice] = useState('');
  const [formError, setFormError] = useState('');
  const refundAttempts = useRef(new Map<string, string>());

  const donations = useQuery({ queryKey: [...userKey, 'donations', donationPage], queryFn: ()=>myDonations(donationPage) });
  const refunds = useQuery({ queryKey: [...userKey, 'refunds', refundPage], queryFn: ()=>myRefunds(refundPage), enabled: tab === 'refunds' });
  const disputes = useQuery({ queryKey: [...userKey, 'disputes', disputePage], queryFn: ()=>myDisputes(disputePage), enabled: tab === 'disputes' });

  const donationItems = useMemo(() => donations.data?.data ?? [], [donations.data?.data]);
  const campaignIds = useMemo(()=>Array.from(new Set(donationItems.map(x=>x.campaignId).filter((x): x is string=>Boolean(x)))),[donationItems]);
  const campaignQueries = useQueries({ queries: campaignIds.map(id=>({
    queryKey: ['campaign-id', id], queryFn: ()=>getCampaignById(id), staleTime: 60_000,
  })) });
  const campaignMap = new Map(campaignIds.map((id,i)=>[id,campaignQueries[i]?.data]));
  const eligibleCampaigns = Array.from(new Set(donationItems.filter(d=>isCountedDonation(d.donationStatus)).map(d=>d.campaignId).filter((id): id is string=>Boolean(id))));
  const invalidate = () => void client.invalidateQueries({ queryKey: userKey });
  const pendingPageCount = donationItems.filter(item => item.donationStatus === 'PENDING').length;
  const confirmedPageCount = donationItems.filter(item => isCountedDonation(item.donationStatus)).length;
  function changeDonationPage(next: number) {
    setDonationPage(next); setSelectedDonation(null); setSelectedCampaign(''); setFormError('');
  }

  const cancelPending = useMutation({
    mutationFn: cancelDonation,
    onSuccess() { setNotice('Đã hủy khoản đóng góp PENDING.'); invalidate(); },
  });
  const refund = useMutation({
    mutationFn: ({ id, payload, key }: { id:string; payload:CreateRefundRequest; key:string })=>requestRefund(id,payload,key),
    onSuccess() { setNotice('Đã gửi yêu cầu hoàn tiền mô phỏng; đang chờ Admin xem xét.'); setSelectedDonation(null); setRefundReason(''); refundAttempts.current.clear(); invalidate(); },
  });
  const cancelRequested = useMutation({
    mutationFn: cancelRefund,
    onSuccess() { setNotice('Đã hủy yêu cầu hoàn tiền.'); invalidate(); },
  });
  const dispute = useMutation({
    mutationFn: ({ campaignId, title, description, type }: {campaignId:string;title:string;description:string;type:DisputeType})=>
      openDispute(campaignId,{title,description,disputeType:type}),
    onSuccess() { setNotice('Đã ghi nhận phản ánh; người quản trị sẽ xem xét.'); setDisputeTitle('');setDisputeDescription('');setSelectedCampaign('');invalidate();setTab('disputes'); },
  });

  async function submitRefund(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedDonation?.id || refund.isPending) return;
    const value=Number(refundAmount);
    const maximum=refundableAmount(selectedDonation.amount,selectedDonation.refundedAmount);
    if (!Number.isSafeInteger(value) || value<=0 || value>maximum || !refundReason.trim()) {
      setFormError('Nhập số tiền nguyên hợp lệ, không vượt quá khoản còn được hoàn, và lý do cụ thể.');return;
    }
    setFormError('');
    const payload={amount:value,reason:refundReason.trim()};
    const signature=selectedDonation.id+JSON.stringify(payload);
    let key = refundAttempts.current.get(signature);
    if (!key) { key = crypto.randomUUID(); refundAttempts.current.set(signature,key); }
    try {await refund.mutateAsync({id:selectedDonation.id,payload,key});}catch{/* Show Backend error below. */}
  }
  async function submitDispute(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!eligibleCampaigns.includes(selectedCampaign) || dispute.isPending) return;
    if (!disputeTitle.trim() || !disputeDescription.trim()) {setFormError('Nhập tiêu đề và mô tả phản ánh.');return;}
    setFormError('');
    try { await dispute.mutateAsync({campaignId:selectedCampaign,title:disputeTitle.trim(),description:disputeDescription.trim(),type:disputeType}); }
    catch {/* Show Backend error below. */}
  }
  function resetNotice() {setNotice('');setFormError('');}
  function selectTab(next: Tab) {
    setTab(next);
    resetNotice();
  }
  function navigateTabs(event: KeyboardEvent<HTMLDivElement>) {
    const index = donorTabs.findIndex(item => item.id === tab);
    let next: number;
    switch (event.key) {
      case 'ArrowRight': next = (index + 1) % donorTabs.length; break;
      case 'ArrowLeft': next = (index + donorTabs.length - 1) % donorTabs.length; break;
      case 'Home': next = 0; break;
      case 'End': next = donorTabs.length - 1; break;
      default: return;
    }
    const item = donorTabs[next];
    if (!item) return;
    event.preventDefault();
    event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus();
    selectTab(item.id);
  }
  return <div className="rise-dashboard donor-dashboard"><Container className="dashboard-inner py-10 sm:py-14">
    <div className="dashboard-hero flex flex-wrap items-end justify-between gap-4">
      <div>
        <p className="text-xs font-bold uppercase tracking-widest text-brand">DONOR · KHÔNG GIAO DỊCH THẬT</p>
        <h1 className="mt-3 text-3xl font-extrabold text-brand-ink sm:text-4xl">Hành trình đóng góp</h1>
        <p className="mt-3 text-sm text-muted">Xin chào {user?.fullName ?? user?.email}. Theo dõi những khoản đã ghi nhận và yêu cầu hỗ trợ.</p>
      </div>
      <Link to="/campaigns" className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-brand px-5 text-sm font-bold text-white">Khám phá chiến dịch <ArrowUpRight size={16} aria-hidden="true"/></Link>
    </div>
    <section className="dashboard-metrics" aria-label="Tóm tắt hành trình">
      <div className="dashboard-metric"><span>Khoản đóng góp đã ghi nhận</span><strong>{donations.isSuccess && typeof donations.data.totalElements === 'number' ? new Intl.NumberFormat('vi-VN').format(donations.data.totalElements) : '—'}</strong><small>Toàn bộ lịch sử của tài khoản</small></div>
      <div className="dashboard-metric"><span>Đang chờ xác nhận</span><strong>{donations.isSuccess ? pendingPageCount : '—'}</strong><small>Trong trang lịch sử đang xem</small></div>
      <div className="dashboard-metric"><span>Đã xác nhận / ghi nhận hoàn</span><strong>{donations.isSuccess ? confirmedPageCount : '—'}</strong><small>Trong trang lịch sử đang xem</small></div>
      <div className="dashboard-metric"><span>Không được xác nhận</span><strong>{donations.isSuccess ? donationItems.filter(item=>item.donationStatus!=='PENDING'&&!isCountedDonation(item.donationStatus)).length : '—'}</strong><small>Trong trang lịch sử đang xem · dữ liệu Backend</small></div>
    </section>
    <div className="dashboard-disclaimer mt-7 rounded-xl border border-border bg-soft-sand p-4 text-xs leading-6 text-warning">
      Toàn bộ đóng góp, hoàn tiền và trạng thái tài chính ở đây chỉ là mô phỏng. Việc tạo đóng góp không đồng nghĩa đã được xác nhận.
    </div>
    <div className="dashboard-tabbar mt-8 flex flex-wrap gap-2 border-b border-border pb-4" role="tablist" aria-label="Quản lý đóng góp" onKeyDown={navigateTabs}>
      {donorTabs.map(({id,icon:Icon,label})=><button key={id} id={'dashboard-tab-'+id} aria-controls={'dashboard-panel-'+id} type="button" role="tab" aria-selected={tab===id} tabIndex={tab===id ? 0 : -1} onClick={()=>selectTab(id)} className={'inline-flex min-h-11 items-center gap-2 rounded-xl px-5 text-sm font-semibold ' + (tab===id?'bg-brand text-white':'bg-surface text-brand hover:bg-soft-mint')}><Icon size={17} aria-hidden="true"/>{label}</button>)}
    </div>
    {notice ? <p className="mt-5 rounded-xl bg-soft-mint p-4 text-sm text-brand-ink" role="status">{notice}</p> : null}
    {cancelPending.isError || cancelRequested.isError ? <p role="alert" className="mt-5 text-sm text-danger">{(cancelPending.error ?? cancelRequested.error)?.message}</p> : null}

    <section hidden={tab!=='donations'} id="dashboard-panel-donations" className="dashboard-section mt-7" role="tabpanel" aria-labelledby="dashboard-tab-donations">
      <h2 className="text-xl font-bold text-brand-ink">Lịch sử đóng góp của tôi</h2>
      <div className="mt-5 space-y-4">
        {donations.isSuccess && donationItems.length ? donationItems.map(item=><article key={item.id} className="rounded-card border border-border bg-surface p-6">
          <div className="flex flex-wrap justify-between gap-4">
            <div>
              <p className="text-xs text-muted">{item.donationCode ?? 'Khoản đóng góp'} · {dateLabel(item.createdAt)}</p>
              <h3 className="mt-2 text-lg font-bold text-brand-ink">{campaignMap.get(item.campaignId ?? '')?.title ?? 'Chiến dịch ' + (item.campaignId?.slice(0,8) ?? 'chưa rõ')}</h3>
              <p className="mt-2 text-sm font-bold text-brand">{amountLabel(item.amount,item.currency)}</p>
              <DonationReceiptNote code={item.donationCode} />
            </div>
            <span className="h-fit rounded-full bg-soft-sand px-3 py-2 text-xs font-semibold text-warning">{donationLabel(item.donationStatus)}</span>
          </div>
          <div className="mt-4 flex flex-wrap gap-3">
            {item.donationStatus==='PENDING' && item.id ? <Button variant="outline" loading={cancelPending.isPending} onClick={()=>{if(window.confirm('Hủy khoản đóng góp đang chờ xác nhận?'))cancelPending.mutate(item.id!);}}>Hủy khoản PENDING</Button> : null}
            {canRefund(item.donationStatus) && item.id && refundableAmount(item.amount,item.refundedAmount)>0 ? <Button variant="outline" onClick={()=>{setSelectedDonation(item);setRefundAmount(String(refundableAmount(item.amount,item.refundedAmount)));setRefundReason('');setFormError('');setTab('donations');}}>Yêu cầu hoàn tiền</Button> : null}
          </div>
        </article>) : <QueryState query={donations} empty="Chưa có khoản đóng góp nào" retry={()=>void donations.refetch()}/>}
      </div>
      {donations.isSuccess ? <RecordPagination page={donationPage} pages={donations.data.totalPages ?? 0} onChange={changeDonationPage}/>:null}
      {selectedDonation ? <form onSubmit={(event)=>void submitRefund(event)} className="mt-8 max-w-xl rounded-card border border-border bg-soft-mint p-6">
        <h3 className="text-xl font-bold text-brand-ink">Yêu cầu hoàn tiền mô phỏng</h3>
        <p className="mt-2 text-sm text-muted">Số tiền có thể yêu cầu: {amountLabel(refundableAmount(selectedDonation.amount,selectedDonation.refundedAmount),selectedDonation.currency)}. Backend quyết định điều kiện hợp lệ.</p>
        <div className="mt-5 grid gap-4">
          <Input id="refund-amount" type="number" min="1" step="1" label="Số tiền đề nghị (VND)" required value={refundAmount} onChange={e=>setRefundAmount(e.target.value)}/>
          <div><label className="mb-2 block text-sm font-semibold" htmlFor="refund-reason">Lý do</label><textarea id="refund-reason" rows={3} maxLength={2000} className="w-full rounded-xl border border-border bg-surface p-3 text-sm" required value={refundReason} onChange={e=>setRefundReason(e.target.value)}/></div>
          {formError ? <p role="alert" className="text-sm text-danger">{formError}</p> : null}
          {refund.isError ? <p role="alert" className="text-sm text-danger">{refund.error.message}</p>:null}
          <div className="flex flex-wrap gap-2"><Button type="submit" loading={refund.isPending}>Gửi yêu cầu hoàn tiền</Button><Button type="button" variant="outline" onClick={()=>setSelectedDonation(null)}>Đóng</Button></div>
        </div>
      </form> : null}
    </section>

    <section hidden={tab!=='refunds'} id="dashboard-panel-refunds" className="dashboard-section mt-7" role="tabpanel" aria-labelledby="dashboard-tab-refunds">
      <h2 className="text-xl font-bold text-brand-ink">Yêu cầu hoàn tiền</h2>
      <div className="mt-5 space-y-4">
        {refunds.isSuccess && refunds.data.data?.length ? refunds.data.data.map(item=><article key={item.id} className="rounded-card border border-border bg-surface p-6">
          <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs text-muted">{item.refundCode ?? 'Yêu cầu hoàn tiền'} · {dateLabel(item.createdAt)}</p><h3 className="mt-2 text-lg font-bold text-brand-ink">{amountLabel(item.amount,item.currency)}</h3></div><span className="rounded-full bg-soft-sand px-3 py-2 text-xs font-semibold text-warning">{refundLabel(item.refundStatus)}</span></div>
          {item.reason ? <p className="mt-3 whitespace-pre-line text-sm text-muted">Lý do: {item.reason}</p>:null}
          {item.decisionReason ? <p className="mt-2 text-sm text-muted">Phản hồi của Admin: {item.decisionReason}</p>:null}
          {item.refundStatus==='REQUESTED' && item.id ? <div className="mt-4"><Button variant="outline" loading={cancelRequested.isPending} onClick={()=>{if(window.confirm('Hủy yêu cầu hoàn tiền này?'))cancelRequested.mutate(item.id!);}}>Hủy yêu cầu</Button></div>:null}
        </article>) : <QueryState query={refunds} empty="Chưa có yêu cầu hoàn tiền" retry={()=>void refunds.refetch()}/>}
      </div>
      {refunds.isSuccess ? <RecordPagination page={refundPage} pages={refunds.data.totalPages??0} onChange={setRefundPage}/>:null}
    </section>

    <section hidden={tab!=='disputes'} id="dashboard-panel-disputes" className="dashboard-section mt-7" role="tabpanel" aria-labelledby="dashboard-tab-disputes">
      <h2 className="text-xl font-bold text-brand-ink">Phản ánh và tranh chấp</h2>
      <p className="mt-2 text-sm leading-7 text-muted">Theo quy tắc Backend, chỉ người có khoản đóng góp đã xác nhận mới có thể mở phản ánh. Chọn trang lịch sử bên dưới để tìm chiến dịch hợp lệ, không chỉ giới hạn ở trang đầu tiên.</p>
      <div className="mt-5 space-y-4">
        {disputes.isSuccess && disputes.data.data?.length ? disputes.data.data.map(item=><article key={item.id} className="rounded-card border border-border bg-surface p-6">
          <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs text-muted">{item.disputeCode??'Phản ánh'} · {dateLabel(item.createdAt)}</p><h3 className="mt-2 text-lg font-bold text-brand-ink">{item.title??'Không có tiêu đề'}</h3><p className="mt-2 text-sm text-muted">{disputeTypeLabel(item.disputeType)}</p></div><span className="rounded-full bg-soft-sand px-3 py-2 text-xs font-semibold text-warning">{disputeLabel(item.disputeStatus)}</span></div>
          {item.id ? <Link to={'/donor/disputes/'+encodeURIComponent(item.id)} className="mt-4 inline-flex min-h-11 items-center font-bold text-brand underline">Xem trao đổi và phản hồi</Link>:null}
        </article>):<QueryState query={disputes} empty="Chưa có phản ánh nào" retry={()=>void disputes.refetch()}/>}
      </div>
      {disputes.isSuccess ? <RecordPagination page={disputePage} pages={disputes.data.totalPages??0} onChange={setDisputePage}/>:null}
      <form onSubmit={(event)=>void submitDispute(event)} className="mt-9 max-w-2xl rounded-card border border-border bg-soft-mint p-6">
        <div className="flex items-center gap-2"><AlertCircle size={22} className="text-brand" aria-hidden="true"/><h3 className="text-xl font-bold text-brand-ink">Gửi phản ánh mới</h3></div>
        <div className="mt-5 grid gap-4">
          <div><label htmlFor="dispute-campaign" className="mb-2 block text-sm font-semibold">Chiến dịch có đóng góp hợp lệ</label><select id="dispute-campaign" required value={selectedCampaign} onChange={e=>setSelectedCampaign(e.target.value)} className="min-h-11 w-full rounded-xl border border-border bg-surface px-3 text-sm"><option value="">Chọn chiến dịch</option>{eligibleCampaigns.map(id=><option key={id} value={id}>{campaignMap.get(id)?.title ?? 'Chiến dịch '+id.slice(0,8)}</option>)}</select></div>
          {donations.isError ? <div role="alert" className="text-xs text-danger">Không tải được lịch sử đóng góp để xác nhận quyền gửi phản ánh. <button type="button" className="font-bold underline" onClick={() => void donations.refetch()}>Thử lại</button></div> : null}
          {eligibleCampaigns.length===0 ? <p className="text-xs text-muted">Chưa thấy khoản đóng góp đã xác nhận trong trang lịch sử này. Bạn có thể chọn trang khác ngay tại đây.</p> : null}
          {donations.isSuccess && (donations.data.totalPages ?? 0)>1 ? <div className="dashboard-page-picker">
            <p className="text-xs font-semibold text-muted">Chọn trang đóng góp để tìm chiến dịch hợp lệ</p>
            <RecordPagination page={donationPage} pages={donations.data.totalPages ?? 0} onChange={changeDonationPage}/>
          </div> : null}
          <div><label htmlFor="dispute-type" className="mb-2 block text-sm font-semibold">Loại phản ánh</label><select id="dispute-type" value={disputeType} onChange={e=>setDisputeType(e.target.value as DisputeType)} className="min-h-11 w-full rounded-xl border border-border bg-surface px-3 text-sm">{disputeTypes.map(x=><option key={x.value} value={x.value}>{x.label}</option>)}</select></div>
          <Input id="dispute-title" label="Tiêu đề" maxLength={200} required value={disputeTitle} onChange={e=>setDisputeTitle(e.target.value)}/>
          <div><label htmlFor="dispute-description" className="mb-2 block text-sm font-semibold">Mô tả phản ánh</label><textarea id="dispute-description" className="w-full rounded-xl border border-border bg-surface p-3 text-sm" rows={4} maxLength={5000} required value={disputeDescription} onChange={e=>setDisputeDescription(e.target.value)}/></div>
          {formError ? <p role="alert" className="text-sm text-danger">{formError}</p>:null}
          {dispute.isError ? <p role="alert" className="text-sm text-danger">{dispute.error.message}</p>:null}
          <Button type="submit" loading={dispute.isPending} disabled={!selectedCampaign}>Gửi phản ánh</Button>
        </div>
      </form>
    </section>
    <button type="button" onClick={()=>{invalidate();setNotice('');}} className="mt-10 inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-brand hover:underline"><RotateCcw size={16} aria-hidden="true"/> Làm mới dữ liệu của tôi</button>
  </Container></div>;
}
