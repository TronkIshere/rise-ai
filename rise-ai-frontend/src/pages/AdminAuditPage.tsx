import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BookOpenText, RefreshCcw } from 'lucide-react';
import { useAuth } from '@/auth/auth-context';
import { listAuditEvents, type AuditFilters } from '@/api/monitoring';
import { Container } from '@/components/layout/Container';
import { AdminMonitoringNav } from '@/components/ui/AdminMonitoringNav';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { countText, readableJson, timeText } from '@/lib/monitoring-display';

const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function AdminAuditPage(){
  const {user}=useAuth();
  const [draft,setDraft]=useState<AuditFilters>({});
  const [filters,setFilters]=useState<AuditFilters>({});
  const [page,setPage]=useState(0);
  const [validation,setValidation]=useState('');
  const query=useQuery({queryKey:['admin',user?.id,'audit',filters,page],queryFn:()=>listAuditEvents(filters,page)});
  function submit(e:FormEvent<HTMLFormElement>){
    e.preventDefault();
    if((draft.actorId?.trim()&&!uuid.test(draft.actorId.trim()))||(draft.entityId?.trim()&&!uuid.test(draft.entityId.trim()))){
      setValidation('ID người thực hiện và ID đối tượng phải là UUID hợp lệ.');return;
    }
    setValidation('');
    setFilters(Object.fromEntries(Object.entries(draft).map(([k,v])=>[k,v?.trim()])) as AuditFilters);
    setPage(0);
  }
  return <Container className="py-10 sm:py-14">
    <p className="text-xs font-extrabold uppercase tracking-widest text-brand">FE-05 · NHẬT KÝ NGHIỆP VỤ</p>
    <h1 className="mt-3 text-3xl font-extrabold text-brand-ink sm:text-4xl">Lịch sử hoạt động đã ghi nhận</h1>
    <p className="mt-3 max-w-3xl text-sm leading-7 text-muted">Nhật ký do Backend cung cấp, để truy vết ai tác động đến đối tượng nào và khi nào. Không suy diễn trường trống thành sự kiện đã xác minh; không cho chỉnh sửa hay xóa nhật ký.</p>
    <AdminMonitoringNav/>
    <section className="mt-10" aria-label="Tra cứu nhật ký">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="flex items-center gap-2 text-xl font-bold text-brand-ink"><BookOpenText size={22} aria-hidden="true" className="text-brand"/> Tra cứu Audit Log</h2>
        <Button variant="outline" onClick={()=>void query.refetch()}><RefreshCcw size={16} aria-hidden="true"/> Làm mới</Button>
      </div>
      <form className="mt-5 grid gap-4 rounded-card border border-border bg-surface p-5 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_1fr_auto]" onSubmit={submit}>
        <Input label="Người thao tác (UUID)" id="audit-actor" placeholder="UUID" value={draft.actorId??''} onChange={e=>setDraft(f=>({...f,actorId:e.target.value}))}/>
        <Input label="Loại đối tượng" id="audit-entity-type" placeholder="Ví dụ: CAMPAIGN" value={draft.entityType??''} onChange={e=>setDraft(f=>({...f,entityType:e.target.value}))}/>
        <Input label="Đối tượng (UUID)" id="audit-entity" placeholder="UUID" value={draft.entityId??''} onChange={e=>setDraft(f=>({...f,entityId:e.target.value}))}/>
        <Input label="Hành động" id="audit-action" placeholder="Ví dụ: MONITORING_FINDING_REVIEWED" value={draft.action??''} onChange={e=>setDraft(f=>({...f,action:e.target.value}))}/>
        <div className="flex items-end"><Button type="submit">Tìm nhật ký</Button></div>
      </form>
      {validation?<p className="mt-3 text-sm text-danger" role="alert">{validation}</p>:null}
      <div className="mt-5 space-y-3">
        {query.isPending?<StatusMessage title="Đang tải nhật ký…"/>:null}
        {query.isError?<StatusMessage error title="Không truy xuất được nhật ký" detail={query.error.message}/>:null}
        {query.isSuccess&&!query.data.data?.length?<StatusMessage title="Không tìm thấy sự kiện phù hợp" detail="Có thể không có bản ghi trong khoảng dữ liệu hiện tại. Không thêm sự kiện giả."/>:null}
        {query.data?.data?.map((log,index)=><article key={log.id??index} className="min-w-0 rounded-card border border-border bg-surface p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-xs font-semibold text-muted">{timeText(log.createdAt)} · Người thao tác {log.actorUserId??'Không xác định'}</p>
              <h3 className="mt-2 break-words text-lg font-extrabold text-brand-ink">{log.action??'Hành động chưa xác định'}</h3>
              <p className="mt-2 break-all text-sm text-copy">Đối tượng: {log.entityType??'—'} · {log.entityId??'—'}</p>
            </div>
            <span className="break-all rounded-full bg-soft-mint px-3 py-2 text-xs font-semibold text-brand-ink">Correlation: {log.correlationId??'—'}</span>
          </div>
          {(log.previousState!=null||log.newState!=null)?<p className="mt-4 text-sm text-copy">Trước: <strong>{log.previousState??'—'}</strong> → Sau: <strong>{log.newState??'—'}</strong></p>:null}
          {log.metadata&&Object.keys(log.metadata).length>0?<details className="mt-4 rounded-xl border border-border p-4">
            <summary className="cursor-pointer text-sm font-bold text-brand">Xem metadata từ Backend</summary>
            <pre className="mt-3 max-w-full overflow-x-auto whitespace-pre-wrap break-all text-xs leading-6 text-copy">{readableJson(log.metadata)}</pre>
          </details>:null}
        </article>)}
      </div>
      {query.isSuccess?<><p className="mt-3 text-xs text-muted">{countText(query.data.totalElements)} bản ghi từ Backend</p><RecordPagination page={page} pages={query.data.totalPages??0} onChange={setPage}/></>:null}
    </section>
  </Container>;
}
