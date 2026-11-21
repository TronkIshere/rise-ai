import { WorkspaceIllustration } from '@/components/ui/WorkspaceIllustration';
import { AdminMasthead, AdminState } from '@/components/ui/AdminOperations';
import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Activity, ArrowLeft, ClipboardCheck, Info, ShieldAlert } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { getFinding, getFindingHistory, reviewFinding, type FindingReviewStatus } from '@/api/monitoring';
import { Container } from '@/components/layout/Container';
import { AdminMonitoringNav } from '@/components/ui/AdminMonitoringNav';
import { Button } from '@/components/ui/Button';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { countText, findingStatusText, readableJson, reviewTargets, severityText, timeText } from '@/lib/monitoring-display';

const factLabels = new Map([
  ['fileHash','Mã SHA-256 của tệp'], ['otherCampaignCount','Số chiến dịch khác'],
  ['earlierEvidenceIds','Mã minh chứng trước đó'], ['matchCount','Số kết quả trùng khớp'],
  ['externalReference','Tham chiếu nguồn'],
]);

const historyStatus = (status:string|undefined, fallback:string) =>
  status ? findingStatusText[status as FindingReviewStatus] ?? status : fallback;

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
  return <div className="rise-deep-page deep-admin-finding admin-ops">
    <div className="rise-deep-topline" aria-hidden="true">
      <span>ADMIN / ĐÁNH GIÁ TÍN HIỆU</span><span>07 · HỒ SƠ PHÁT HIỆN</span>
    </div>
    <Container className="deep-inner py-10 sm:py-14">
    <Link to="/admin/monitoring" className="inline-flex min-h-11 items-center gap-2 text-sm font-bold text-brand"><ArrowLeft size={17} aria-hidden="true"/> Danh sách phát hiện</Link>
    <AdminMasthead title="Kiểm tra một cảnh báo AI" eyebrow="Hồ sơ điều tra" art="scale">
      <p>Đối chiếu tín hiệu, dữ kiện và lịch sử trước khi đánh giá. Cảnh báo chỉ hỗ trợ kiểm tra, không phải kết luận sai phạm.</p>
    </AdminMasthead>
    <AdminMonitoringNav/>
    {!id?<div className="mt-7"><StatusMessage error title="Thiếu ID phát hiện"/></div>:null}
    {detail.isPending?<div className="mt-7"><StatusMessage title="Đang tải cảnh báo…"/></div>:null}
    {detail.isError?<div className="mt-7"><StatusMessage error title="Không tải được cảnh báo" detail={detail.error.message}/><Button variant="outline" onClick={()=>void detail.refetch()}>Thử tải lại cảnh báo</Button></div>:null}
    {detail.isSuccess&&!finding?<div className="mt-7"><StatusMessage error title="Backend không trả thông tin phát hiện"/></div>:null}
    {finding?<div className="investigation-desk">
      <div className="investigation-record">
        <section className="admin-finding-signal investigation-panel" aria-labelledby="finding-signal-heading">
          <h2 className="investigation-band" id="finding-signal-heading">Tín hiệu giám sát</h2>
          <h3 className="investigation-explanation">{finding.explanation??'Chưa có diễn giải'}</h3>
          <div className="investigation-badges mt-4 flex flex-wrap gap-2">
            <AdminState value={finding.severity}>{finding.severity?severityText[finding.severity]:"Không rõ mức độ"}</AdminState>
            <AdminState value={finding.reviewStatus}>{finding.reviewStatus?findingStatusText[finding.reviewStatus]:"Không rõ trạng thái"}</AdminState>
            <span className="investigation-advisory"><Info size={18} aria-hidden="true"/>{finding.advisoryOnly===true?'Chỉ hỗ trợ quyết định':'Trạng thái tư vấn chưa xác định'}</span>
          </div>
          <dl className="admin-finding-timing">
            <div><dt>Số lần ghi nhận</dt><dd>{countText(finding.occurrenceCount)}</dd></div>
            <div><dt>Lần đầu</dt><dd>{timeText(finding.firstSeenAt)}</dd></div>
            <div><dt>Lần gần nhất</dt><dd>{timeText(finding.lastSeenAt)}</dd></div>
            <div><dt>Mới sau đánh giá</dt><dd>{countText(finding.newOccurrencesSinceReview)}</dd></div>
          </dl>
          <h3 className="investigation-subheading mt-4">Hướng kiểm tra được đề xuất</h3>
          <p className="investigation-copy mt-1 whitespace-pre-wrap break-words">{finding.recommendedAction??'Không có đề xuất cụ thể'}</p>
          <details className="admin-review-identifiers">
            <summary>Quy tắc và định danh liên quan</summary>
            <dl className="admin-review-fields">
              {([
                ['Mã phát hiện',finding.id],['Mã quy tắc',finding.ruleId],['Phiên bản quy tắc',finding.ruleVersion],
                ['Bộ phát hiện',finding.detectorType],['Nhóm',finding.category],['Đối tượng',finding.subjectType],
                ['Mã đối tượng',finding.subjectId],['Mã chiến dịch',finding.campaignId],['Mã cột mốc',finding.milestoneId],
                ['Mã đề nghị giải ngân',finding.disbursementRequestId],
              ] as const).filter(([,value])=>value!=null&&value!=='').map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
            </dl>
          </details>
        </section>
        <section className="admin-finding-facts investigation-panel" aria-labelledby="finding-facts-heading">
          <h2 className="investigation-band" id="finding-facts-heading">Dữ kiện do Monitoring cung cấp</h2>
          {finding.facts?.length?<ol className="investigation-fact-list">{finding.facts.map((fact,index)=><li key={index} className="admin-finding-fact">
            <h3>{typeof fact.name==='string' ? (factLabels.get(fact.name) ?? 'Dữ kiện '+(index+1)) : 'Dữ kiện '+(index+1)}</h3>
            {(typeof fact.value==='string'||typeof fact.value==='number'||typeof fact.value==='boolean') && <p className="investigation-fact-value">
              {typeof fact.value==='boolean' ? (fact.value ? 'Có' : 'Không') : String(fact.value)}
            </p>}
            <details className="investigation-technical">
              <summary>Xem dữ liệu kỹ thuật đầy đủ</summary>
              <pre className="investigation-fact-data">{readableJson(fact)}</pre>
            </details>
          </li>)}</ol>:<p className="investigation-copy mt-2">Chưa có dữ kiện chi tiết.</p>}
        </section>
      <div className="investigation-history">
        <section className="admin-finding-sequence investigation-panel" aria-label="Các lần phát hiện">
          <h2 className="investigation-band">Các lần ghi nhận ({countText(finding.occurrenceCount)})</h2>
          <p className="investigation-meta mt-2">Những ghi nhận này không phải bằng chứng độc lập về sai phạm.</p>
          {detail.data?.occurrences?.length?<ol className="investigation-timeline">{detail.data.occurrences.map((x,index)=><li key={x.sourceEventId??index} className="admin-finding-occurrence">
            <span className="investigation-marker"><Activity size={17} aria-hidden="true"/></span>
            <div className="investigation-event-head">
              <h3 className="investigation-event-title"><time dateTime={x.observedAt}>{timeText(x.observedAt)}</time></h3>
              <AdminState value={x.severity}>{x.severity?severityText[x.severity]:'Mức độ chưa rõ'}</AdminState>
            </div>
            <dl className="investigation-event-facts">
              <div><dt>Sự kiện</dt><dd>{x.sourceEventId??'—'}</dd></div>
              <div><dt>Người liên quan</dt><dd>{x.actorId??'—'}</dd></div>
            </dl>
            {x.aiNote?<div className="investigation-event-note">
              <p className="investigation-meta font-bold">Ghi chú giám sát</p>
              <p className="investigation-copy whitespace-pre-wrap break-words">{x.aiNote}</p>
            </div>:null}
          </li>)}</ol>:<StatusMessage title="Chưa có lịch sử ghi nhận chi tiết"/>}
        </section>
        <section className="admin-finding-sequence investigation-panel">
          <h2 className="investigation-band">Lịch sử đánh giá</h2>
          {history.isPending?<div className="mt-4"><StatusMessage title="Đang tải lịch sử đánh giá…"/></div>:null}
          {history.isError?<div className="mt-4"><StatusMessage error title="Lịch sử không truy cập được — tạm khóa quyết định" detail={history.error.message}/><Button type="button" variant="outline" onClick={()=>void history.refetch()}>Tải lại lịch sử</Button></div>:null}
          {history.isSuccess&&!history.data.length?<p className="investigation-copy mt-4">Chưa có lần đánh giá trước đó.</p>:null}
          {history.data?.length ? <ol className="investigation-timeline">{history.data.map((x,index)=><li key={x.id??index} className="admin-finding-occurrence">
            <span className="investigation-marker investigation-review-marker"><ClipboardCheck size={17} aria-hidden="true"/></span>
            <h3 className="investigation-event-title">{historyStatus(x.fromStatus,'Ban đầu')} → {historyStatus(x.toStatus,'Chưa rõ')}</h3>
            <p className="investigation-review-meta">
              <span>Người đánh giá <strong>{x.changedBy || '—'}</strong></span>
              <time className="investigation-meta" dateTime={x.createdAt}>{timeText(x.createdAt)}</time>
            </p>
            {x.reason?<p className="investigation-copy mt-2 whitespace-pre-wrap break-words">{x.reason}</p>:null}
          </li>)}</ol>:null}
          {finding.reviewedAt?<p className="investigation-meta mt-4 break-words">Đánh giá gần nhất: {timeText(finding.reviewedAt)} · {finding.reviewedBy || '—'}</p>:null}
          {finding.reviewNote?<p className="investigation-copy mt-2 whitespace-pre-wrap break-words">{finding.reviewNote}</p>:null}
        </section>
      </div>
      </div>
      <aside className="investigation-human min-w-0">
        <section className="investigation-panel">
          <div className="investigation-human-head investigation-band"><h2>Quyết định của người kiểm tra</h2><WorkspaceIllustration kind="scale" /></div>
          <p className="investigation-copy mt-2">Backend ngăn xung đột lợi ích nếu bạn tạo hành động liên quan hoặc sở hữu chiến dịch. Không thể tự động phê duyệt, tạm dừng hoặc giải ngân.</p>
          {!choices.length?<p className="investigation-copy mt-4">Không có chuyển trạng thái đánh giá nào được phép tiếp tục.</p>:null}
          {!history.isSuccess?<p role="alert" className="investigation-meta mt-3 font-semibold">Cần tải thành công lịch sử đánh giá trước khi đưa ra quyết định.</p>:null}
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
            <label className="investigation-copy flex items-start gap-2"><input className="mt-1 accent-brand" type="checkbox" checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/> Tôi đã kiểm tra các dữ kiện và hiểu đây chỉ là đánh giá cảnh báo, không phải quyết định giải ngân.</label>
            <Button type="submit" loading={review.isPending} disabled={!selected||!confirmed||!note.trim()||!detail.isSuccess||!history.isSuccess}>Lưu đánh giá</Button>
            {review.isError?<p role="alert" className="break-words text-sm text-danger">{review.error.message}</p>:null}
          </form>:null}
          {notice?<p role="status" className="mt-4 rounded-xl bg-soft-mint p-4 text-sm text-brand-ink">{notice}</p>:null}
        </section>
        <p className="flex gap-2 rounded-xl bg-soft-sand p-4 text-xs leading-6 text-warning"><ShieldAlert size={18} className="shrink-0" aria-hidden="true"/> Cảnh báo chỉ hỗ trợ giám sát. Không suy diễn cảnh báo thành bằng chứng rằng chiến dịch vi phạm.</p>
      </aside>
    </div>:null}
    </Container>
  </div>;
}
