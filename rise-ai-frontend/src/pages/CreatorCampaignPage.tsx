import { useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CircleAlert, ClipboardCheck, FileText, Landmark, ShieldCheck } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import {
  cancelCreatorDisbursement, createCreatorDisbursement, createCreatorEvidence, createCreatorMilestone,
  creatorDisbursements, creatorEvidence, getCreatorCampaign, publishCreatorCampaign,
  resubmitCreatorDisbursement, startCreatorMilestone, submitCreatorCampaign, updateCreatorMilestone,
} from '@/api/creator';
import { listCampaignMilestones } from '@/api/campaigns';
import type { EvidenceRequest, EvidenceType, MilestoneRequest, MilestoneResponse } from '@/api/contracts';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { amountLabel, dateLabel, milestoneLabel } from '@/lib/campaign-display';
import { campaignState, disbursementState, editableCampaign, evidenceState, evidenceTypes, localInput, toInstant } from '@/lib/creator-display';
import { creatorEvidenceSummary } from '@/lib/creator-evidence';

type MilestoneDraft = Omit<MilestoneRequest,'startAt'|'dueAt'> & {startAt:string;dueAt:string};
function milestoneDraft(item?:MilestoneResponse):MilestoneDraft {return {
  title:item?.title??'',description:item?.description??'',startAt:localInput(item?.startAt),dueAt:localInput(item?.dueAt),
  plannedAmount:item?.plannedAmount??0,expectedDeliverables:item?.expectedDeliverables??'',releaseConditions:item?.releaseConditions??'',
};}
const evidenceBlank:EvidenceRequest={title:'',evidenceType:'PROGRESS_REPORT',description:'',externalReference:'',fileUrl:'',fileHash:''};
export function CreatorCampaignPage() {
  const {id}=useParams();
  const {user}=useAuth();
  const client=useQueryClient();
  const key=['creator',user?.id] as const;
  const [milestoneForm,setMilestoneForm]=useState<MilestoneDraft|null>(null);
  const [editingMilestone,setEditingMilestone]=useState<string|null>(null);
  const [selected,setSelected]=useState<string|null>(null);
  const [evidencePage,setEvidencePage]=useState(0);
  const [disbursementPage,setDisbursementPage]=useState(0);
  const [evidenceForm,setEvidenceForm]=useState<EvidenceRequest>(evidenceBlank);
  const [amount,setAmount]=useState('');
  const [reason,setReason]=useState('');
  const [notice,setNotice]=useState('');
  const [error,setError]=useState('');
  const requestAttempt=useRef<{signature:string;key:string}|null>(null);
  const campaign=useQuery({queryKey:[...key,'campaign',id],queryFn:()=>getCreatorCampaign(id!),enabled:Boolean(id)});
  const owned=Boolean(campaign.data?.creatorId&&campaign.data.creatorId===user?.id);
  const milestones=useQuery({queryKey:[...key,'milestones',id],queryFn:()=>listCampaignMilestones(id!),enabled:Boolean(id&&owned)});
  const disbursements=useQuery({queryKey:[...key,'disbursements',id,disbursementPage],queryFn:()=>creatorDisbursements(id!,disbursementPage),enabled:Boolean(id&&owned)});
  const evidence=useQuery({queryKey:[...key,'evidence',selected,evidencePage],queryFn:()=>creatorEvidence(selected!,evidencePage),enabled:Boolean(selected&&owned)});
  const evidenceSummary=creatorEvidenceSummary(evidence);
  const activeMilestone=milestones.data?.find(item=>item.id===selected);
  const editable=editableCampaign(campaign.data?.campaignStatus);
  const plannedTotal=milestones.data?.reduce((sum,item)=>sum+(item.plannedAmount??0),0)??0;
  const invalidate=()=>Promise.all([client.invalidateQueries({queryKey:key}),client.invalidateQueries({queryKey:['campaign',campaign.data?.slug]})]);
  const action=useMutation({
    mutationFn:(operation:'submit'|'publish')=>operation==='submit'?submitCreatorCampaign(id!):publishCreatorCampaign(id!),
    onSuccess(_,operation){setNotice(operation==='submit'?'Chiến dịch đang chờ Admin duyệt.':'Chiến dịch đã được công bố.');setError('');invalidate();},
  });
  const saveMilestone=useMutation({
    mutationFn:(data:MilestoneRequest)=>editingMilestone?updateCreatorMilestone(editingMilestone,data):createCreatorMilestone(id!,data),
    onSuccess(){setMilestoneForm(null);setEditingMilestone(null);setNotice('Đã lưu cột mốc.');invalidate();},
  });
  const start=useMutation({mutationFn:startCreatorMilestone,onSuccess(){setNotice('Đã bắt đầu cột mốc.');invalidate();}});
  const saveEvidence=useMutation({
    mutationFn:({milestoneId,data}:{milestoneId:string;data:EvidenceRequest})=>createCreatorEvidence(milestoneId,data),
    async onSuccess(){setEvidencePage(0);setEvidenceForm(evidenceBlank);setNotice('Đã gửi thông tin minh chứng chờ Admin xem xét.');await invalidate();},
  });
  const requestFunds=useMutation({
    mutationFn:(params:{amount:number;reason:string;key:string})=>createCreatorDisbursement(selected!,{amount:params.amount,reason:params.reason},params.key),
    async onSuccess(){setDisbursementPage(0);setAmount('');setReason('');requestAttempt.current=null;setNotice('Đã gửi đề nghị giải ngân mô phỏng. Không có tiền thật được chuyển.');await invalidate();},
  });
  const fixFunds=useMutation({mutationFn:resubmitCreatorDisbursement,onSuccess(){setNotice('Đã gửi lại đề nghị để Admin xem xét.');invalidate();}});
  const cancelFunds=useMutation({
    mutationFn:({id:requestId,reason:why}:{id:string;reason:string})=>cancelCreatorDisbursement(requestId,why),
    onSuccess(){setNotice('Đã hủy đề nghị giải ngân.');invalidate();},
  });
  const globalError=action.error??saveMilestone.error??start.error??saveEvidence.error??requestFunds.error??fixFunds.error??cancelFunds.error;
  function openMilestone(item?:MilestoneResponse){setEditingMilestone(item?.id??null);setMilestoneForm(milestoneDraft(item));setError('');}
  async function submitMilestone(event:FormEvent<HTMLFormElement>){
    event.preventDefault();if(!milestoneForm||saveMilestone.isPending)return;
    try {
      const startAt=toInstant(milestoneForm.startAt),dueAt=toInstant(milestoneForm.dueAt);
      if(new Date(dueAt)<=new Date(startAt)){setError('Ngày kết thúc cột mốc phải sau ngày bắt đầu.');return;}
      if(!Number.isSafeInteger(milestoneForm.plannedAmount)||milestoneForm.plannedAmount<=0){setError('Kinh phí cột mốc phải là số nguyên dương.');return;}
      const others=plannedTotal-(milestones.data?.find(item=>item.id===editingMilestone)?.plannedAmount??0);
      if(others+milestoneForm.plannedAmount>(campaign.data?.targetAmount??0)){setError('Tổng kinh phí cột mốc vượt quá mục tiêu chiến dịch.');return;}
      setError('');await saveMilestone.mutateAsync({...milestoneForm,startAt,dueAt});
    }catch(reason){if(reason instanceof Error&&reason.message==='Ngày giờ không hợp lệ.')setError(reason.message);}
  }
  async function submitEvidence(event:FormEvent<HTMLFormElement>){
    event.preventDefault();if(!selected||saveEvidence.isPending)return;
    if(!evidenceForm.description?.trim()&&!evidenceForm.externalReference?.trim()&&!evidenceForm.fileUrl?.trim()){setError('Cung cấp mô tả, URL hoặc tham chiếu minh chứng.');return;}
    if(evidenceForm.fileHash&&!/^[a-fA-F0-9]{64}$/.test(evidenceForm.fileHash)){setError('SHA-256 phải gồm 64 ký tự hexadecimal.');return;}
    if(evidenceForm.fileUrl&&!/^(https|sim):\/\/\S+$/.test(evidenceForm.fileUrl)){setError('URL minh chứng chỉ hỗ trợ https:// hoặc sim://.');return;}
    setError('');
    try{await saveEvidence.mutateAsync({milestoneId:selected,data:{...evidenceForm,title:evidenceForm.title.trim(),fileUrl:evidenceForm.fileUrl?.trim()||'',externalReference:evidenceForm.externalReference?.trim()||''}});}catch{/* Backend error below */}
  }
  async function submitFunds(event:FormEvent<HTMLFormElement>){
    event.preventDefault();if(!selected||requestFunds.isPending)return;
    if(!evidenceSummary.hasEvidence){setError('Cần tải minh chứng thành công trước khi gửi đề nghị.');return;}
    const n=Number(amount);
    if(!Number.isSafeInteger(n)||n<=0||n>(activeMilestone?.remainingAmount??0)||!reason.trim()){setError('Nhập số tiền hợp lệ, không vượt ngân sách còn lại, và lý do.');return;}
    setError('');
    const signature=selected+JSON.stringify({amount:n,reason:reason.trim()});
    if(!requestAttempt.current||requestAttempt.current.signature!==signature)requestAttempt.current={signature,key:crypto.randomUUID()};
    try{await requestFunds.mutateAsync({amount:n,reason:reason.trim(),key:requestAttempt.current.key});}catch{/* Retry with same idempotency key. */}
  }
  if(!id)return <Container className="py-14"><StatusMessage error title="Thiếu mã chiến dịch"/></Container>;
  return <div className="rise-deep-page deep-creator-campaign">
    <div className="rise-deep-topline" aria-hidden="true">
      <span>CREATOR / TRIỂN KHAI</span><span>03 · LỘ TRÌNH MINH CHỨNG</span>
    </div>
    <Container className="deep-inner py-10 sm:py-14">
    <Link to="/creator" className="inline-flex min-h-11 items-center gap-2 text-sm font-bold text-brand"><ArrowLeft size={17} aria-hidden="true"/> Về không gian Creator</Link>
    {campaign.isPending?<div className="mt-7"><StatusMessage title="Đang tải chiến dịch…"/></div>:null}
    {campaign.isError?<div className="mt-7"><StatusMessage error title="Không tải được chiến dịch" detail={campaign.error.message}/></div>:null}
    {campaign.isSuccess&&!owned?<div className="mt-7"><StatusMessage error title="Bạn không quản lý chiến dịch này"/></div>:null}
    {campaign.data&&owned?<>
      <div className="mt-5 flex flex-wrap items-end justify-between gap-4">
        <div><p className="text-xs font-extrabold uppercase tracking-widest text-brand">CREATOR · {campaignState(campaign.data.campaignStatus)}</p>
          <h1 className="mt-3 text-3xl font-extrabold text-brand-ink sm:text-4xl">{campaign.data.title}</h1>
          <p className="mt-3 text-sm text-muted">{campaign.data.organization?.name??'Tổ chức'} · Mục tiêu: {amountLabel(campaign.data.targetAmount,campaign.data.currency)}</p>
        </div>
        {editable?<Link to={'/creator/campaigns/'+encodeURIComponent(id)+'/edit'} className="inline-flex min-h-11 items-center rounded-xl border border-border px-5 text-sm font-bold text-brand">Chỉnh sửa chiến dịch</Link>:null}
      </div>
      <div className="mt-7 rounded-card border border-border bg-soft-mint p-6">
        <h2 className="flex items-center gap-2 text-lg font-bold text-brand-ink"><ClipboardCheck size={20} className="text-brand" aria-hidden="true"/> Quy trình xét duyệt</h2>
        <p className="mt-3 text-sm leading-7 text-muted">Cần ít nhất một cột mốc, tổng dự kiến bằng mục tiêu và ngày kết thúc trong tương lai để gửi duyệt. Admin xác minh tổ chức và phê duyệt chiến dịch; Creator chỉ được công bố sau khi đã được duyệt.</p>
        <p className="mt-3 text-sm font-semibold">Ngân sách cột mốc: {amountLabel(plannedTotal,campaign.data.currency)} / {amountLabel(campaign.data.targetAmount,campaign.data.currency)}</p>
        {campaign.data.reviewNote?<p className="mt-2 text-sm text-muted">Phản hồi xét duyệt: {campaign.data.reviewNote}</p>:null}
        <div className="mt-5 flex flex-wrap gap-3">
          {editable?<Button disabled={!milestones.isSuccess||!milestones.data.length||Math.abs(plannedTotal-(campaign.data.targetAmount??0))>.001} loading={action.isPending} onClick={()=>{if(window.confirm('Gửi chiến dịch và các cột mốc để Admin xét duyệt?'))action.mutate('submit');}}>Gửi xét duyệt</Button>:null}
          {campaign.data.campaignStatus==='APPROVED'?<Button loading={action.isPending} onClick={()=>{if(window.confirm('Công bố chiến dịch đã được duyệt?'))action.mutate('publish');}}>Công bố chiến dịch</Button>:null}
        </div>
      </div>
      {notice?<p className="mt-6 rounded-xl bg-soft-mint p-4 text-sm text-brand-ink" role="status">{notice}</p>:null}
      {error?<p className="mt-5 text-sm text-danger" role="alert">{error}</p>:null}
      {globalError?<p className="mt-5 text-sm text-danger" role="alert">{globalError.message}</p>:null}
      <section className="mt-12">
        <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-2xl font-extrabold text-brand-ink">Cột mốc triển khai</h2>{editable?<Button variant="outline" onClick={()=>openMilestone()}>+ Tạo cột mốc</Button>:null}</div>
        {milestones.isPending?<div className="mt-5"><StatusMessage title="Đang tải cột mốc…"/></div>:null}
        {milestones.isError?<div className="mt-5"><StatusMessage error title="Không tải được cột mốc" detail={milestones.error.message}/><Button variant="outline" onClick={()=>void milestones.refetch()}>Thử lại</Button></div>:null}
        {milestones.isSuccess&&!milestones.data.length?<div className="mt-5"><StatusMessage title="Chưa có cột mốc" detail="Tạo cột mốc trước khi gửi chiến dịch để Admin duyệt."/></div>:null}
        {milestones.isSuccess?<div className="mt-5 grid gap-3">
          {milestones.data.map(m=><article key={m.id} className={'rounded-card border p-5 '+(selected===m.id?'border-brand bg-soft-mint':'border-border bg-surface')}>
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div><p className="text-xs font-semibold text-brand">Cột mốc {m.sequenceNumber} · {milestoneLabel(m.milestoneStatus)}</p><h3 className="mt-2 text-lg font-bold text-brand-ink">{m.title}</h3>
                <p className="mt-2 text-sm text-muted">Ngân sách {amountLabel(m.plannedAmount,campaign.data.currency)} · Đã giải ngân (mô phỏng) {amountLabel(m.releasedAmount,campaign.data.currency)}</p>
                <p className="mt-1 text-xs text-muted">Hạn: {dateLabel(m.dueAt)} · Minh chứng: {m.id===selected?(evidenceSummary.count??({loading:'Đang tải…',error:'Không tải được',unknown:'Chưa xác định',ready:'Có minh chứng (chưa có tổng số)',empty:'0'}[evidenceSummary.status])):(Number.isSafeInteger(m.evidenceCount)&&m.evidenceCount!>=0?m.evidenceCount:'Chưa tải')}</p>
              </div>
              <div className="flex flex-wrap gap-2">
                {m.id?<Button variant="outline" onClick={()=>{setSelected(m.id!);setEvidencePage(0);setNotice('');setError('');}}>Minh chứng & giải ngân</Button>:null}
                {editable?<Button variant="outline" onClick={()=>openMilestone(m)}>Sửa</Button>:null}
                {campaign.data.campaignStatus==='ACTIVE'&&m.milestoneStatus==='READY'&&m.id?<Button variant="outline" loading={start.isPending} onClick={()=>{if(window.confirm('Bắt đầu cột mốc này?'))start.mutate(m.id!);}}>Bắt đầu</Button>:null}
              </div>
            </div>
          </article>)}
        </div>:null}
        {milestoneForm&&editable?<form onSubmit={e=>void submitMilestone(e)} className="mt-7 grid gap-5 rounded-card border border-border bg-surface p-6 sm:grid-cols-2">
          <h3 className="text-lg font-bold sm:col-span-2">{editingMilestone?'Sửa cột mốc':'Tạo cột mốc'}</h3>
          <div className="sm:col-span-2"><Input id="ms-title" label="Tên cột mốc" required maxLength={200} value={milestoneForm.title} onChange={e=>setMilestoneForm({...milestoneForm,title:e.target.value})}/></div>
          <Input id="ms-budget" label="Ngân sách dự kiến (VND)" type="number" min={1} step={1} required value={String(milestoneForm.plannedAmount)} onChange={e=>setMilestoneForm({...milestoneForm,plannedAmount:Number(e.target.value)})}/>
          <div><label htmlFor="ms-description" className="mb-2 block text-sm font-semibold">Mô tả</label><textarea id="ms-description" maxLength={5000} rows={2} className="w-full rounded-xl border border-border bg-surface p-3 text-sm" value={milestoneForm.description} onChange={e=>setMilestoneForm({...milestoneForm,description:e.target.value})}/></div>
          <Input id="ms-start" label="Ngày bắt đầu" type="datetime-local" required value={milestoneForm.startAt} onChange={e=>setMilestoneForm({...milestoneForm,startAt:e.target.value})}/>
          <Input id="ms-due" label="Hạn hoàn thành" type="datetime-local" required value={milestoneForm.dueAt} onChange={e=>setMilestoneForm({...milestoneForm,dueAt:e.target.value})}/>
          <div><label htmlFor="ms-deliverables" className="mb-2 block text-sm font-semibold">Kết quả cần bàn giao</label><textarea id="ms-deliverables" required maxLength={5000} rows={3} className="w-full rounded-xl border border-border bg-surface p-3 text-sm" value={milestoneForm.expectedDeliverables} onChange={e=>setMilestoneForm({...milestoneForm,expectedDeliverables:e.target.value})}/></div>
          <div><label htmlFor="ms-conditions" className="mb-2 block text-sm font-semibold">Điều kiện giải ngân mô phỏng</label><textarea id="ms-conditions" required maxLength={5000} rows={3} className="w-full rounded-xl border border-border bg-surface p-3 text-sm" value={milestoneForm.releaseConditions} onChange={e=>setMilestoneForm({...milestoneForm,releaseConditions:e.target.value})}/></div>
          <div className="flex flex-wrap gap-2 sm:col-span-2"><Button type="submit" loading={saveMilestone.isPending}>Lưu cột mốc</Button><Button type="button" variant="outline" onClick={()=>{setMilestoneForm(null);setError('');}}>Đóng</Button></div>
        </form>:null}
      </section>
      {selected&&activeMilestone?<section className="mt-12" aria-label="Minh chứng và yêu cầu giải ngân">
        <div className="flex items-center gap-2"><FileText size={23} className="text-brand" aria-hidden="true"/><h2 className="text-2xl font-extrabold text-brand-ink">Minh chứng: {activeMilestone.title}</h2></div>
        <p className="mt-3 text-sm leading-7 text-muted">FE-03 chỉ gửi mô tả, URL và tham chiếu đã có. Không có API upload file nhị phân trong phạm vi này; không tự tạo file hay giả mạo SHA-256.</p>
        <div className="mt-5 space-y-3">
          {evidenceSummary.status==='loading'?<StatusMessage title="Đang tải minh chứng…"/>:null}
          {evidenceSummary.status==='error'?<StatusMessage error title="Không tải được minh chứng" detail={evidence.error?.message}/>:null}
          {evidenceSummary.status==='unknown'?<StatusMessage error title="Chưa xác định được minh chứng" detail="Backend chưa cung cấp đủ dữ liệu minh chứng. Vui lòng tải lại."/>:null}
          {['error','unknown'].includes(evidenceSummary.status)?<Button variant="outline" onClick={()=>void evidence.refetch()}>Tải lại minh chứng</Button>:null}
          {evidenceSummary.status==='empty'?<StatusMessage title="Chưa có minh chứng"/>:null}
          {evidenceSummary.status==='ready'?evidence.data?.data?.map(ev=><article key={ev.id} className="rounded-xl border border-border bg-surface p-5">
            <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="font-bold text-brand-ink">{ev.title}</h3><span className="text-xs font-semibold text-warning">{evidenceState(ev.evidenceStatus)}</span></div>
            {ev.description?<p className="mt-2 whitespace-pre-line text-sm text-muted">{ev.description}</p>:null}
            {ev.externalReference?<p className="mt-2 break-words text-xs text-muted">Tham chiếu: {ev.externalReference}</p>:null}
            {ev.reviewNote?<p className="mt-2 text-xs text-muted">Ghi chú Admin: {ev.reviewNote}</p>:null}
          </article>):null}
        </div>
        {evidenceSummary.status==='ready'||evidenceSummary.status==='empty'?<RecordPagination page={evidencePage} pages={evidence.data?.totalPages??0} onChange={setEvidencePage}/>:null}
        {['ACTIVE','PAUSED','COMPLETED'].includes(campaign.data.campaignStatus??'')&&['IN_PROGRESS','EVIDENCE_SUBMITTED','PARTIALLY_DISBURSED'].includes(activeMilestone.milestoneStatus??'')?<form onSubmit={e=>void submitEvidence(e)} className="mt-7 grid max-w-3xl gap-4 rounded-card border border-border bg-surface p-6">
          <h3 className="text-lg font-bold text-brand-ink">Gửi minh chứng tham chiếu</h3>
          <Input id="evidence-title" label="Tên minh chứng" required maxLength={200} value={evidenceForm.title} onChange={e=>setEvidenceForm({...evidenceForm,title:e.target.value})}/>
          <div><label htmlFor="evidence-type" className="mb-2 block text-sm font-semibold">Loại minh chứng</label><select id="evidence-type" className="min-h-11 w-full rounded-xl border border-border bg-surface px-3" value={evidenceForm.evidenceType} onChange={e=>setEvidenceForm({...evidenceForm,evidenceType:e.target.value as EvidenceType})}>{evidenceTypes.map(item=><option key={item.value} value={item.value}>{item.label}</option>)}</select></div>
          <div><label htmlFor="evidence-description" className="mb-2 block text-sm font-semibold">Mô tả nội dung</label><textarea id="evidence-description" maxLength={5000} rows={3} className="w-full rounded-xl border border-border bg-surface p-3 text-sm" value={evidenceForm.description} onChange={e=>setEvidenceForm({...evidenceForm,description:e.target.value})}/></div>
          <Input id="evidence-reference" label="Mã/nguồn tham chiếu có thật (nếu có)" maxLength={500} value={evidenceForm.externalReference} onChange={e=>setEvidenceForm({...evidenceForm,externalReference:e.target.value})}/>
          <Input id="evidence-url" label="URL minh chứng https:// hoặc sim:// (nếu có)" maxLength={500} value={evidenceForm.fileUrl} onChange={e=>setEvidenceForm({...evidenceForm,fileUrl:e.target.value})}/>
          <Input id="evidence-hash" label="SHA-256 thực tế của tệp (nếu có)" maxLength={64} value={evidenceForm.fileHash} onChange={e=>setEvidenceForm({...evidenceForm,fileHash:e.target.value})}/>
          <Button type="submit" loading={saveEvidence.isPending}>Gửi minh chứng</Button>
        </form>:null}
        {campaign.data.campaignStatus==='ACTIVE'&&['IN_PROGRESS','EVIDENCE_SUBMITTED','PARTIALLY_DISBURSED'].includes(activeMilestone.milestoneStatus??'')&&evidenceSummary.hasEvidence?<form onSubmit={e=>void submitFunds(e)} className="mt-8 grid max-w-3xl gap-4 rounded-card border border-border bg-soft-mint p-6">
          <h3 className="flex items-center gap-2 text-lg font-bold text-brand-ink"><Landmark size={21} className="text-brand" aria-hidden="true"/> Đề nghị giải ngân mô phỏng</h3>
          <p className="text-xs leading-6 text-muted">Số tiền còn lại theo Backend: {amountLabel(activeMilestone.remainingAmount,campaign.data.currency)}. Admin độc lập xem xét, phê duyệt và ghi nhận giải ngân; Creator không chuyển tiền.</p>
          <Input id="disbursement-amount" label="Số tiền đề nghị (VND)" type="number" step="1" min="1" required value={amount} onChange={e=>setAmount(e.target.value)}/>
          <div><label htmlFor="disbursement-reason" className="mb-2 block text-sm font-semibold">Mục đích sử dụng</label><textarea id="disbursement-reason" required maxLength={2000} rows={3} className="w-full rounded-xl border border-border bg-surface p-3 text-sm" value={reason} onChange={e=>setReason(e.target.value)}/></div>
          <Button type="submit" loading={requestFunds.isPending}>Gửi đề nghị (không chuyển tiền)</Button>
        </form>:null}
      </section>:null}
      <section className="mt-14" aria-labelledby="creator-disbursements">
        <h2 id="creator-disbursements" className="flex items-center gap-2 text-2xl font-extrabold text-brand-ink"><Landmark className="text-brand" size={24} aria-hidden="true"/> Lịch sử đề nghị giải ngân</h2>
        <div className="mt-5 space-y-3">
          {disbursements.isPending?<StatusMessage title="Đang tải đề nghị…"/>:null}
          {disbursements.isError?<StatusMessage error title="Không tải được đề nghị" detail={disbursements.error.message}/>:null}
          {disbursements.isSuccess&&!disbursements.data.data?.length?<StatusMessage title="Chưa có đề nghị giải ngân"/>:null}
          {disbursements.data?.data?.map(item=><article key={item.id} className="rounded-card border border-border bg-surface p-6">
            <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs text-muted">{item.requestCode} · {dateLabel(item.createdAt)}</p><h3 className="mt-2 text-lg font-bold text-brand-ink">{amountLabel(item.requestedAmount,item.currency)}</h3><p className="mt-2 text-sm text-muted">{item.requestReason}</p></div><span className="rounded-full bg-soft-sand px-3 py-2 text-xs font-semibold text-warning">{disbursementState(item.requestStatus)}</span></div>
            {item.decisionReason?<p className="mt-3 text-sm text-muted">Phản hồi Admin: {item.decisionReason}</p>:null}
            {item.id&&item.requestStatus==='NEEDS_MORE_EVIDENCE'?<div className="mt-4 flex flex-wrap gap-3"><Button variant="outline" onClick={()=>{if(item.milestoneId){setSelected(item.milestoneId);setEvidencePage(0);window.scrollTo({top:0,behavior:'smooth'});}}}>Bổ sung minh chứng</Button><Button variant="outline" loading={fixFunds.isPending} onClick={()=>{if(window.confirm('Gửi lại yêu cầu để Admin xem xét?'))fixFunds.mutate(item.id!);}}>Gửi lại đề nghị</Button></div>:null}
            {item.id&&['SUBMITTED','NEEDS_MORE_EVIDENCE'].includes(item.requestStatus??'')?<div className="mt-3"><Button variant="outline" loading={cancelFunds.isPending} onClick={()=>{const reason=window.prompt('Lý do hủy đề nghị (bắt buộc):');if(reason?.trim())cancelFunds.mutate({id:item.id!,reason:reason.trim()});}}>Hủy đề nghị</Button></div>:null}
          </article>)}
        </div>
        {disbursements.isSuccess?<RecordPagination page={disbursementPage} pages={disbursements.data.totalPages??0} onChange={setDisbursementPage}/>:null}
        <div className="mt-6 flex items-start gap-3 rounded-xl bg-soft-sand p-5 text-sm leading-7 text-warning"><CircleAlert size={20} className="mt-1 shrink-0" aria-hidden="true"/> Mọi trạng thái phê duyệt, minh chứng và giải ngân ở đây do Backend trả về. Không có khoản chuyển tiền thực tế.</div>
      </section>
      <div className="mt-8 flex items-center gap-2 text-xs text-muted"><ShieldCheck size={18} className="text-brand" aria-hidden="true"/> Admin giữ quyền kiểm duyệt độc lập.</div>
    </>:null}
    </Container>
  </div>;
}
