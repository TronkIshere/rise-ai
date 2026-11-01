import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, RefreshCcw, ScanSearch } from 'lucide-react';
import { Link } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { listFindings, listRulePrecision, type FindingFilters, type FindingReviewStatus, type FindingSeverity } from '@/api/monitoring';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { AdminMonitoringNav } from '@/components/ui/AdminMonitoringNav';
import { Input } from '@/components/ui/Input';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { findingStatusText, reviewStates, severityStates, severityText, countText, timeText, shortRef } from '@/lib/monitoring-display';

const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function AdminMonitoringPage(){
  const {user}=useAuth();
  const [draft,setDraft]=useState<FindingFilters>({status:'OPEN',severity:'',category:'',campaignId:''});
  const [filter,setFilter]=useState<FindingFilters>({status:'OPEN'});
  const [page,setPage]=useState(0);
  const [validation,setValidation]=useState('');
  const key=['admin',user?.id,'monitoring'] as const;
  const findings=useQuery({queryKey:[...key,'findings',filter,page],queryFn:()=>listFindings(filter,page)});
  const precision=useQuery({queryKey:[...key,'precision'],queryFn:listRulePrecision});
  function applyFilters(e:FormEvent<HTMLFormElement>){
    e.preventDefault();
    if(draft.campaignId?.trim()&&!uuid.test(draft.campaignId.trim())){setValidation('Mã chiến dịch phải là UUID hợp lệ.');return;}
    setValidation('');setFilter({...draft,category:draft.category?.trim()??'',campaignId:draft.campaignId?.trim()??''});setPage(0);
  }
  return <Container className="py-10 sm:py-14">
    <p className="text-xs font-extrabold uppercase tracking-widest text-brand">FE-05 · DÀNH CHO ADMIN</p>
    <h1 className="mt-3 text-3xl font-extrabold text-brand-ink sm:text-4xl">Cảnh báo giám sát thiện nguyện</h1>
    <p className="mt-3 max-w-3xl text-sm leading-7 text-muted">Các phát hiện do Monitoring gửi đến Backend để Admin kiểm tra. Mỗi cảnh báo chỉ là tín hiệu tham khảo, không phải kết luận hành vi sai phạm và không tự dừng chiến dịch hay giải ngân.</p>
    <AdminMonitoringNav/>
    <section className="mt-10" aria-labelledby="finding-list">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="finding-list" className="flex items-center gap-2 text-xl font-bold text-brand-ink"><ScanSearch aria-hidden="true" size={22} className="text-brand"/> Các phát hiện cần xem xét</h2>
        <Button variant="outline" onClick={()=>void findings.refetch()}><RefreshCcw size={16} aria-hidden="true"/> Làm mới</Button>
      </div>
      <form onSubmit={applyFilters} className="mt-5 grid gap-4 rounded-card border border-border bg-surface p-5 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_1.5fr_auto]">
        <div><label htmlFor="finding-status" className="mb-2 block text-sm font-semibold">Đánh giá</label>
          <select id="finding-status" className="min-h-11 w-full rounded-xl border border-border bg-surface px-3 text-sm" value={draft.status??''} onChange={e=>setDraft(d=>({...d,status:e.target.value as FindingReviewStatus|''}))}>
            <option value="">Tất cả</option>{reviewStates.map(s=><option key={s} value={s}>{findingStatusText[s]}</option>)}
          </select></div>
        <div><label htmlFor="finding-severity" className="mb-2 block text-sm font-semibold">Mức độ</label>
          <select id="finding-severity" className="min-h-11 w-full rounded-xl border border-border bg-surface px-3 text-sm" value={draft.severity??''} onChange={e=>setDraft(d=>({...d,severity:e.target.value as FindingSeverity|''}))}>
            <option value="">Tất cả</option>{severityStates.map(s=><option key={s} value={s}>{severityText[s]}</option>)}
          </select></div>
        <Input id="finding-category" label="Nhóm cảnh báo" placeholder="Không bắt buộc" value={draft.category??''} onChange={e=>setDraft(d=>({...d,category:e.target.value}))}/>
        <Input id="finding-campaign" label="UUID chiến dịch" placeholder="Lọc theo chiến dịch" value={draft.campaignId??''} onChange={e=>setDraft(d=>({...d,campaignId:e.target.value}))}/>
        <div className="flex items-end"><Button type="submit">Áp dụng</Button></div>
      </form>
      {validation?<p role="alert" className="mt-3 text-sm text-danger">{validation}</p>:null}
      <div className="mt-5 space-y-3">
        {findings.isPending?<StatusMessage title="Đang tải các phát hiện…"/>:null}
        {findings.isError?<StatusMessage error title="Không thể tải phát hiện" detail={findings.error.message}/>:null}
        {findings.isSuccess&&!findings.data.data?.length?<StatusMessage title="Không có phát hiện phù hợp" detail="Thử bộ lọc khác hoặc kiểm tra luồng sự kiện. Không có cảnh báo giả được tạo trên Frontend."/>:null}
        {findings.data?.data?.map((item,index)=><article key={item.id??index} className="min-w-0 rounded-card border border-border bg-surface p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0 flex-1">
              <p className="break-words text-xs font-semibold text-brand">{item.category??'Chưa phân loại'} · {item.ruleId??'Chưa có rule'} · {shortRef(item.id)}</p>
              <h3 className="mt-2 break-words text-lg font-extrabold text-brand-ink">{item.explanation??'Chưa có mô tả phát hiện'}</h3>
              <p className="mt-3 text-xs text-muted">Chiến dịch {shortRef(item.campaignId)} · {timeText(item.lastSeenAt)} · {countText(item.occurrenceCount)} lần ghi nhận</p>
            </div>
            <div className="flex flex-wrap gap-2 text-xs font-bold">
              <span className="rounded-full bg-soft-sand px-3 py-2 text-warning">{item.severity?severityText[item.severity]:'Chưa xác định mức độ'}</span>
              <span className="rounded-full bg-soft-mint px-3 py-2 text-brand-ink">{item.reviewStatus?findingStatusText[item.reviewStatus]:'Chưa xác định trạng thái'}</span>
            </div>
          </div>
          {item.newOccurrencesSinceReview? <p className="mt-3 text-xs font-semibold text-warning">{item.newOccurrencesSinceReview} lần xuất hiện mới từ lần đánh giá trước</p>:null}
          {item.id?<Link to={'/admin/monitoring/findings/'+encodeURIComponent(item.id)} className="mt-4 inline-flex min-h-11 items-center gap-2 text-sm font-bold text-brand hover:underline">Xem giải thích, bằng chứng và lịch sử <ArrowRight size={17} aria-hidden="true"/></Link>:null}
        </article>)}
      </div>
      {findings.isSuccess?<><p className="mt-3 text-xs text-muted">Tổng số phù hợp: {countText(findings.data.totalElements)}</p><RecordPagination page={page} pages={findings.data.totalPages??0} onChange={setPage}/></>:null}
    </section>
    <section className="mt-12" aria-labelledby="rule-precision">
      <h2 id="rule-precision" className="text-xl font-bold text-brand-ink">Phân loại theo quy tắc đã được Admin đánh giá</h2>
      <p className="mt-2 text-xs leading-6 text-muted">Tỷ lệ dưới đây chỉ dựa trên các cảnh báo đã có kết luận xác nhận/bác bỏ; không phải xác suất AI đúng cho mọi trường hợp.</p>
      {precision.isPending?<div className="mt-5"><StatusMessage title="Đang tải thống kê quy tắc…"/></div>:null}
      {precision.isError?<div className="mt-5"><StatusMessage error title="Không tải được thống kê rule" detail={precision.error.message}/></div>:null}
      {precision.isSuccess&&!precision.data.length?<div className="mt-5"><StatusMessage title="Chưa có quy tắc được thống kê"/></div>:null}
      <div className="mt-5 grid gap-3 sm:grid-cols-2">{precision.data?.map((rule,index)=><article key={rule.ruleId??index} className="min-w-0 rounded-card border border-border bg-surface p-5">
        <h3 className="break-all text-sm font-bold text-brand-ink">{rule.ruleId??'Rule chưa xác định'}</h3>
        <p className="mt-2 text-sm">Xác nhận: {countText(rule.concernConfirmed)} · Không đủ căn cứ: {countText(rule.dismissed)}</p>
        <p className="mt-2 text-sm font-semibold text-brand">{typeof rule.precision==='number'?(rule.precision*100).toFixed(1)+'% trên các ca đã kết luận':'Chưa đủ dữ liệu kết luận'}</p>
        <p className="mt-2 text-xs text-muted">Tổng {countText(rule.total)} · Đang mở {countText(rule.open)} · Đã ghi nhận {countText(rule.acknowledged)}</p>
      </article>)}</div>
    </section>
  </Container>;
}
