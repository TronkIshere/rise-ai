import { AdminMasthead, AdminState } from '@/components/ui/AdminOperations';
import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, RefreshCcw, ScanSearch } from 'lucide-react';
import { Link } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { listFindings, listRulePrecision, type FindingFilters, type FindingReviewStatus, type FindingSeverity, type RulePrecision } from '@/api/monitoring';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { AdminMonitoringNav } from '@/components/ui/AdminMonitoringNav';
import { Input } from '@/components/ui/Input';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { findingStatusText, reviewStates, severityStates, severityText, countText, timeText, shortRef } from '@/lib/monitoring-display';
import { assessRulePrecision, ruleCount } from '@/lib/rule-precision-display';

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
  return <div className="rise-deep-page deep-admin-monitoring admin-ops">
    <div className="rise-deep-topline" aria-hidden="true">
      <span>ADMIN / GIÁM SÁT</span><span>06 · PHÁT HIỆN CẦN XEM XÉT</span>
    </div>
    <Container className="deep-inner py-10 sm:py-14">
    <AdminMasthead title="Tín hiệu cần kiểm tra" eyebrow="Giám sát thiện nguyện" art="alert">
      <p>Theo dõi mức độ và trạng thái đánh giá. Cảnh báo chỉ là tín hiệu tham khảo cho người kiểm tra.</p>
    </AdminMasthead>
    <AdminMonitoringNav/>
    <div className="admin-risk-board u6-findings-board">
    <section className="admin-risk-list" aria-labelledby="finding-list">
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
        {findings.data?.data?.map((item,index)=><article key={item.id??index} className="admin-risk-row" data-severity={item.severity}>
          <div className="monitoring-finding-state">
            <AdminState value={item.severity}>{item.severity?severityText[item.severity]:'Chưa xác định mức độ'}</AdminState>
            <AdminState value={item.reviewStatus}>{item.reviewStatus?findingStatusText[item.reviewStatus]:'Chưa xác định trạng thái'}</AdminState>
          </div>
          <h3 className="monitoring-finding-title">{item.explanation??'Chưa có mô tả phát hiện'}</h3>
          {item.recommendedAction && <div className="monitoring-finding-guidance">
            <p className="monitoring-finding-guidance-label">Hướng kiểm tra đề xuất</p>
            <p className="monitoring-finding-copy">{item.recommendedAction}</p>
          </div>}
          <div className="monitoring-finding-meta">
            <dl className="monitoring-finding-related">
              <div><dt>Chiến dịch</dt><dd>{shortRef(item.campaignId)}</dd></div>
              <div><dt>Ghi nhận gần nhất</dt><dd>{timeText(item.lastSeenAt)}</dd></div>
              <div><dt>Số lần ghi nhận</dt><dd>{countText(item.occurrenceCount)}</dd></div>
            </dl>
            {item.newOccurrencesSinceReview ? <p className="monitoring-finding-new">{item.newOccurrencesSinceReview} lần xuất hiện mới từ lần đánh giá trước</p>:null}
            <details>
              <summary>Quy tắc và định danh tín hiệu</summary>
              <p>{item.category??'Chưa phân loại'} · {item.ruleId??'Chưa có quy tắc'} · {item.id??'—'}</p>
              {item.campaignId && <p>Mã chiến dịch: {item.campaignId}</p>}
            </details>
          </div>
          {item.id && <div className="monitoring-finding-action">
            <Link className="monitoring-finding-link" to={'/admin/monitoring/findings/'+encodeURIComponent(item.id)}>Mở hồ sơ <ArrowRight size={18} aria-hidden="true"/></Link>
          </div>}
        </article>)}
      </div>
      {findings.isSuccess?<><p className="mt-3 text-xs text-muted">Tổng số phù hợp: {countText(findings.data.totalElements)}</p><RecordPagination page={page} pages={findings.data.totalPages??0} onChange={setPage}/></>:null}
    </section>
    </div>
    <section className="u6-rule-card" aria-labelledby="rule-precision">
      <header className="u6-cardhead"><h2 id="rule-precision">Hiệu quả quy tắc</h2><span className="u6-pill">{precision.isSuccess?`${precision.data.length} quy tắc`:precision.isPending?'Đang tải':'Không khả dụng'}</span></header>
      <div className="u6-rule-body">
        <p className="u6-intro">Tỷ lệ xác nhận được tính trên các phát hiện đã có kết luận của Admin; không phải xác suất AI đúng trên mọi cảnh báo.</p>
        {precision.isPending?<StatusMessage title="Đang tải thống kê quy tắc…"/>:null}
        {precision.isError?<StatusMessage error title="Không tải được thống kê quy tắc" detail={precision.error.message}/>:null}
        {precision.isSuccess&&!precision.data.length?<StatusMessage title="Chưa có quy tắc được thống kê"/>:null}
        {precision.isSuccess?<div className="u6-rows">{precision.data.map((rule,index)=><RulePrecisionRow key={rule.ruleId??index} rule={rule}/>)}</div>:null}
        <p className="u6-note">Tỷ lệ = ca xác nhận quan ngại ÷ (ca xác nhận quan ngại + ca không đủ căn cứ). Các ca “Chưa đánh giá” không thuộc mẫu số. Trạng thái “Đã ghi nhận” được theo dõi riêng.</p>
      </div>
    </section>
    </Container>
  </div>;
}

function RulePrecisionRow({rule}:{rule:RulePrecision}){
  const result=assessRulePrecision(rule);
  return <article className="u6-rule-row" data-precision-state={result.state}>
    <div className="u6-module"><span className="u6-label">Quy tắc</span><strong className="u6-rule-name">{rule.ruleId||'Quy tắc chưa xác định'}</strong><span className="u6-detail">Tổng {ruleCount(rule.total)} phát hiện</span></div>
    <div className="u6-module u6-rate-module"><span className="u6-label">Tỷ lệ xác nhận</span><strong className="u6-rate-value">{result.precisionText}</strong><span className="u6-detail">{result.state==='valid'?`${ruleCount(rule.concernConfirmed)} / ${ruleCount(result.concluded)} ca kết luận`:result.state==='no-conclusion'?'Chưa có ca kết luận':'Cần xác minh dữ liệu'}</span>{result.issue?<span className="u6-no-data" role="alert">{result.issue}</span>:null}</div>
    <div className="u6-module u6-outcomes"><span className="u6-label">Kết quả đã kết luận</span><div className="u6-outcome"><span className="u6-num">{ruleCount(rule.concernConfirmed)}</span><span className="u6-text">Xác nhận quan ngại</span></div><div className="u6-outcome"><span className="u6-num">{ruleCount(rule.dismissed)}</span><span className="u6-text">Không đủ căn cứ</span></div></div>
    <div className="u6-module u6-pending"><span className="u6-label">Chưa đánh giá</span><strong className="u6-count">{ruleCount(rule.open)}</strong><span className="u6-desc">Phát hiện đang mở</span><span className="u6-desc">Đã ghi nhận: {ruleCount(rule.acknowledged)}</span></div>
  </article>;
}
