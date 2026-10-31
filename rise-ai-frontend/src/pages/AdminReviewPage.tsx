import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, FileCheck2, MessagesSquare, ShieldAlert } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { creatorEvidence } from '@/api/creator';
import { getAdminDisputeMessages,getAdminItem,getDisbursementEvidence,performAdminAction,reviewAdminEvidence,adminQueues,type AdminQueue,type AdminOperation,type AdminItem } from '@/api/admin';
import type { DisputeResponse, EvidenceResponse } from '@/api/contracts';
import { useAuth } from '@/auth/auth-context';
import { Container } from '@/components/layout/Container';
import { AdminDecisionForm } from '@/components/ui/AdminDecisionForm';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { amountLabel, dateLabel } from '@/lib/campaign-display';
import { reviewChoices,shortId } from '@/lib/admin-display';

const kinds=new Set<string>(['organizations','campaigns','donations','refunds','disbursements','disputes','adjustments','settlements']);
function readValue(record:Record<string,unknown>,key:string):string{
  const value=record[key];
  return value===undefined||value===null||typeof value==='object'?'':String(value);
}
function AdminEvidenceRow({item,onChanged}:{item:EvidenceResponse;onChanged:()=>void}) {
  const mut=useMutation({
    mutationFn:({approve,note}:{approve:boolean;note:string})=>reviewAdminEvidence(item.id!,approve,note),
    onSuccess:onChanged,
  });
  return <article className="rounded-xl border border-border bg-surface p-5">
    <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="font-bold text-brand-ink">{item.title??shortId(item.id)}</h3><span className="text-xs font-semibold text-warning">{item.evidenceStatus??'Chưa rõ'}</span></div>
    <p className="mt-2 text-xs text-muted">Loại: {item.evidenceType??'—'} · Người gửi: {shortId(item.submittedBy)}</p>
    {item.description?<p className="mt-3 whitespace-pre-wrap text-sm text-muted">{item.description}</p>:null}
    {item.externalReference?<p className="mt-2 break-all text-xs text-muted">Tham chiếu: {item.externalReference}</p>:null}
    {item.fileUrl?<p className="mt-2 break-all text-xs text-muted">URL khai báo: {item.fileUrl}</p>:null}
    {item.fileHash?<p className="mt-2 break-all text-xs text-muted">SHA-256 khai báo: {item.fileHash} (chưa tự động xác thực tệp)</p>:null}
    {item.reviewNote?<p className="mt-2 text-xs text-muted">Phản hồi: {item.reviewNote}</p>:null}
    {item.evidenceStatus==='SUBMITTED'&&item.id?<AdminDecisionForm
      choices={[{id:'accept',label:'Chấp thuận minh chứng',required:false,critical:true},{id:'reject',label:'Từ chối minh chứng',required:true,critical:true}]}
      onExecute={(operation,reason)=>mut.mutateAsync({approve:operation==='accept',note:reason})}
      onComplete={onChanged}/>:null}
    {mut.isError?<p role="alert" className="mt-2 text-sm text-danger">{mut.error.message}</p>:null}
  </article>;
}
function DisbursementEvidence({recordId,milestoneId,onChanged}:{recordId:string;milestoneId?:string;onChanged:()=>void}) {
  const {user}=useAuth();
  const [page,setPage]=useState(0);
  const query=useQuery({queryKey:['admin',user?.id,'disbursement-evidence',recordId,milestoneId,page],queryFn:()=>milestoneId?creatorEvidence(milestoneId,page):getDisbursementEvidence(recordId,page)});
  return <section className="mt-9" aria-label="Xem xét minh chứng">
    <h2 className="flex items-center gap-2 text-xl font-bold text-brand-ink"><FileCheck2 className="text-brand" size={23} aria-hidden="true"/> Minh chứng của cột mốc liên quan</h2>
    <p className="mt-2 text-xs leading-6 text-muted">Minh chứng chỉ là metadata được tác giả khai báo. Xác minh chứng cứ độc lập trước khi duyệt; không coi URL hoặc chuỗi SHA-256 là bằng chứng đã xác thực.</p>
    <div className="mt-5 space-y-3">
      {query.isPending?<StatusMessage title="Đang tải minh chứng…"/>:null}
      {query.isError?<StatusMessage error title="Không thể tải minh chứng" detail={query.error.message}/>:null}
      {query.isSuccess&&!query.data.data?.length?<StatusMessage title="Chưa có minh chứng trong đề nghị" detail="Kiểm tra minh chứng theo cột mốc nếu có; Backend quyết định điều kiện phê duyệt."/>:null}
      {query.data?.data?.map(x=><AdminEvidenceRow key={x.id} item={x} onChanged={()=>{void query.refetch();onChanged();}}/>)}
      {query.isSuccess?<RecordPagination page={page} pages={query.data.totalPages??0} onChange={setPage}/>:null}
    </div>
  </section>;
}
export function AdminReviewPage(){
  const {kind:kindParam,id}=useParams();
  const kind=kinds.has(kindParam??'')?kindParam as AdminQueue:null;
  const {user}=useAuth();
  const client=useQueryClient();
  const query=useQuery({queryKey:['admin',user?.id,'detail',kind,id],queryFn:()=>getAdminItem(kind!,id!),enabled:Boolean(kind&&id)});
  const [notice,setNotice]=useState('');
  const [messagePage,setMessagePage]=useState(0);
  const thread=useQuery({
    queryKey:['admin',user?.id,'dispute-thread',id,messagePage],
    queryFn:()=>getAdminDisputeMessages(id!,messagePage),
    enabled:Boolean(kind==='disputes'&&id),
  });
  if(!kind||!id)return <Container className="py-16"><StatusMessage error title="Đường dẫn xử lý không hợp lệ"/></Container>;
  const record=query.data as AdminItem|undefined;
  const r=(record??{}) as unknown as Record<string,unknown>;
  const status=readValue(r,'verificationStatus')||readValue(r,'campaignStatus')||readValue(r,'donationStatus')||readValue(r,'refundStatus')||
    readValue(r,'requestStatus')||readValue(r,'disputeStatus')||readValue(r,'adjustmentStatus')||readValue(r,'settlementStatus');
  const heading=readValue(r,'name')||readValue(r,'title')||readValue(r,'donationCode')||readValue(r,'refundCode')||readValue(r,'requestCode')||readValue(r,'disputeCode')||readValue(r,'adjustmentCode')||readValue(r,'settlementCode')||'Bản ghi '+shortId(id);
  const amount=typeof r.amount==='number'?r.amount:typeof r.requestedAmount==='number'?r.requestedAmount:typeof r.targetAmount==='number'?r.targetAmount:undefined;
  const reviewer=readValue(r,'assignedTo');
  const direction=readValue(r,'direction');
  const references=kind==='disputes'?(record as DisputeResponse|undefined)?.supportingReferences??[]:[];
  let actions=reviewChoices(kind,status);
  if(kind==='disputes'&&status==='UNDER_REVIEW'&&reviewer!==user?.id){
    actions=actions.filter(x=>x.id==='assign');
  }
  // Do not permit resolution while the evidence discussion is unavailable.
  if(kind==='disputes'&&!thread.isSuccess){
    actions=actions.filter(x=>!x.id.startsWith('resolve-'));
  }
  // A reviewer must see a valid debit/credit direction before approving an adjustment.
  if(kind==='adjustments'&&!['CREDIT','DEBIT','NEUTRAL'].includes(direction)){
    actions=actions.filter(x=>x.id!=='approve');
  }
  const refresh=()=>{setNotice('Backend đã ghi nhận thay đổi.');void client.invalidateQueries({queryKey:['admin',user?.id]});};
  return <Container className="py-10 sm:py-14">
    <Link to="/admin" className="inline-flex min-h-11 items-center gap-2 text-sm font-bold text-brand"><ArrowLeft size={17} aria-hidden="true"/> Về trung tâm xét duyệt</Link>
    {query.isPending?<div className="mt-6"><StatusMessage title="Đang tải hồ sơ…" /></div>:null}
    {query.isError?<div className="mt-6"><StatusMessage error title="Không thể tải hồ sơ" detail={query.error.message}/></div>:null}
    {record?<div className="mt-5 grid gap-8 lg:grid-cols-[minmax(0,1fr)_320px]">
      <div>
        <p className="text-xs font-extrabold uppercase tracking-widest text-brand">{adminQueues.find(x=>x.id===kind)?.label} · {shortId(id)}</p>
        <h1 className="mt-3 break-words text-3xl font-extrabold text-brand-ink">{heading}</h1>
        <span className="mt-5 inline-flex rounded-full bg-soft-sand px-4 py-2 text-sm font-semibold text-warning">Backend: {status||'Chưa rõ trạng thái'}</span>
        {notice?<p role="status" className="mt-5 rounded-xl bg-soft-mint p-4 text-sm text-brand-ink">{notice}</p>:null}
        <div className="mt-7 rounded-card border border-border bg-surface p-6">
          <h2 className="text-xl font-bold text-brand-ink">Thông tin hồ sơ</h2>
          {amount!==undefined?<p className="mt-4 text-lg font-extrabold text-brand">{amountLabel(amount,readValue(r,'currency')||'VND')}</p>:null}
          {kind==='adjustments'?<div className="mt-4 rounded-xl border border-border bg-soft-sand p-4" role="note">
            <p className="text-sm font-extrabold text-brand-ink">Chiều điều chỉnh: {direction||'THIẾU DỮ LIỆU'}</p>
            <p className="mt-2 text-sm font-semibold text-warning">{direction==='CREDIT'?'TĂNG số dư escrow mô phỏng':direction==='DEBIT'?'GIẢM số dư escrow mô phỏng':direction==='NEUTRAL'?'Điều chỉnh trung tính — không cộng/trừ số dư':'Không thể phê duyệt khi Backend chưa cung cấp chiều điều chỉnh hợp lệ.'}</p>
            {amount!==undefined?<p className="mt-2 text-sm">Giá trị đề nghị: {amountLabel(amount,readValue(r,'currency')||'VND')}</p>:null}
          </div>:null}
          <dl className="mt-5 grid gap-4 text-sm">
            {(['id','campaignId','organizationId','milestoneId','donorId','openedBy','assignedTo','requestedBy','proposedBy','createdBy',
              'verificationNotes','reviewNote','shortDescription','fullDescription','problemStatement','solutionDescription',
              'reason','requestReason','description','decisionReason','resolution','externalPaymentStatus','simulatedReference',
              'direction','adjustmentStatus','settlementType','fundsReserved','simulated','createdAt','submittedAt','confirmedAt','recordedAt','releasedAt','endAt'] as const)
            .filter(key=>r[key]!==null&&r[key]!==undefined&&r[key]!=='')
            .map(key=><div key={key} className="grid gap-1 border-b border-border pb-3 sm:grid-cols-[160px_1fr]">
              <dt className="text-xs font-semibold text-muted">{key}</dt><dd className="break-all whitespace-pre-wrap text-sm text-copy">{readValue(r,key)}</dd>
            </div>)}
          </dl>
        </div>
        {kind==='disbursements'?<DisbursementEvidence recordId={id} milestoneId={readValue(r,'milestoneId')} onChanged={refresh}/>:null}
        {kind==='disputes'?<section className="mt-8 rounded-card border border-border bg-surface p-6" aria-label="Minh chứng và trao đổi tranh chấp">
          <h2 className="flex items-center gap-2 text-xl font-bold text-brand-ink"><MessagesSquare size={22} className="text-brand" aria-hidden="true"/> Minh chứng và trao đổi của người đóng góp</h2>
          <h3 className="mt-5 text-sm font-bold text-brand-ink">Tham chiếu hỗ trợ ({references.length})</h3>
          {references.length?<ul className="mt-3 list-disc space-y-2 pl-5 text-sm text-copy">
            {references.map((reference,index)=><li key={index} className="break-all whitespace-pre-wrap">{reference}</li>)}
          </ul>:<p className="mt-2 text-sm text-muted">Không có tham chiếu hỗ trợ được khai báo trong hồ sơ.</p>}
          <h3 className="mt-7 text-sm font-bold text-brand-ink">Lịch sử trao đổi từ Backend</h3>
          <p className="mt-2 text-xs leading-6 text-muted">Kiểm tra tất cả các trang trao đổi và tham chiếu trước khi kết luận. Tin nhắn có thể chứa bổ sung minh chứng; chỉ hiển thị văn bản, không tự xác thực URL hoặc tệp.</p>
          {thread.isPending?<div className="mt-4"><StatusMessage title="Đang tải các trao đổi…"/></div>:null}
          {thread.isError?<div className="mt-4">
            <StatusMessage error title="Không tải được trao đổi — chưa thể kết luận" detail={thread.error.message}/>
            <button type="button" className="mt-3 text-sm font-bold text-brand underline" onClick={()=>void thread.refetch()}>Tải lại trao đổi</button>
          </div>:null}
          {thread.isSuccess&&!thread.data.data?.length?<div className="mt-4"><StatusMessage title="Chưa có tin nhắn trao đổi"/></div>:null}
          {thread.data?.data?.map((message,index)=><article key={message.id??index} className="mt-4 rounded-xl border border-border bg-canvas p-4">
            <p className="text-xs font-semibold text-muted">Người gửi {shortId(message.authorId)} · {dateLabel(message.createdAt)}</p>
            <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-7 text-copy">{message.message||'Tin nhắn trống'}</p>
          </article>)}
          {thread.isSuccess?<><p className="mt-4 text-xs text-muted">{thread.data.totalElements??thread.data.data?.length??0} tin nhắn · Trang {messagePage+1}/{Math.max(1,thread.data.totalPages??1)}</p>
            <RecordPagination page={messagePage} pages={thread.data.totalPages??0} onChange={setMessagePage}/></>:null}
        </section>:null}
        {kind==='disputes'&&status==='OPEN'?<p className="mt-6 rounded-xl bg-soft-mint p-4 text-sm text-brand-ink">Cần phân công người xử lý trước khi kết luận. Nút “Nhận xử lý” chỉ gán Admin đang đăng nhập; Backend kiểm tra xung đột lợi ích.</p>:null}
      </div>
      <aside className="h-fit space-y-5 lg:sticky lg:top-28">
        <div className="rounded-card border border-border bg-surface p-6">
          <h2 className="text-xl font-bold text-brand-ink">Quyết định của Admin</h2>
          <p className="mt-2 text-xs leading-6 text-muted">Không tự phê duyệt hồ sơ liên quan đến bạn. Kiểm tra chứng cứ, quyền và điều kiện nghiệp vụ trước khi xác nhận.</p>
          {kind==='disputes'&&!thread.isSuccess?<p role="alert" className="mt-3 text-sm font-semibold text-warning">Chưa thể kết luận: cần tải được các trao đổi của tranh chấp.</p>:null}
          <AdminDecisionForm choices={actions} onExecute={(operation,reason)=>performAdminAction(kind,id,operation as AdminOperation,reason,user?.id)} onComplete={refresh}/>
        </div>
        <div className="flex items-start gap-3 rounded-xl bg-soft-sand p-5 text-xs leading-6 text-warning"><ShieldAlert className="mt-1 shrink-0" size={20} aria-hidden="true"/>
          Quyết định ghi sổ hoặc phê duyệt chỉ tác động hệ thống mô phỏng; không chuyển tiền ngoài đời thực.
        </div>
      </aside>
    </div>:null}
  </Container>;
}
