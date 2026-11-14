import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, ArrowRight, ArrowRightToLine, BookOpenText, Box, ChevronRight, ClipboardList, Clock3, CodeXml, FileText, History, ListChecks, LockKeyhole, RefreshCcw, ScanSearch, ShieldCheck, UserRound, Users } from 'lucide-react';
import { useAuth } from '@/auth/auth-context';
import { listAuditEvents, type AuditEvent, type AuditFilters } from '@/api/monitoring';
import { Container } from '@/components/layout/Container';
import { AdminMonitoringNav } from '@/components/ui/AdminMonitoringNav';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { countText, readableJson, shortRef, timeText } from '@/lib/monitoring-display';

const auditActions: Record<string,string> = { MONITORING_FINDING_REVIEWED:'Đánh giá cảnh báo giám sát', CAMPAIGN_APPROVED:'Phê duyệt chiến dịch', RECONCILIATION_RUN_CREATED:'Tạo lượt đối soát' };
const auditObjects: Record<string,string> = { CAMPAIGN:'Chiến dịch', MONITORING_FINDING:'Cảnh báo giám sát', RECONCILIATION_RUN:'Lượt đối soát' };
const auditStates: Record<string,string> = { OPEN:'Chưa đánh giá', ACKNOWLEDGED:'Đã ghi nhận', PENDING_REVIEW:'Chờ xét duyệt', APPROVED:'Đã phê duyệt' };
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const auditMarkers = new Map([
  ['MONITORING_FINDING_REVIEWED', ScanSearch],
  ['CAMPAIGN_APPROVED', ShieldCheck],
  ['RECONCILIATION_RUN_CREATED', ListChecks],
]);

const auditMarkerTones = new Map([
  ['MONITORING_FINDING_REVIEWED', 'cyan'],
  ['CAMPAIGN_APPROVED', 'amber'],
  ['RECONCILIATION_RUN_CREATED', 'mint'],
]);
const auditOutcomeTones = new Map([
  ['OPEN', 'attention'], ['PENDING_REVIEW', 'attention'],
  ['ACKNOWLEDGED', 'received'], ['APPROVED', 'approved'],
  ['REJECTED', 'rejected'], ['FAILED', 'rejected'],
]);

function AuditRef({value}:{value:string}){
  return <code className="journal-ref">{shortRef(value)}{value.length>12?'…':''}</code>;
}

