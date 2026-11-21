import { useEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertCircle, ArrowUpRight, HandHeart, MessageCircleWarning, ReceiptText, RotateCcw } from 'lucide-react';
import { Link } from 'react-router';
import { cancelDonation, cancelRefund, getCampaignById, myDisputes, myDonations, myRefunds, openDispute, requestRefund } from '@/api/donor';
import type { CreateRefundRequest, DisputeType, DonationResponse } from '@/api/contracts';
import { useAuth } from '@/auth/auth-context';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { DonorReceiptCard } from '@/pages/DonorReceiptCard';
import { Input } from '@/components/ui/Input';
import { RecordPagination } from '@/components/ui/RecordPagination';
import donorHero from '@/components/ui/donor-hero-v2.svg?raw';
import { amountLabel, dateLabel } from '@/lib/campaign-display';
import { canRefund, donationLabel, disputeLabel, disputeTypeLabel, disputeTypes, isCountedDonation, refundLabel, refundableAmount } from '@/lib/donor-display';

type Tab = 'donations' | 'refunds' | 'disputes';
function QueryState({ query, empty, retry }: { query: { isPending: boolean; isError: boolean; error: Error | null }; empty: string; retry: ()=>void }) {
  return <div className="u3-empty" data-state={query.isError?'error':query.isPending?'loading':'empty'} role={query.isError?'alert':'status'}>
    <span className="u3-emptyart" aria-hidden="true">{query.isError?'!':'▤'}</span>
    <h2>{query.isPending?'Đang tải dữ liệu cá nhân…':query.isError?'Không tải được dữ liệu':empty}</h2>
    <p>{query.isError?query.error?.message:query.isPending?'Đang lấy dữ liệu từ Backend.':'Dữ liệu được tải từ Backend, không thêm bản ghi giả.'}</p>
    {query.isError?<Button variant="outline" onClick={retry}>Thử lại</Button>:null}
  </div>;
}
const receiptKey=(item:DonationResponse,index:number)=>item.id??item.donationCode??'receipt-row-'+index;
const heroMarkup={__html:donorHero};

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
  const [activeReceiptKey,setActiveReceiptKey]=useState<string|null>(null);
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
  const activeReceipt=donationItems.find((item,index)=>receiptKey(item,index)===activeReceiptKey)??donationItems[0]??null;
  useEffect(()=>{
    if(activeReceiptKey&&!donationItems.some((item,index)=>receiptKey(item,index)===activeReceiptKey))setActiveReceiptKey(null);
  },[activeReceiptKey,donationItems]);
  function changeDonationPage(next: number) {
    setActiveReceiptKey(null);
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
  return <div className="rise-dashboard donor-dashboard u3-approved-v2"><Container className="dashboard-inner u3-body">
    <header className="u3-hero"><div className="u3-herotext"><div className="u3-eyebrow">✦ HÀNH TRÌNH THIỆN NGUYỆN</div>
      <h1>Mỗi điều tốt,<span>một dấu ấn riêng.</span></h1>
      <p>Nơi bạn theo dõi những đóng góp, lưu giữ biên nhận và đồng hành cùng các sáng kiến tạo tác động tích cực.</p>
      <Link to="/campaigns" className="u3-btn">Khám phá chiến dịch <ArrowUpRight size={16} aria-hidden="true"/></Link>
    </div><div className="u3-heroart" dangerouslySetInnerHTML={heroMarkup}/></header>
    <section className="u3-stats" aria-label="Tóm tắt hành trình">
      <div className="u3-stat"><div className="u3-statbar">Khoản đóng góp đã ghi nhận<span aria-hidden="true">🌱</span></div><div className="u3-statbody"><strong>{donations.isSuccess&&typeof donations.data.totalElements==='number'?new Intl.NumberFormat('vi-VN').format(donations.data.totalElements):'—'}</strong><small>Toàn bộ lịch sử của tài khoản</small></div></div>
      <div className="u3-stat"><div className="u3-statbar">Đã xác nhận / ghi nhận hoàn<span aria-hidden="true">♥</span></div><div className="u3-statbody"><strong>{donations.isSuccess?confirmedPageCount:'—'}</strong><small>Trong trang lịch sử đang xem</small></div></div>
      <div className="u3-stat"><div className="u3-statbar">Đang chờ xác nhận<span aria-hidden="true">✉</span></div><div className="u3-statbody"><strong>{donations.isSuccess?pendingPageCount:'—'}</strong><small>Trong trang lịch sử đang xem</small></div></div>
    </section>
    <div className="u3-sectionhead"><div><small>✦ GÓC NHỎ CỦA BẠN</small><h2>Hành trình đóng góp</h2></div><span className="u3-pill">DỮ LIỆU MÔ PHỎNG</span></div>
    <div className="u3-disclaimer">Toàn bộ đóng góp, hoàn tiền và trạng thái tài chính ở đây chỉ là mô phỏng. Việc tạo đóng góp không đồng nghĩa đã được xác nhận.</div>
    <div className="dashboard-tabbar u3-tabs" role="tablist" aria-label="Quản lý đóng góp" onKeyDown={navigateTabs}>
      {donorTabs.map(({id,icon:Icon,label})=><button key={id} id={'dashboard-tab-'+id} aria-controls={'dashboard-panel-'+id} type="button" role="tab" aria-selected={tab===id} tabIndex={tab===id?0:-1} onClick={()=>selectTab(id)}><Icon size={17} aria-hidden="true"/>{label}</button>)}
    </div>
    {notice?<p className="u3-note" role="status">{notice}</p>:null}
    {cancelPending.isError||cancelRequested.isError?<p role="alert" className="u3-error">{(cancelPending.error??cancelRequested.error)?.message}</p>:null}
    <section hidden={tab!=='donations'} id="dashboard-panel-donations" className="dashboard-section u3-tabpanel" role="tabpanel" aria-labelledby="dashboard-tab-donations">
      <div className="u3-sectionhead u3-list-heading"><h2>Lịch sử đóng góp của tôi</h2><span>Chọn một khoản để xem biên nhận</span></div>
      <div className={'u3-columns'+(!donations.isSuccess||!activeReceipt?' u3-columns-empty':'')}>
        <div className="u3-list">
          {donations.isSuccess&&donationItems.length?donationItems.map((item,index)=><button type="button" className={'u3-recorditem'+(activeReceipt===item?' u3-on':'')} key={receiptKey(item,index)} aria-pressed={activeReceipt===item} aria-controls="donor-selected-receipt" onClick={()=>setActiveReceiptKey(receiptKey(item,index))}>
            <span className="u3-recordnum" aria-hidden="true">{String(donationPage*10+index+1).padStart(2,'0')}</span>
            <span><span className="u3-recordname">{campaignMap.get(item.campaignId??'')?.title??'Chiến dịch '+(item.campaignId?.slice(0,8)??'chưa rõ')}</span><span className="u3-recordcode">{item.donationCode??'Chưa có mã từ Backend'} · {dateLabel(item.createdAt)}</span></span>
            <span className="u3-recordright">{amountLabel(item.amount,item.currency)}<br/><span className="u3-status" data-status={item.donationStatus??'UNKNOWN'}>{donationLabel(item.donationStatus)??'Chưa xác định'}</span></span>
          </button>):<QueryState query={donations} empty="Chưa có khoản đóng góp nào" retry={()=>void donations.refetch()}/>}
          {donations.isSuccess?<RecordPagination page={donationPage} pages={donations.data.totalPages??0} onChange={changeDonationPage}/>:null}
        </div>
        {donations.isSuccess&&activeReceipt?<aside id="donor-selected-receipt" aria-label="Biên nhận đang chọn"><DonorReceiptCard donation={activeReceipt} campaignTitle={campaignMap.get(activeReceipt.campaignId??'')?.title??'Chiến dịch '+(activeReceipt.campaignId?.slice(0,8)??'chưa rõ')}>
          <div className="u3-receipt-actions flex flex-wrap gap-3">
            {activeReceipt.donationStatus==='PENDING' && activeReceipt.id ? <Button variant="outline" loading={cancelPending.isPending} onClick={()=>{if(window.confirm('Hủy khoản đóng góp đang chờ xác nhận?'))cancelPending.mutate(activeReceipt.id!);}}>Hủy khoản PENDING</Button> : null}
            {canRefund(activeReceipt.donationStatus) && activeReceipt.id && refundableAmount(activeReceipt.amount,activeReceipt.refundedAmount)>0 ? <Button variant="outline" onClick={()=>{setSelectedDonation(activeReceipt);setRefundAmount(String(refundableAmount(activeReceipt.amount,activeReceipt.refundedAmount)));setRefundReason('');setFormError('');setTab('donations');}}>Yêu cầu hoàn tiền</Button> : null}
          </div>
        </DonorReceiptCard></aside>:null}
      </div>
      {selectedDonation ? <form onSubmit={(event)=>void submitRefund(event)} className="u3-refund-form u3-sheet" data-refund-donation-id={selectedDonation.id}>
        <h3 className="text-xl font-bold text-brand-ink">Yêu cầu hoàn tiền mô phỏng</h3>
        <p className="u3-refund-scope">Khoản đóng góp: {selectedDonation.donationCode??selectedDonation.id??'Chưa có mã từ Backend'}</p>
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
    <section hidden={tab!=='refunds'} id="dashboard-panel-refunds" className="dashboard-section u3-tabpanel" role="tabpanel" aria-labelledby="dashboard-tab-refunds">
      <h2 className="text-xl font-bold text-brand-ink">Yêu cầu hoàn tiền</h2>
      <div className="u3-columns u3-refund-columns"><div className="u3-refunds-list"><div className="mt-5 space-y-4">
        {refunds.isSuccess && refunds.data.data?.length ? refunds.data.data.map(item=><article key={item.id} className="u3-sheet u3-refund-record">
          <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs text-muted">{item.refundCode ?? 'Yêu cầu hoàn tiền'} · {dateLabel(item.createdAt)}</p><h3 className="mt-2 text-lg font-bold text-brand-ink">{amountLabel(item.amount,item.currency)}</h3></div><span className="u3-status" data-status={item.refundStatus??'UNKNOWN'}>{refundLabel(item.refundStatus)??'Chưa xác định'}</span></div>
          {item.reason ? <p className="mt-3 whitespace-pre-line text-sm text-muted">Lý do: {item.reason}</p>:null}
          {item.decisionReason ? <p className="mt-2 text-sm text-muted">Phản hồi của Admin: {item.decisionReason}</p>:null}
          {item.refundStatus==='REQUESTED' && item.id ? <div className="mt-4"><Button variant="outline" loading={cancelRequested.isPending} onClick={()=>{if(window.confirm('Hủy yêu cầu hoàn tiền này?'))cancelRequested.mutate(item.id!);}}>Hủy yêu cầu</Button></div>:null}
        </article>) : <QueryState query={refunds} empty="Chưa có yêu cầu hoàn tiền" retry={()=>void refunds.refetch()}/>}
      </div>
      {refunds.isSuccess ? <RecordPagination page={refundPage} pages={refunds.data.totalPages??0} onChange={setRefundPage}/>:null}
      </div><aside className="u3-sheet u3-refund-info"><div className="u3-tiny">02 / RECORD</div><h2>Rõ ràng từng yêu cầu.</h2><p>Số tiền, lý do và trạng thái đều được ghi nhận. Không chuyển tiền thật.</p><div className="u3-note">Gửi yêu cầu → Xem xét → Phản hồi</div></aside></div>
    </section>


    <section hidden={tab!=='disputes'} id="dashboard-panel-disputes" className="dashboard-section u3-tabpanel" role="tabpanel" aria-labelledby="dashboard-tab-disputes">
      <h2 className="text-xl font-bold text-brand-ink">Phản ánh và tranh chấp</h2>
      <p className="mt-2 text-sm leading-7 text-muted">Theo quy tắc Backend, chỉ người có khoản đóng góp đã xác nhận mới có thể mở phản ánh. Chọn trang lịch sử bên dưới để tìm chiến dịch hợp lệ, không chỉ giới hạn ở trang đầu tiên.</p>
      <div className="u3-columns u3-dispute-columns">
      <form onSubmit={(event)=>void submitDispute(event)} className="u3-sheet u3-dispute-form">
        <div className="flex items-center gap-2"><AlertCircle size={22} className="text-brand" aria-hidden="true"/><h3 className="text-xl font-bold text-brand-ink">Gửi phản ánh mới</h3></div>
        <div className="u3-form-grid">
          <div className="u3-campaign-field"><label htmlFor="dispute-campaign" className="mb-2 block text-sm font-semibold">Chiến dịch có đóng góp hợp lệ</label><select id="dispute-campaign" required value={selectedCampaign} onChange={e=>setSelectedCampaign(e.target.value)} className="min-h-11 w-full rounded-xl border border-border bg-surface px-3 text-sm"><option value="">Chọn chiến dịch</option>{eligibleCampaigns.map(id=><option key={id} value={id}>{campaignMap.get(id)?.title ?? 'Chiến dịch '+id.slice(0,8)}</option>)}</select></div>
          {donations.isError ? <div role="alert" className="text-xs text-danger">Không tải được lịch sử đóng góp để xác nhận quyền gửi phản ánh. <button type="button" className="font-bold underline" onClick={() => void donations.refetch()}>Thử lại</button></div> : null}
          {eligibleCampaigns.length===0 ? <p className="text-xs text-muted">Chưa thấy khoản đóng góp đã xác nhận trong trang lịch sử này. Bạn có thể chọn trang khác ngay tại đây.</p> : null}
          {donations.isSuccess && (donations.data.totalPages ?? 0)>1 ? <div className="dashboard-page-picker">
            <p className="text-xs font-semibold text-muted">Chọn trang đóng góp để tìm chiến dịch hợp lệ</p>
            <RecordPagination page={donationPage} pages={donations.data.totalPages ?? 0} onChange={changeDonationPage}/>
          </div> : null}
          <div className="u3-type-field"><label htmlFor="dispute-type" className="mb-2 block text-sm font-semibold">Loại phản ánh</label><select id="dispute-type" value={disputeType} onChange={e=>setDisputeType(e.target.value as DisputeType)} className="min-h-11 w-full rounded-xl border border-border bg-surface px-3 text-sm">{disputeTypes.map(x=><option key={x.value} value={x.value}>{x.label}</option>)}</select></div>
          <Input id="dispute-title" label="Tiêu đề" maxLength={200} required value={disputeTitle} onChange={e=>setDisputeTitle(e.target.value)}/>
          <div><label htmlFor="dispute-description" className="mb-2 block text-sm font-semibold">Mô tả phản ánh</label><textarea id="dispute-description" className="w-full rounded-xl border border-border bg-surface p-3 text-sm" rows={4} maxLength={5000} required value={disputeDescription} onChange={e=>setDisputeDescription(e.target.value)}/></div>
          {formError ? <p role="alert" className="text-sm text-danger">{formError}</p>:null}
          {dispute.isError ? <p role="alert" className="text-sm text-danger">{dispute.error.message}</p>:null}
          <Button type="submit" loading={dispute.isPending} disabled={!selectedCampaign}>Gửi phản ánh</Button>
        </div>
      </form>
<div className="u3-skycard u3-disputes-list"><div className="u3-tiny">03 / REVIEW · THEO DÕI</div><h3>Lắng nghe &amp; đối chiếu.</h3>
      <div className="mt-5 space-y-4">
        {disputes.isSuccess && disputes.data.data?.length ? disputes.data.data.map(item=><article key={item.id} className="u3-sheet u3-dispute-record">
          <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs text-muted">{item.disputeCode??'Phản ánh'} · {dateLabel(item.createdAt)}</p><h3 className="mt-2 text-lg font-bold text-brand-ink">{item.title??'Không có tiêu đề'}</h3><p className="mt-2 text-sm text-muted">{disputeTypeLabel(item.disputeType)}</p></div><span className="u3-status" data-status={item.disputeStatus??'UNKNOWN'}>{disputeLabel(item.disputeStatus)??'Chưa xác định'}</span></div>
          {item.id ? <Link to={'/donor/disputes/'+encodeURIComponent(item.id)} className="mt-4 inline-flex min-h-11 items-center font-bold text-brand underline">Xem trao đổi và phản hồi</Link>:null}
        </article>):<QueryState query={disputes} empty="Chưa có phản ánh nào" retry={()=>void disputes.refetch()}/>}
      </div>
      {disputes.isSuccess ? <RecordPagination page={disputePage} pages={disputes.data.totalPages??0} onChange={setDisputePage}/>:null}

</div></div>
    </section>

    <button type="button" onClick={()=>{invalidate();setNotice('');}} className="u3-smallbtn u3-refresh"><RotateCcw size={16} aria-hidden="true"/> Làm mới dữ liệu của tôi</button>
  </Container></div>;
}
