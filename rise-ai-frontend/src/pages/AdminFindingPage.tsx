import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, BookOpenText, ShieldAlert } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { getFinding, getFindingHistory, reviewFinding, type FindingReviewStatus } from '@/api/monitoring';
import { Container } from '@/components/layout/Container';
import { AdminMonitoringNav } from '@/components/ui/AdminMonitoringNav';
import { Button } from '@/components/ui/Button';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { countText, findingStatusText, readableJson, reviewTargets, severityText, shortRef, timeText } from '@/lib/monitoring-display';

export function AdminFindingPage(){
  const {id}=useParams();
  const {user}=useAuth();
  const cache=useQueryClient();
  const [target,setTarget]=useState<Exclude<FindingReviewStatus,'OPEN'>|''>('');
  const [note,setNote]=useState('');
  const [confirmed,setConfirmed]=useState(false);
  const [notice,setNotice]=useState('');
  const key=['admin',user?.id,'monitoring'] as const;
  const detail=useQuery({queryKey:[...key,'finding',id],queryFn:()=>getFinding(id!),enabled:!!id});
  const history=useQuery({queryKey:[...key,'finding-history',id],queryFn:()=>getFindingHistory(id!),enabled:!!id});
  const finding=detail.data?.finding;
  const choices=reviewTargets(finding?.reviewStatus);
  const selected=choices.includes(target as Exclude<FindingReviewStatus,'OPEN'>);
  const review=useMutation({
    mutationFn:()=>reviewFinding(id!,target as Exclude<FindingReviewStatus,'OPEN'>,note.trim()),
    onSuccess(result){
      setNotice('Backend đã ghi nhận: '+(result.reviewStatus?findingStatusText[result.reviewStatus]:'thao tác hoàn tất'));
      setTarget('');setNote('');setConfirmed(false);
      void cache.invalidateQueries({queryKey:key});
    },
  });
  async function submit(e:FormEvent<HTMLFormElement>){
    e.preventDefault();
    if(!id||!detail.isSuccess||!finding||!history.isSuccess||!selected||!note.trim()||!confirmed||review.isPending)return;
    try{await review.mutateAsync();}catch{/* Show authoritative Backend error. */}
  }
  return <Container className="py-10 sm:py-14">
    <Link to="/admin/monitoring" className="inline-flex min-h-11 items-center gap-2 text-sm font-bold text-brand"><ArrowLeft size={17} aria-hidden="true"/> Danh sách phát hiện</Link>
    <h1 className="mt-4 text-3xl font-extrabold text-brand-ink">Kiểm tra một cảnh báo AI</h1>
    <p className="mt-3 max-w-3xl text-sm leading-7 text-muted">Kiểm tra dữ kiện, những lần xuất hiện và lịch sử xử lý trước khi ghi nhận kết luận. Monitoring chỉ cung cấp thông tin tham khảo; quyết định nghiệp vụ thuộc Admin và Backend.</p>
    <AdminMonitoringNav/>
    {!id?<div className="mt-7"><StatusMessage error title="Thiếu ID phát hiện"/></div>:null}
    {detail.isPending?<div className="mt-7"><StatusMessage title="Đang tải cảnh báo…"/></div>:null}
    {detail.isError?<div className="mt-7"><StatusMessage error title="Không tải được cảnh báo" detail={detail.error.message}/></div>:null}
    {detail.isSuccess&&!finding?<div className="mt-7"><StatusMessage error title="Backend không trả thông tin phát hiện"/></div>:null}
    {finding?<div className="mt-8 grid min-w-0 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(280px,330px)]">
      <div className="min-w-0 space-y-6">
        <section className="min-w-0 rounded-card border border-border bg-surface p-6">
          <p className="break-all text-xs font-bold text-brand">Rule {finding.ruleId??'Không xác định'} · {finding.ruleVersion??'Không có phiên bản'} · {shortRef(finding.id)}</p>
          <h2 className="mt-3 break-words text-xl font-extrabold text-brand-ink">{finding.explanation??'Chưa có diễn giải'}</h2>
          <div className="mt-4 flex flex-wrap gap-2 text-xs font-bold">
            <span className="rounded-full bg-soft-sand px-3 py-2 text-warning">{finding.severity?severityText[finding.severity]:'Không rõ mức độ'}</span>
            <span className="rounded-full bg-soft-mint px-3 py-2 text-brand-ink">{finding.reviewStatus?findingStatusText[finding.reviewStatus]:'Không rõ trạng thái'}</span>
            <span className="rounded-full border border-border px-3 py-2 text-muted">{finding.advisoryOnly===true?'Chỉ hỗ trợ quyết định':'Trạng thái tư vấn chưa xác định'}</span>
          </div>
          <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2">
            {([
              ['ID',finding.id],['Loại detector',finding.detectorType],['Nhóm',finding.category],
              ['Đối tượng',finding.subjectType],['ID đối tượng',finding.subjectId],['Chiến dịch',finding.campaignId],
              ['Cột mốc',finding.milestoneId],['Đề nghị giải ngân',finding.disbursementRequestId],
              ['Lần đầu',timeText(finding.firstSeenAt)],['Lần gần nhất',timeText(finding.lastSeenAt)],
              ['Số lần xuất hiện',countText(finding.occurrenceCount)],['Mới sau đánh giá',countText(finding.newOccurrencesSinceReview)],
            ] as const).map(([label,value])=><div key={label} className="min-w-0 border-b border-border pb-3">
              <dt className="text-xs font-semibold text-muted">{label}</dt><dd className="mt-1 break-all text-sm text-copy">{value??'—'}</dd>
            </div>)}
          </dl>
          <h3 className="mt-6 text-sm font-bold text-brand-ink">Hướng kiểm tra được đề xuất</h3>
          <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-7 text-copy">{finding.recommendedAction??'Không có đề xuất cụ thể'}</p>
          <h3 className="mt-6 text-sm font-bold text-brand-ink">Dữ kiện do Monitoring cung cấp</h3>
          {finding.facts?.length?<ol className="mt-3 space-y-3">{finding.facts.map((fact,index)=><li key={index} className="min-w-0 rounded-xl bg-soft-sand p-4">
            <p className="text-xs font-semibold text-warning">Dữ kiện {index+1}</p>
            <pre className="mt-2 overflow-x-auto whitespace-pre-wrap break-all text-xs leading-6 text-copy">{readableJson(fact)}</pre>
          </li>)}</ol>:<p className="mt-2 text-sm text-muted">Chưa có dữ kiện chi tiết.</p>}
        </section>
        <section className="min-w-0 rounded-card border border-border bg-surface p-6" aria-label="Các lần phát hiện">
          <h2 className="text-lg font-bold text-brand-ink">Các lần ghi nhận ({countText(finding.occurrenceCount)})</h2>
          <p className="mt-2 text-xs text-muted">Những ghi nhận này không phải bằng chứng độc lập về sai phạm.</p>
          {detail.data?.occurrences?.length?<ol className="mt-4 space-y-3">{detail.data.occurrences.map((x,index)=><li key={x.sourceEventId??index} className="min-w-0 rounded-xl border border-border p-4">
            <p className="text-xs font-semibold text-muted">{timeText(x.observedAt)} · {x.severity?severityText[x.severity]:'Mức độ chưa rõ'}</p>
            <p className="mt-2 break-all text-xs text-muted">Event {x.sourceEventId??'—'} · Actor {x.actorId??'—'}</p>
            {x.aiNote?<p className="mt-3 whitespace-pre-wrap break-words text-sm text-copy">Ghi chú giám sát: {x.aiNote}</p>:null}
          </li>)}</ol>:<StatusMessage title="Chưa có lịch sử ghi nhận chi tiết"/>}
        </section>
        <section className="min-w-0 rounded-card border border-border bg-surface p-6">
          <h2 className="flex items-center gap-2 text-lg font-bold text-brand-ink"><BookOpenText size={19} aria-hidden="true" className="text-brand"/> Lịch sử đánh giá</h2>
          {history.isPending?<div className="mt-4"><StatusMessage title="Đang tải lịch sử đánh giá…"/></div>:null}
          {history.isError?<div className="mt-4"><StatusMessage error title="Lịch sử không truy cập được — tạm khóa quyết định" detail={history.error.message}/></div>:null}
          {history.isSuccess&&!history.data.length?<p className="mt-4 text-sm text-muted">Chưa có lần đánh giá trước đó.</p>:null}
          {history.data?.map((x,index)=><article key={x.id??index} className="mt-4 rounded-xl border border-border p-4">
            <p className="text-xs font-semibold text-muted">{timeText(x.createdAt)} · Người đánh giá {shortRef(x.changedBy)}</p>
            <p className="mt-2 text-sm font-bold">{x.fromStatus??'Ban đầu'} → {x.toStatus??'Chưa rõ'}</p>
            {x.reason?<p className="mt-2 whitespace-pre-wrap break-words text-sm text-copy">{x.reason}</p>:null}
          </article>)}
          {finding.reviewedAt?<p className="mt-4 text-xs text-muted">Đánh giá gần nhất: {timeText(finding.reviewedAt)} · {shortRef(finding.reviewedBy)}</p>:null}
          {finding.reviewNote?<p className="mt-2 whitespace-pre-wrap break-words text-sm text-copy">{finding.reviewNote}</p>:null}
        </section>
      </div>
      <aside className="min-w-0 space-y-4">
        <section className="rounded-card border border-border bg-surface p-5">
          <h2 className="text-lg font-extrabold text-brand-ink">Quyết định của người kiểm tra</h2>
          <p className="mt-2 text-xs leading-6 text-muted">Backend ngăn xung đột lợi ích nếu bạn tạo hành động liên quan hoặc sở hữu chiến dịch. Không thể tự động phê duyệt, tạm dừng hoặc giải ngân.</p>
          {!choices.length?<p className="mt-4 text-sm text-muted">Không có chuyển trạng thái đánh giá nào được phép tiếp tục.</p>:null}
          {!history.isSuccess?<p role="alert" className="mt-3 text-xs font-semibold text-warning">Cần tải thành công lịch sử đánh giá trước khi đưa ra quyết định.</p>:null}
          {choices.length?<form className="mt-4 space-y-4" onSubmit={e=>void submit(e)}>
            <div><label htmlFor="finding-target" className="mb-2 block text-sm font-bold">Hướng xử lý</label>
              <select id="finding-target" className="min-h-11 w-full rounded-xl border border-border bg-surface px-3 text-sm" value={target} onChange={e=>{setTarget(e.target.value as typeof target);setConfirmed(false);}}>
                <option value="">Chọn kết luận</option>
                {choices.map(choice=><option value={choice} key={choice}>{findingStatusText[choice]}</option>)}
              </select>
            </div>
            <div><label htmlFor="finding-review-note" className="mb-2 block text-sm font-bold">Căn cứ đánh giá (bắt buộc)</label>
              <textarea id="finding-review-note" className="w-full rounded-xl border border-border bg-surface p-3 text-sm" rows={5} maxLength={2000} required value={note} onChange={e=>setNote(e.target.value)} placeholder="Ghi rõ chứng cứ, lý do và đối chiếu thực tế"/>
            </div>
            <label className="flex items-start gap-2 text-sm text-copy"><input className="mt-1 accent-brand" type="checkbox" checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/> Tôi đã kiểm tra các dữ kiện và hiểu đây chỉ là đánh giá cảnh báo, không phải quyết định giải ngân.</label>
            <Button type="submit" loading={review.isPending} disabled={!selected||!confirmed||!note.trim()||!detail.isSuccess||!history.isSuccess}>Lưu đánh giá</Button>
            {review.isError?<p role="alert" className="break-words text-sm text-danger">{review.error.message}</p>:null}
          </form>:null}
          {notice?<p role="status" className="mt-4 rounded-xl bg-soft-mint p-4 text-sm text-brand-ink">{notice}</p>:null}
        </section>
        <p className="flex gap-2 rounded-xl bg-soft-sand p-4 text-xs leading-6 text-warning"><ShieldAlert size={18} className="shrink-0" aria-hidden="true"/> Cảnh báo chỉ hỗ trợ giám sát. Không suy diễn cảnh báo thành bằng chứng rằng chiến dịch vi phạm.</p>
      </aside>
    </div>:null}
  </Container>;
}