function AuditEntry({log}:{log:AuditEvent}){
  const Marker=auditMarkers.get(log.action??'')??BookOpenText;
  const reason=log.metadata?.reason;
  const outcome=auditOutcomeTones.get(log.newState??'');
  const OutcomeIcon=outcome==='approved'?ShieldCheck:outcome==='received'?ClipboardList:outcome==='rejected'?AlertTriangle:outcome==='attention'?Clock3:ArrowRightToLine;
  return <li className="admin-journal-entry">
    <div className="journal-closed-record">
      <span className="journal-closed-icon" data-tone={auditMarkerTones.get(log.action??'')}><Marker size={26} aria-hidden="true"/></span>
      <div className="journal-record-main">
        <h3>{auditActions[log.action??''] ?? (log.action || 'Hành động chưa xác định')}</h3>
        <dl className="journal-record-meta">
          <div className="journal-record-object">
            <dt><span className="journal-field-icon"><Box size={17} aria-hidden="true"/></span>Đối tượng</dt>
            <dd>
              <span>{auditObjects[log.entityType??''] ?? log.entityType ?? '—'}</span>
              {log.entityId && <span className="journal-record-id">Mã đối tượng <AuditRef value={log.entityId}/></span>}
            </dd>
          </div>
          <div className="journal-record-time">
            <dt><span className="journal-field-icon"><Clock3 size={17} aria-hidden="true"/></span>Thời điểm ghi nhận</dt>
            <dd>{log.createdAt ? <time dateTime={log.createdAt}>{timeText(log.createdAt)}</time> : timeText()}</dd>
          </div>
          <div className="journal-record-actor">
            <dt><span className="journal-field-icon"><UserRound size={17} aria-hidden="true"/></span>Người thao tác</dt>
            <dd>{log.actorUserId ? <AuditRef value={log.actorUserId}/> : '—'}</dd>
          </div>
        </dl>
      </div>
      {(log.previousState!=null||log.newState!=null) && <dl className="journal-record-change">
        <div><dt><History size={18} aria-hidden="true"/>Trước</dt><dd>{auditStates[log.previousState??''] ?? log.previousState ?? '—'}</dd></div>
        <ArrowRight size={24} aria-hidden="true"/>
        <div data-outcome={outcome}><dt><OutcomeIcon size={18} aria-hidden="true"/>Sau</dt><dd>{auditStates[log.newState??''] ?? log.newState ?? '—'}</dd></div>
      </dl>}
    </div>
    <span className="journal-event-mark"><Marker size={21} aria-hidden="true"/></span>
    <div className="admin-journal-time">
      <span>Thời điểm ghi nhận</span>
      <strong>{log.createdAt ? <time dateTime={log.createdAt}>{timeText(log.createdAt)}</time> : timeText()}</strong>
    </div>
    <div className="admin-journal-action">
      <p className="journal-actor"><strong>Người thao tác</strong> <span>{log.actorUserId??'—'}</span></p>
      <h3>{auditActions[log.action??''] ?? (log.action || 'Hành động chưa xác định')}</h3>
      <p className="journal-object"><strong>Đối tượng</strong> {auditObjects[log.entityType??''] ?? log.entityType ?? '—'}
        {log.entityId && <span className="journal-object-id">{log.entityId}</span>}
      </p>
    </div>
    {(log.previousState!=null||log.newState!=null) && <dl className="admin-journal-transition">
      <div><dt>Trước</dt><dd>{auditStates[log.previousState??''] ?? log.previousState ?? '—'}</dd></div>
      <ArrowRight size={19} aria-hidden="true"/>
      <div><dt>Sau</dt><dd>{auditStates[log.newState??''] ?? log.newState ?? '—'}</dd></div>
    </dl>}
    <details className="admin-journal-details">
      <summary><FileText className="journal-closed-file" size={19} aria-hidden="true"/>Dấu vết &amp; định danh<ChevronRight className="journal-closed-chevron" size={17} aria-hidden="true"/></summary>
      <div className="journal-dossier">
        {typeof reason==='string' && reason.trim() && <div className="journal-dossier-reason">
          <span className="journal-reason-icon"><ClipboardList size={23} aria-hidden="true"/></span>
          <div>
            <h4>Lý do ghi nhận</h4>
            <p>{reason}</p>
          </div>
        </div>}
        <div className="journal-dossier-groups">
          <div className="journal-dossier-panel">
            <h4><FileText size={22} aria-hidden="true"/> Bản ghi và ngữ cảnh</h4>
            <dl>
              <div><dt>Mã bản ghi</dt><dd><code>{log.id??'—'}</code></dd></div>
              <div><dt>Mã hành động</dt><dd><code>{log.action??'—'}</code></dd></div>
              <div><dt>Mã liên kết (correlation)</dt><dd><code>{log.correlationId??'—'}</code></dd></div>
              {(log.previousState!=null||log.newState!=null) && <div>
                <dt>Trạng thái gốc</dt><dd><code>{log.previousState??'—'} → {log.newState??'—'}</code></dd>
              </div>}
            </dl>
          </div>
          <div className="journal-dossier-panel journal-dossier-actor">
            <h4><Users size={22} aria-hidden="true"/> Người thao tác và đối tượng</h4>
            <dl>
              <div><dt>Mã người thao tác</dt><dd><code>{log.actorUserId??'—'}</code></dd></div>
              <div><dt>Loại đối tượng gốc</dt><dd>{log.entityType??'—'}</dd></div>
              <div><dt>Mã đối tượng</dt><dd><code>{log.entityId??'—'}</code></dd></div>
              {log.ipAddress!=null && <div><dt>Địa chỉ IP</dt><dd><code>{log.ipAddress}</code></dd></div>}
            </dl>
          </div>
        </div>
        {log.metadata!=null && <details className="journal-raw">
          <summary>
            <CodeXml className="journal-raw-icon" size={22} aria-hidden="true"/>
            Metadata gốc (JSON)
            <ChevronRight className="journal-raw-caret" size={20} aria-hidden="true"/>
          </summary>
          <pre tabIndex={0}>{readableJson(log.metadata)}</pre>
        </details>}
      </div>
    </details>
  </li>;
}

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
  return <div className="rise-deep-page deep-admin-audit admin-ops">
    <div className="rise-deep-topline" aria-hidden="true">
      <span>ADMIN / TRUY VẾT</span><span>09 · NHẬT KÝ HỆ THỐNG</span>
    </div>
    <Container className="deep-inner py-10 sm:py-14">
    <header className="journal-masthead">
      <div className="journal-masthead-copy">
        <p className="journal-kicker"><LockKeyhole size={16} aria-hidden="true"/> Nhật ký chỉ đọc</p>
        <h1>Lịch sử hoạt động <span>đã ghi nhận</span></h1>
        <p className="journal-intro">Tra cứu thời điểm, người thao tác, đối tượng và những thay đổi được hệ thống ghi nhận.</p>
      </div>
      <svg className="journal-emblem" viewBox="0 0 344 180" aria-hidden="true" focusable="false">
        <ellipse cx="191" cy="152" rx="127" ry="14" fill="var(--journal-art-shadow)" opacity=".53"/>
        <g transform="rotate(-8 176 89)" stroke="var(--journal-art-ink)" strokeWidth="3" strokeLinejoin="round">
          <path d="M40 47 148 30 183 45 279 30 303 48 299 131 192 154 163 145 59 157 40 140Z" fill="var(--journal-art-cover)"/>
          <path d="M44 42 145 24Q165 24 183 39 236 16 284 23L296 34 291 126Q233 123 190 148 174 137 159 138L53 150Z" fill="var(--journal-art-edge)"/>
          <path d="M42 35Q110 16 147 19 165 19 183 35 230 10 287 18L290 117Q237 112 189 137 174 124 156 125L54 141Z" fill="var(--journal-art-page)"/>
          <path d="M183 35Q230 10 287 18L290 117Q237 112 189 137Z" fill="var(--journal-art-fold)"/>
          <path d="M77 66 131 55M79 83 151 68M81 100 148 86M83 117 130 107" fill="none" stroke="var(--journal-art-rules)" strokeLinecap="round"/>
          <path d="M219 51 258 40M219 70 267 57M220 89 259 78" fill="none" stroke="var(--journal-art-rules)" strokeLinecap="round"/>
          <path d="M183 35 189 137" fill="none"/>
          <path d="m244 19 17-3 2 39-9-5-8 9Z" fill="var(--journal-art-bookmark)"/>
          <circle cx="211" cy="53" r="3" fill="var(--journal-art-bullet)" stroke="none"/>
          <circle cx="212" cy="72" r="3" fill="var(--journal-art-bullet)" stroke="none"/>
          <circle cx="213" cy="91" r="3" fill="var(--journal-art-bullet)" stroke="none"/>
        </g>
        <g transform="rotate(-28 290 115)" stroke="var(--journal-art-ink)" strokeWidth="3" strokeLinejoin="round">
          <path d="M276 88h17v62l-8 15-9-15Z" fill="var(--journal-art-pen)"/>
          <path d="M276 89v-7q0-6 6-6h5q6 0 6 6v7Z" fill="var(--journal-art-cap)"/>
          <path d="m276 150 9 15 8-15Z" fill="var(--journal-art-tip)"/>
          <path d="M284 98v46" stroke="var(--journal-art-tip)" strokeLinecap="round"/>
        </g>
      </svg>
    </header>
    <AdminMonitoringNav/>
    <section className="journal-desk" aria-labelledby="audit-search-heading">
      <div className="journal-search">
        <header className="journal-search-head">
          <h2 id="audit-search-heading"><BookOpenText size={21} aria-hidden="true"/> Tra cứu nhật ký</h2>
          <Button variant="outline" onClick={()=>void query.refetch()}><RefreshCcw size={16} aria-hidden="true"/> Làm mới</Button>
        </header>
      <form className="journal-filters grid gap-4 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_1fr_auto]" onSubmit={submit}>
        <Input label="Người thao tác (UUID)" id="audit-actor" placeholder="UUID" value={draft.actorId??''} onChange={e=>setDraft(f=>({...f,actorId:e.target.value}))}/>
        <Input label="Loại đối tượng" id="audit-entity-type" placeholder="Ví dụ: CAMPAIGN" value={draft.entityType??''} onChange={e=>setDraft(f=>({...f,entityType:e.target.value}))}/>
        <Input label="Đối tượng (UUID)" id="audit-entity" placeholder="UUID" value={draft.entityId??''} onChange={e=>setDraft(f=>({...f,entityId:e.target.value}))}/>
        <Input label="Hành động" id="audit-action" placeholder="Mã hành động" value={draft.action??''} onChange={e=>setDraft(f=>({...f,action:e.target.value}))}/>
        <div className="flex items-end"><Button type="submit">Tìm nhật ký</Button></div>
      </form>
      {validation?<p className="mt-3 text-sm text-danger" role="alert">{validation}</p>:null}
        </div>
      <div className="journal-stream">
        {query.isPending?<StatusMessage title="Đang tải nhật ký…"/>:null}
        {query.isError?<StatusMessage error title="Không truy xuất được nhật ký" detail={query.error.message}/>:null}
        {query.isSuccess&&!query.data.data?.length?<StatusMessage title="Không tìm thấy sự kiện phù hợp" detail="Thử điều chỉnh bộ lọc để mở rộng kết quả."/>:null}
        {query.data?.data?.length ? <ol className="journal-timeline" aria-label="Dòng thời gian nhật ký">
          {query.data.data.map((log,index)=><AuditEntry key={log.id??index} log={log}/>)}
        </ol>:null}
      </div>
      {query.isSuccess?<><p className="journal-result-count">{countText(query.data.totalElements)} bản ghi từ Backend</p><RecordPagination page={page} pages={query.data.totalPages??0} onChange={setPage}/></>:null}
    </section>
    </Container>
  </div>;
}
