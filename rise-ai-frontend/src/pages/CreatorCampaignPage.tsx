import { useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ArrowRight, CircleAlert, ClipboardCheck, FileText, Flag, Landmark, ShieldCheck } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { ApiError } from '@/api/client';
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
  const [openForm,setOpenForm]=useState<'evidence'|'disbursement'|null>(null);
  const [evidencePage,setEvidencePage]=useState(0);
  const [disbursementPage,setDisbursementPage]=useState(0);
  const [evidenceForm,setEvidenceForm]=useState<EvidenceRequest>(evidenceBlank);
  const [amount,setAmount]=useState('');
  const [reason,setReason]=useState('');
  const [notice,setNotice]=useState('');
  const [error,setError]=useState('');
  const requestAttempts=useRef(new Map<string,string>());
  const campaign=useQuery({queryKey:[...key,'campaign',id],queryFn:()=>getCreatorCampaign(id!),enabled:Boolean(id)});
  const owned=Boolean(campaign.data?.creatorId&&campaign.data.creatorId===user?.id);
  const milestones=useQuery({queryKey:[...key,'milestones',id],queryFn:()=>listCampaignMilestones(id!),enabled:Boolean(id&&owned)});
  const disbursements=useQuery({queryKey:[...key,'disbursements',id,disbursementPage],queryFn:()=>creatorDisbursements(id!,disbursementPage),enabled:Boolean(id&&owned)});
  const evidence=useQuery({queryKey:[...key,'evidence',selected,evidencePage],queryFn:()=>creatorEvidence(selected!,evidencePage),enabled:Boolean(selected&&owned)});
  const evidenceSummary=creatorEvidenceSummary(evidence);
  const activeMilestone=milestones.data?.find(item=>item.id===selected);
  const editable=editableCampaign(campaign.data?.campaignStatus);
  const activeWork = ['IN_PROGRESS','EVIDENCE_SUBMITTED','PARTIALLY_DISBURSED'].includes(activeMilestone?.milestoneStatus??'');
  const canAddEvidence = activeWork && ['ACTIVE','PAUSED','COMPLETED'].includes(campaign.data?.campaignStatus??'');
  const canRequestFunds = activeWork && campaign.data?.campaignStatus==='ACTIVE' && evidenceSummary.hasEvidence;
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
    mutationFn:(params:{milestoneId:string;amount:number;reason:string;key:string;signature:string})=>createCreatorDisbursement(params.milestoneId,{amount:params.amount,reason:params.reason},params.key),
    async onSuccess(_,params){setDisbursementPage(0);setAmount('');setReason('');requestAttempts.current.delete(params.signature);setNotice('Đã gửi đề nghị giải ngân mô phỏng. Không có tiền thật được chuyển.');await invalidate();},
  });
  const fixFunds=useMutation({mutationFn:resubmitCreatorDisbursement,onSuccess(){setNotice('Đã gửi lại đề nghị để Admin xem xét.');invalidate();}});
  const cancelFunds=useMutation({
    mutationFn:({id:requestId,reason:why}:{id:string;reason:string})=>cancelCreatorDisbursement(requestId,why),
    onSuccess(){setNotice('Đã hủy đề nghị giải ngân.');invalidate();},
  });
  const globalError=action.error??saveMilestone.error??start.error??saveEvidence.error??requestFunds.error??fixFunds.error??cancelFunds.error;
  const globalErrorMessage = globalError instanceof ApiError && globalError.code === 'INSUFFICIENT_AVAILABLE_BALANCE'
    ? 'Số dư ký quỹ khả dụng không đủ cho đề nghị này. Kiểm tra khoản đóng góp đã được Admin xác nhận và các khoản đang được giữ chỗ; ngân sách còn lại của cột mốc không phải số dư ký quỹ khả dụng.'
    : globalError?.message;
  const campaignNextStep = campaign.data?.campaignStatus === 'APPROVED'
    ? 'Chiến dịch đã được duyệt nhưng chưa công bố. Chọn “Công bố chiến dịch” để chuyển sang hoạt động; sau đó có thể bắt đầu các cột mốc sẵn sàng.'
    : campaign.data?.campaignStatus === 'ACTIVE'
      ? 'Chiến dịch đã công bố. Bắt đầu cột mốc ở trạng thái “Sẵn sàng”, gửi minh chứng thực tế, rồi lập đề nghị giải ngân để Admin độc lập xét duyệt.'
      : editable
        ? 'Hoàn thiện hồ sơ và cột mốc, gửi xét duyệt, rồi công bố sau khi được Admin phê duyệt. Việc lưu bản nháp chưa làm chiến dịch công khai.'
        : campaign.data?.campaignStatus === 'PENDING_REVIEW'
          ? 'Chiến dịch đang chờ Admin xét duyệt. Sau khi được phê duyệt, bạn cần công bố chiến dịch trước khi bắt đầu cột mốc.'
          : 'Theo dõi trạng thái chiến dịch và cột mốc bên dưới. Backend tiếp tục kiểm tra quyền hạn và điều kiện cho từng thao tác.';
  function selectMilestone(milestoneId: string, form: 'evidence' | null = null) {
    setSelected(milestoneId);
    setEvidencePage(0);
    setOpenForm(form);
    setNotice('');
    setError('');
  }
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
    let key=requestAttempts.current.get(signature);
    if(!key){
      key=crypto.randomUUID();
      requestAttempts.current.set(signature,key);
    }
    try{await requestFunds.mutateAsync({milestoneId:selected,amount:n,reason:reason.trim(),key,signature});}catch{/* Keep the key for this payload while its outcome is uncertain. */}
  }
  if(!id)return <Container className="py-14"><StatusMessage error title="Thiếu mã chiến dịch"/></Container>;
  return <div className="rise-deep-page deep-creator-campaign creator-operations">
    <div className="rise-deep-topline" aria-hidden="true">
      <span>CREATOR / TRIỂN KHAI</span><span>03 · LỘ TRÌNH MINH CHỨNG</span>
    </div>
    <Container className="deep-inner creator-operations-inner">
    <Link to="/creator" className="ops-back"><ArrowLeft size={17} aria-hidden="true"/> Về không gian Creator</Link>
    {campaign.isPending?<div className="mt-7"><StatusMessage title="Đang tải chiến dịch…"/></div>:null}
    {campaign.isError?<div className="mt-7"><StatusMessage error title="Không tải được chiến dịch" detail={campaign.error.message}/></div>:null}
    {campaign.isSuccess&&!owned?<div className="mt-7"><StatusMessage error title="Bạn không quản lý chiến dịch này"/></div>:null}
    {campaign.data&&owned?<>
      <header className="ops-overview">
        <div className="ops-overview-heading">
          <svg className="ops-overview-art" viewBox="0 0 64 64" aria-hidden="true" focusable="false">
            <path className="ops-art-shadow" d="M12 45 34 36l22 9v6L34 60 12 52Z" />
            <path className="ops-art-base" d="M9 43 31 34l22 9v6L31 58 9 50Z" />
            <path className="ops-art-paper" d="M9 43 31 34l22 9-22 9Z" />
            <path className="ops-art-pole" d="M19 46V12" />
            <path className="ops-art-shadow" d="M20 15c11-7 21 6 33 0l-6 8 6 8c-12 6-22-7-33 0Z" />
            <path className="ops-art-base" d="M19 12c11-7 21 6 33 0l-6 8 6 8c-12 6-22-7-33 0Z" />
            <path className="ops-art-glint" d="M25 13c5-1 9 2 14 3" />
          </svg>
          <div>
            <p className="ops-kicker">Quản lý chiến dịch</p>
            <h1>{campaign.data.title}</h1>
            <p>{campaign.data.organization?.name??'Tổ chức'}</p>
          </div>
          <span className="ops-badge" data-state={campaign.data.campaignStatus}>{campaignState(campaign.data.campaignStatus)}</span>
        </div>
        <dl className="ops-overview-budget">
          <div><dt>Mục tiêu chiến dịch</dt><dd>{amountLabel(campaign.data.targetAmount,campaign.data.currency)}</dd></div>
          <div><dt>Ngân sách cột mốc</dt><dd>{milestones.isSuccess ? amountLabel(plannedTotal,campaign.data.currency) : '—'}</dd></div>
        </dl>
      </header>
      <p className="creator-next-step rounded-xl bg-soft-mint p-4 text-sm leading-7 text-brand-ink" role="note"><strong>Bước tiếp theo: </strong>{campaignNextStep}</p>
      <div className="ops-review">
        <details>
          <summary><ClipboardCheck size={19} aria-hidden="true"/> Quy trình xét duyệt</summary>
          <p>Cần ít nhất một cột mốc, tổng dự kiến bằng mục tiêu và ngày kết thúc trong tương lai để gửi duyệt. Admin xác minh tổ chức và phê duyệt chiến dịch; Creator chỉ được công bố sau khi đã được duyệt.</p>
        </details>
        {campaign.data.reviewNote?<p>Phản hồi xét duyệt: {campaign.data.reviewNote}</p>:null}
        {(editable || campaign.data.campaignStatus==='APPROVED') && <div className="ops-review-actions">
          {editable && <Link to={'/creator/campaigns/'+encodeURIComponent(id)+'/edit'} className="ops-edit-campaign">Chỉnh sửa chiến dịch</Link>}
          {editable?<Button disabled={!milestones.isSuccess||!milestones.data.length||Math.abs(plannedTotal-(campaign.data.targetAmount??0))>.001} loading={action.isPending} onClick={()=>{if(window.confirm('Gửi chiến dịch và các cột mốc để Admin xét duyệt?'))action.mutate('submit');}}>Gửi xét duyệt</Button>:null}
          {campaign.data.campaignStatus==='APPROVED'?<Button loading={action.isPending} onClick={()=>{if(window.confirm('Công bố chiến dịch đã được duyệt?'))action.mutate('publish');}}>Công bố chiến dịch</Button>:null}
        </div>}
      </div>
      {notice?<p className="mt-6 rounded-xl bg-soft-mint p-4 text-sm text-brand-ink" role="status">{notice}</p>:null}
      {error?<p className="mt-5 text-sm text-danger" role="alert">{error}</p>:null}
      {globalError?<p className="mt-5 text-sm text-danger" role="alert">{globalErrorMessage}</p>:null}
      <div className="ops-workbench">
      <section className="ops-panel ops-milestones" aria-labelledby="ops-milestones-title">
        <div className="ops-panel-heading"><span className="ops-icon" aria-hidden="true"><Flag size={22}/></span><h2 id="ops-milestones-title">Cột mốc triển khai</h2>{editable?<Button variant="outline" onClick={()=>openMilestone()}>+ Tạo cột mốc</Button>:null}</div>
        {milestones.isPending?<div className="mt-5"><StatusMessage title="Đang tải cột mốc…"/></div>:null}
        {milestones.isError?<div className="mt-5"><StatusMessage error title="Không tải được cột mốc" detail={milestones.error.message}/><Button variant="outline" onClick={()=>void milestones.refetch()}>Thử lại</Button></div>:null}
        {milestones.isSuccess&&!milestones.data.length?<div className="mt-5"><StatusMessage title="Chưa có cột mốc" detail="Tạo cột mốc trước khi gửi chiến dịch để Admin duyệt."/></div>:null}
        {milestones.isSuccess && milestones.data.length > 0?<div className="ops-milestone-list">
          {milestones.data.map(m=><article key={m.id} className="ops-milestone" data-selected={Boolean(m.id && selected===m.id)}>
            <div className="ops-milestone-stop">
              {m.sequenceNumber != null && <span aria-label={'Cột mốc ' + m.sequenceNumber}>{String(m.sequenceNumber).padStart(2, '0')}</span>}
            </div>
            <div className="ops-milestone-content">
              <div>
                <p className="ops-milestone-state" data-state={m.milestoneStatus}>{milestoneLabel(m.milestoneStatus)}</p>
                <h3>{m.title}</h3>
                <dl className="ops-milestone-budget">
                  <div><dt>Ngân sách</dt><dd>{amountLabel(m.plannedAmount,campaign.data.currency)}</dd></div>
                  <div><dt>Đã giải ngân · mô phỏng</dt><dd>{amountLabel(m.releasedAmount,campaign.data.currency)}</dd></div>
                </dl>
                <p className="ops-milestone-meta">Hạn: {dateLabel(m.dueAt)} · Minh chứng: {m.id===selected?(evidenceSummary.count??({loading:'Đang tải…',error:'Không tải được',unknown:'Chưa xác định',ready:'Có minh chứng (chưa có tổng số)',empty:'0'}[evidenceSummary.status])):(Number.isSafeInteger(m.evidenceCount)&&m.evidenceCount!>=0?m.evidenceCount:'Chưa tải')}</p>
              </div>
              {m.milestoneStatus==='READY' && campaign.data.campaignStatus!=='ACTIVE' ? <p className="mt-3 text-sm leading-6 text-muted">Chưa thể bắt đầu: chiến dịch cần được công bố và ở trạng thái đang hoạt động.</p> : null}
              <div className="ops-milestone-actions">
                {m.id?<Button variant="outline" aria-pressed={selected===m.id} aria-controls="ops-milestone-detail" onClick={()=>selectMilestone(m.id!)}>Minh chứng & giải ngân <ArrowRight size={16} aria-hidden="true"/></Button>:null}
                {editable?<Button variant="outline" onClick={()=>openMilestone(m)}>Sửa</Button>:null}
                {campaign.data.campaignStatus==='ACTIVE'&&m.milestoneStatus==='READY'&&m.id?<Button variant="outline" loading={start.isPending} onClick={()=>{if(window.confirm('Bắt đầu cột mốc này?'))start.mutate(m.id!);}}>Bắt đầu</Button>:null}
              </div>
            </div>
          </article>)}
        </div>:null}
      </section>
      <section id="ops-milestone-detail" className="ops-panel ops-detail" aria-labelledby="ops-detail-title">
      {selected&&activeMilestone?<div className="ops-detail-content">
        <div className="ops-panel-heading">
          <span className="ops-icon ops-icon-warm" aria-hidden="true"><FileText size={22}/></span>
          <div><p className="ops-kicker">Cột mốc {activeMilestone.sequenceNumber}</p><h2 id="ops-detail-title">{activeMilestone.title}</h2></div>
        </div>
        <div className="ops-detail-toolbar">
          <h3 id="ops-evidence-title">Minh chứng</h3>
          <div className="ops-detail-actions">
            {canAddEvidence && <Button type="button" variant="outline" aria-expanded={openForm==='evidence'} aria-controls={openForm==='evidence'?'ops-evidence-form':undefined} onClick={()=>setOpenForm(openForm==='evidence'?null:'evidence')}>Gửi minh chứng</Button>}
            {canRequestFunds && <Button type="button" aria-expanded={openForm==='disbursement'} aria-controls={openForm==='disbursement'?'ops-funds-form':undefined} onClick={()=>setOpenForm(openForm==='disbursement'?null:'disbursement')}>Đề nghị giải ngân</Button>}
          </div>
        </div>
        <div className="rounded-xl bg-soft-sand p-4 text-sm leading-7 text-brand-ink" role="note">
          <p><strong>Gửi minh chứng: </strong>{canAddEvidence ? 'Cột mốc đủ điều kiện nhận minh chứng. Chọn “Gửi minh chứng” để bổ sung tài liệu có thật.' : !activeWork ? 'Cột mốc cần đang triển khai, đã gửi minh chứng hoặc đã giải ngân một phần. Nếu đang sẵn sàng, hãy bắt đầu cột mốc sau khi công bố chiến dịch.' : 'Chiến dịch cần đang hoạt động, tạm dừng hoặc đã hoàn thành để bổ sung minh chứng.'}</p>
          <p className="mt-2"><strong>Đề nghị giải ngân: </strong>{campaign.data.campaignStatus!=='ACTIVE' ? 'Chỉ gửi khi chiến dịch đang hoạt động.' : !activeWork ? 'Bắt đầu triển khai cột mốc trước khi lập đề nghị.' : evidenceSummary.status==='loading' ? 'Đang tải dữ liệu minh chứng để xác định điều kiện.' : ['error','unknown'].includes(evidenceSummary.status) ? 'Tải lại minh chứng thành công trước khi xác định điều kiện gửi đề nghị.' : !evidenceSummary.hasEvidence ? 'Gửi ít nhất một minh chứng rồi chờ danh sách cập nhật để mở thao tác đề nghị.' : 'Đã có minh chứng. Chọn “Đề nghị giải ngân”; Backend vẫn kiểm tra ngân sách, số dư khả dụng, quyền và trạng thái khi gửi.'}</p>
        </div>
        <div className="ops-evidence-records" aria-labelledby="ops-evidence-title">
          {evidenceSummary.status==='loading'?<StatusMessage title="Đang tải minh chứng…"/>:null}
          {evidenceSummary.status==='error'?<StatusMessage error title="Không tải được minh chứng" detail={evidence.error?.message}/>:null}
          {evidenceSummary.status==='unknown'?<StatusMessage error title="Chưa xác định được minh chứng" detail="Backend chưa cung cấp đủ dữ liệu minh chứng. Vui lòng tải lại."/>:null}
          {['error','unknown'].includes(evidenceSummary.status)?<Button variant="outline" onClick={()=>void evidence.refetch()}>Tải lại minh chứng</Button>:null}
          {evidenceSummary.status==='empty'?<StatusMessage title="Chưa có minh chứng"/>:null}
          {evidenceSummary.status === 'ready' && evidence.data?.data?.map(ev => {
            const typeLabel = evidenceTypes.find(type => type.value === ev.evidenceType)?.label;
            return <article key={ev.id} className="ops-evidence-record" data-state={ev.evidenceStatus}>
              <svg className="ops-document-icon" viewBox="0 0 72 76" width="72" height="76" aria-hidden="true" focusable="false">
                <path className="ops-document-shadow" d="m10 63 50-3 9 7-50 6Z" />
                <path className="ops-document-depth" d="M15 15h18l5 6h24l-3 40-46 5-5-42a8 8 0 0 1 7-9Z" />
                <path className="ops-document-sheet" d="m22 5 26 2 10 13-6 39-34-3Z" />
                <path className="ops-document-fold" d="m48 7-2 12 12 1Z" />
                <path className="ops-document-lines" d="m28 24 12 1m-13 6 20 2m-21 5 13 1" />
                <path className="ops-document-cover" d="m7 44 22-2 5 4 28-4-3 19-46 5Z" />
                <path className="ops-document-glint" d="m14 49 12-1m12 2 17-2" />
              </svg>
              <div className="ops-evidence-copy">
                {typeLabel && <p className="ops-evidence-type">{typeLabel}</p>}
                <h3>{ev.title}</h3>
                {ev.description && <p className="ops-evidence-description">{ev.description}</p>}
                {ev.externalReference && <p className="ops-evidence-reference">Tham chiếu nguồn <strong>{ev.externalReference}</strong></p>}
                {ev.reviewNote && <p className="ops-evidence-note">Ghi chú Admin: {ev.reviewNote}</p>}
              </div>
              {ev.evidenceStatus && <div className="ops-evidence-status">
                {ev.evidenceStatus === 'ACCEPTED' && <svg viewBox="0 0 56 56" width="56" height="56" aria-hidden="true" focusable="false">
                  <path className="ops-approval-ribbon" d="m14 33-5 19 12-5 6 7 3-16m11-5 6 19-12-5-6 7-3-16" />
                  <path className="ops-approval-seal" d="m28 4 6 4 7 1 3 6 5 5-2 7 1 7-6 4-4 6-7-1-6 3-6-4-7-1-3-6-5-5 2-7-1-7 6-4 4-6 7 1Z" />
                  <circle cx="27" cy="25" r="13" className="ops-approval-ring" />
                  <path className="ops-approval-check" d="m21 25 4 4 8-9" />
                </svg>}
                {ev.evidenceStatus === 'SUBMITTED' && <svg viewBox="0 0 28 28" width="23" height="23" aria-hidden="true" focusable="false">
                  <path className="ops-waiting-glass" d="M8 4h12m-12 20h12M9 4v4c0 3 3 4 5 6-2 2-5 3-5 6v4m10-20v4c0 3-3 4-5 6 2 2 5 3 5 6v4" />
                  <path className="ops-waiting-sand" d="m11 22 3-4 3 4Z" />
                </svg>}
                <span>{evidenceState(ev.evidenceStatus)}</span>
              </div>}
            </article>;
          })}
        </div>
        {evidenceSummary.status==='ready'||evidenceSummary.status==='empty'?<RecordPagination page={evidencePage} pages={evidence.data?.totalPages??0} onChange={setEvidencePage}/>:null}
        {canAddEvidence && openForm==='evidence' ? <form id="ops-evidence-form" aria-labelledby="ops-evidence-form-title" onSubmit={e=>void submitEvidence(e)} className="ops-action-form">
          <h3 id="ops-evidence-form-title">Gửi minh chứng tham chiếu</h3>
          <p>Dùng mô tả, URL hoặc mã tham chiếu của minh chứng đã có.</p>
          <Input id="evidence-title" label="Tên minh chứng" required maxLength={200} value={evidenceForm.title} onChange={e=>setEvidenceForm({...evidenceForm,title:e.target.value})}/>
          <div><label htmlFor="evidence-type" className="mb-2 block text-sm font-semibold">Loại minh chứng</label><select id="evidence-type" className="min-h-11 w-full rounded-xl border border-border bg-surface px-3" value={evidenceForm.evidenceType} onChange={e=>setEvidenceForm({...evidenceForm,evidenceType:e.target.value as EvidenceType})}>{evidenceTypes.map(item=><option key={item.value} value={item.value}>{item.label}</option>)}</select></div>
          <div><label htmlFor="evidence-description" className="mb-2 block text-sm font-semibold">Mô tả nội dung</label><textarea id="evidence-description" maxLength={5000} rows={3} className="w-full rounded-xl border border-border bg-surface p-3 text-sm" value={evidenceForm.description} onChange={e=>setEvidenceForm({...evidenceForm,description:e.target.value})}/></div>
          <Input id="evidence-reference" label="Mã/nguồn tham chiếu có thật (nếu có)" maxLength={500} value={evidenceForm.externalReference} onChange={e=>setEvidenceForm({...evidenceForm,externalReference:e.target.value})}/>
          <Input id="evidence-url" label="URL minh chứng https:// hoặc sim:// (nếu có)" maxLength={500} value={evidenceForm.fileUrl} onChange={e=>setEvidenceForm({...evidenceForm,fileUrl:e.target.value})}/>
          <Input id="evidence-hash" label="SHA-256 thực tế của tệp (nếu có)" maxLength={64} value={evidenceForm.fileHash} onChange={e=>setEvidenceForm({...evidenceForm,fileHash:e.target.value})}/>
          <Button type="submit" loading={saveEvidence.isPending}>Gửi minh chứng</Button>
        </form>:null}
        {canRequestFunds && openForm==='disbursement' ? <form id="ops-funds-form" aria-labelledby="ops-funds-title" onSubmit={e=>void submitFunds(e)} className="ops-action-form">
          <h3 id="ops-funds-title">Đề nghị giải ngân mô phỏng</h3>
          <p className="text-xs leading-6 text-muted">Ngân sách cột mốc còn lại theo Backend: {amountLabel(activeMilestone.remainingAmount,campaign.data.currency)}. Đây không phải số dư ký quỹ khả dụng. Backend kiểm tra số dư khi nhận đề nghị; Admin độc lập xem xét, phê duyệt và ghi nhận giải ngân. Creator không chuyển tiền.</p>
          <Input id="disbursement-amount" label="Số tiền đề nghị (VND)" type="number" step="1" min="1" required value={amount} onChange={e=>setAmount(e.target.value)}/>
          <div><label htmlFor="disbursement-reason" className="mb-2 block text-sm font-semibold">Mục đích sử dụng</label><textarea id="disbursement-reason" required maxLength={2000} rows={3} className="w-full rounded-xl border border-border bg-surface p-3 text-sm" value={reason} onChange={e=>setReason(e.target.value)}/></div>
          <Button type="submit" loading={requestFunds.isPending}>Gửi đề nghị (không chuyển tiền)</Button>
        </form>:null}
      </div>:<div className="ops-detail-empty">
        <span className="ops-icon ops-icon-warm" aria-hidden="true"><FileText size={22}/></span>
        <div><h2 id="ops-detail-title">Chi tiết cột mốc</h2><p>Chọn một cột mốc để xem minh chứng và các thao tác hiện có.</p></div>
      </div>}
      </section>
        {milestoneForm&&editable?<form onSubmit={e=>void submitMilestone(e)} className="ops-panel ops-milestone-form grid gap-5 sm:grid-cols-2">
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
      </div>
      <section className="ops-panel ops-history" aria-labelledby="creator-disbursements">
        <div className="ops-panel-heading"><span className="ops-icon" aria-hidden="true"><Landmark size={22}/></span><h2 id="creator-disbursements">Lịch sử đề nghị giải ngân</h2></div>
        <div className="ops-history-records">
          {disbursements.isPending?<StatusMessage title="Đang tải đề nghị…"/>:null}
          {disbursements.isError?<StatusMessage error title="Không tải được đề nghị" detail={disbursements.error.message}/>:null}
          {disbursements.isSuccess&&!disbursements.data.data?.length?<StatusMessage title="Chưa có đề nghị giải ngân"/>:null}
          {disbursements.data?.data?.map(item=><article key={item.id} className="ops-history-record">
            <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs text-muted">{item.requestCode} · {dateLabel(item.createdAt)}</p><h3 className="ops-history-amount">{amountLabel(item.requestedAmount,item.currency)}</h3><p className="mt-2 text-sm text-muted">{item.requestReason}</p></div><span className="ops-badge" data-state={item.requestStatus}>{disbursementState(item.requestStatus)}</span></div>
            {item.decisionReason?<p className="mt-3 text-sm text-muted">Phản hồi Admin: {item.decisionReason}</p>:null}
            {item.id&&item.requestStatus==='NEEDS_MORE_EVIDENCE'?<div className="mt-4 flex flex-wrap gap-3"><Button variant="outline" onClick={()=>{if(item.milestoneId){selectMilestone(item.milestoneId,'evidence');document.getElementById('ops-milestone-detail')?.scrollIntoView({block:'start'});}}}>Bổ sung minh chứng</Button><Button variant="outline" loading={fixFunds.isPending} onClick={()=>{if(window.confirm('Gửi lại yêu cầu để Admin xem xét?'))fixFunds.mutate(item.id!);}}>Gửi lại đề nghị</Button></div>:null}
            {item.id&&['SUBMITTED','NEEDS_MORE_EVIDENCE'].includes(item.requestStatus??'')?<div className="mt-3"><Button variant="outline" loading={cancelFunds.isPending} onClick={()=>{const reason=window.prompt('Lý do hủy đề nghị (bắt buộc):');if(reason?.trim())cancelFunds.mutate({id:item.id!,reason:reason.trim()});}}>Hủy đề nghị</Button></div>:null}
          </article>)}
        </div>
        {disbursements.isSuccess?<RecordPagination page={disbursementPage} pages={disbursements.data.totalPages??0} onChange={setDisbursementPage}/>:null}
        <div className="ops-simulation-note"><CircleAlert size={20} className="mt-1 shrink-0" aria-hidden="true"/> Mọi trạng thái phê duyệt, minh chứng và giải ngân ở đây do Backend trả về. Không có khoản chuyển tiền thực tế.</div>
      </section>
      <div className="ops-audit-note"><ShieldCheck size={18} className="text-brand" aria-hidden="true"/> Admin giữ quyền kiểm duyệt độc lập.</div>
    </>:null}
    </Container>
  </div>;
}
