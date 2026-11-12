import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Activity, RefreshCcw, Send } from 'lucide-react';
import { useAuth } from '@/auth/auth-context';
import { listAlerts, listOutbox, getAlertStats, getOutboxStats, requeueAlert, requeueOutbox, type AlertEvent, type AlertStatus, type OutboxEvent, type OutboxStatus } from '@/api/monitoring';
import { Container } from '@/components/layout/Container';
import { AdminMonitoringNav } from '@/components/ui/AdminMonitoringNav';
import { Button } from '@/components/ui/Button';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { countText, readableJson, timeText, shortRef } from '@/lib/monitoring-display';

const outboxStatuses:OutboxStatus[]=['PENDING','IN_FLIGHT','PUBLISHED','FAILED'];
const alertStatuses:AlertStatus[]=['PENDING','RETRYING','SENT','FAILED'];

function RetryForm({id,type,onDone}:{id:string;type:'outbox'|'alert';onDone:()=>void}){
  const [expanded,setExpanded]=useState(false);
  const [reason,setReason]=useState('');
  const [confirm,setConfirm]=useState(false);
  const mutation=useMutation({
    mutationFn:async()=>{ if(type==='outbox') await requeueOutbox(id,reason.trim()); else await requeueAlert(id,reason.trim()); },
    onSuccess(){setExpanded(false);setReason('');setConfirm(false);onDone();},
  });
  async function submit(e:FormEvent<HTMLFormElement>){
    e.preventDefault();if(!reason.trim()||!confirm||mutation.isPending)return;
    try{await mutation.mutateAsync();}catch{/* Surface exact Backend error. */}
  }
  return <div className="mt-3">
    <Button variant="outline" type="button" onClick={()=>setExpanded(v=>!v)}><Send size={16} aria-hidden="true"/> {type==='outbox'?'Đưa lại vào hàng chờ':'Xếp lại lịch gửi'}</Button>
    {expanded?<form className="mt-3 max-w-xl space-y-3 rounded-xl bg-soft-sand p-4" onSubmit={e=>void submit(e)}>
      <p className="text-xs leading-6 text-warning">Chỉ áp dụng cho trạng thái FAILED. Đưa lại vào hàng chờ không có nghĩa gửi thành công. Backend quyết định trạng thái và lưu dấu vết.</p>
      <label htmlFor={'retry-'+type+'-'+id} className="block text-sm font-semibold">Lý do thử lại (bắt buộc)</label>
      <textarea id={'retry-'+type+'-'+id} maxLength={2000} required rows={3} value={reason} onChange={e=>setReason(e.target.value)} className="w-full rounded-xl border border-border bg-surface p-3 text-sm"/>
      <label className="flex gap-2 text-sm text-copy"><input type="checkbox" checked={confirm} onChange={e=>setConfirm(e.target.checked)} className="mt-1 accent-brand"/> Tôi đã kiểm tra lỗi trước khi thử lại.</label>
      {mutation.isError?<p role="alert" className="text-sm text-danger">{mutation.error.message}</p>:null}
      <Button type="submit" loading={mutation.isPending} disabled={!confirm||!reason.trim()}>Xác nhận xếp lại hàng chờ</Button>
    </form>:null}
  </div>;
}

function OutboxCard({item,onDone}:{item:OutboxEvent;onDone:()=>void}){
  return <article className="min-w-0 rounded-card border border-border bg-surface p-5">
    <div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0">
      <p className="text-xs text-muted">{timeText(item.occurredAt)} · Event {shortRef(item.eventId)}</p>
      <h3 className="mt-2 break-all text-sm font-bold text-brand-ink">{item.eventType??'Sự kiện chưa rõ'}</h3>
      <p className="mt-2 break-all text-xs text-muted">{item.aggregateType??'—'} · {item.aggregateId??'—'}</p>
    </div><span className="rounded-full bg-soft-sand px-3 py-2 text-xs font-semibold text-warning">{item.status??'—'}</span></div>
    <p className="mt-3 text-xs text-muted">Số lần thử: {countText(item.attempts)} · Lịch thử lại: {timeText(item.nextAttemptAt)} · Đã xuất bản: {timeText(item.publishedAt)}</p>
    {item.lastError?<p className="mt-3 break-words whitespace-pre-wrap rounded-xl bg-soft-sand p-3 text-xs text-warning">Lỗi Backend: {item.lastError}</p>:null}
    {item.status==='FAILED'&&item.id?<RetryForm type="outbox" id={item.id} onDone={onDone}/>:null}
  </article>;
}
function AlertCard({item,onDone}:{item:AlertEvent;onDone:()=>void}){
  return <article className="min-w-0 rounded-card border border-border bg-surface p-5">
    <div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0">
      <p className="text-xs text-muted">{timeText(item.createdAt)} · Alert {shortRef(item.id)}</p>
      <h3 className="mt-2 break-all text-sm font-bold text-brand-ink">{item.kind??'Thông báo chưa rõ'}</h3>
    </div><span className="rounded-full bg-soft-sand px-3 py-2 text-xs font-semibold text-warning">{item.status??'—'}</span></div>
    <p className="mt-3 text-xs text-muted">Số lần thử: {countText(item.attempts)} · Lịch thử lại: {timeText(item.nextAttemptAt)} · Đã gửi: {timeText(item.deliveredAt)}</p>
    {item.details&&Object.keys(item.details).length>0?<details className="mt-3 rounded-xl border border-border p-3"><summary className="cursor-pointer text-sm font-semibold text-brand">Chi tiết thông báo</summary>
      <pre className="mt-2 max-w-full overflow-x-auto whitespace-pre-wrap break-all text-xs leading-6">{readableJson(item.details)}</pre>
    </details>:null}
    {item.lastError?<p className="mt-3 break-words whitespace-pre-wrap rounded-xl bg-soft-sand p-3 text-xs text-warning">Lỗi gửi: {item.lastError}</p>:null}
    {item.status==='FAILED'&&item.id?<RetryForm type="alert" id={item.id} onDone={onDone}/>:null}
  </article>;
}

export function AdminDeliveryPage(){
  const {user}=useAuth();
  const client=useQueryClient();
  const key=['admin',user?.id,'delivery'] as const;
  const [outboxFilter,setOutboxFilter]=useState<OutboxStatus|''>('FAILED');
  const [alertFilter,setAlertFilter]=useState<AlertStatus|''>('FAILED');
  const [outboxPage,setOutboxPage]=useState(0);
  const [alertPage,setAlertPage]=useState(0);
  const outboxStats=useQuery({queryKey:[...key,'outbox-stats'],queryFn:getOutboxStats});
  const alertStats=useQuery({queryKey:[...key,'alert-stats'],queryFn:getAlertStats});
  const outbox=useQuery({queryKey:[...key,'outbox',outboxFilter,outboxPage],queryFn:()=>listOutbox(outboxFilter,outboxPage)});
  const alerts=useQuery({queryKey:[...key,'alerts',alertFilter,alertPage],queryFn:()=>listAlerts(alertFilter,alertPage)});
  const refresh=()=>void client.invalidateQueries({queryKey:key});
  return <div className="rise-deep-page deep-admin-delivery">
    <div className="rise-deep-topline" aria-hidden="true">
      <span>ADMIN / DÒNG SỰ KIỆN</span><span>08 · GIAO NHẬN CẢNH BÁO</span>
    </div>
    <Container className="deep-inner py-10 sm:py-14">
    <p className="text-xs font-extrabold uppercase tracking-widest text-brand">FE-05 · HỆ THỐNG GIÁM SÁT</p>
    <h1 className="mt-3 text-3xl font-extrabold text-brand-ink sm:text-4xl">Đường đi sự kiện và cảnh báo</h1>
    <p className="mt-3 max-w-3xl text-sm leading-7 text-muted">Theo dõi sự kiện từ Backend đến Monitoring và trạng thái gửi thông báo. Trạng thái hàng chờ không đảm bảo cảnh báo đã đến người nhận; nút thử lại chỉ đặt lại lịch.</p>
    <AdminMonitoringNav/>
    <section className="mt-10" aria-label="Sức khỏe luồng sự kiện">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="flex items-center gap-2 text-xl font-bold text-brand-ink"><Activity size={22} aria-hidden="true" className="text-brand"/> Tình trạng chuyển sự kiện</h2>
        <Button variant="outline" onClick={refresh}><RefreshCcw size={16} aria-hidden="true"/> Làm mới</Button></div>
      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <div className="min-w-0 rounded-card border border-border bg-surface p-5">
          <h3 className="font-bold text-brand-ink">Backend → Monitoring</h3>
          {outboxStats.isPending?<p className="mt-3 text-sm text-muted">Đang tải…</p>:null}
          {outboxStats.isError?<p role="alert" className="mt-3 text-sm text-danger">{outboxStats.error.message}</p>:null}
          {outboxStats.data?<><p className="mt-3 text-sm font-semibold">{outboxStats.data.relayEnabled?'Đã bật relay':'Relay đang tắt'}</p>
            <p className="mt-2 text-xs text-muted">Chờ {countText(outboxStats.data.pending)} · Đang xử lý {countText(outboxStats.data.inFlight)} · Đã xuất bản {countText(outboxStats.data.published)} · Lỗi {countText(outboxStats.data.failed)}</p>
            <p className="mt-2 text-xs text-muted">Sự kiện chưa giao lâu nhất: {countText(outboxStats.data.oldestUndeliveredAgeSeconds)} giây</p>
          </>:null}
        </div>
        <div className="min-w-0 rounded-card border border-border bg-surface p-5">
          <h3 className="font-bold text-brand-ink">Thông báo ra ngoài</h3>
          {alertStats.isPending?<p className="mt-3 text-sm text-muted">Đang tải…</p>:null}
          {alertStats.isError?<p role="alert" className="mt-3 text-sm text-danger">{alertStats.error.message}</p>:null}
          {alertStats.data?<><p className="mt-3 text-sm font-semibold">{alertStats.data.webhookConfigured?'Webhook đã cấu hình':'Webhook chưa cấu hình'}</p>
            <p className="mt-2 text-xs text-muted">Chờ {countText(alertStats.data.pending)} · Thử lại {countText(alertStats.data.retrying)} · Đã gửi {countText(alertStats.data.sent)} · Lỗi {countText(alertStats.data.failed)}</p>
            <p className="mt-2 text-xs text-muted">Thông báo chưa gửi lâu nhất: {countText(alertStats.data.oldestUndeliveredAgeSeconds)} giây</p>
          </>:null}
        </div>
      </div>
    </section>
    <section className="mt-10" aria-label="Danh sách Outbox">
      <div className="flex flex-wrap items-center gap-3"><h2 className="text-xl font-bold text-brand-ink">Sự kiện Outbox</h2>
        <label className="text-sm" htmlFor="outbox-filter">Trạng thái</label>
        <select id="outbox-filter" value={outboxFilter} className="min-h-11 rounded-xl border border-border bg-surface px-3 text-sm" onChange={e=>{setOutboxFilter(e.target.value as OutboxStatus|'');setOutboxPage(0);}}>
          <option value="">Tất cả</option>{outboxStatuses.map(status=><option key={status}>{status}</option>)}
        </select>
      </div>
      <div className="mt-5 space-y-3">
        {outbox.isPending?<StatusMessage title="Đang tải Outbox…"/>:null}
        {outbox.isError?<StatusMessage error title="Không tải được Outbox" detail={outbox.error.message}/>:null}
        {outbox.isSuccess&&!outbox.data.data?.length?<StatusMessage title="Không có sự kiện Outbox phù hợp"/>:null}
        {outbox.data?.data?.map((item,index)=><OutboxCard key={item.id??index} item={item} onDone={refresh}/>)}
        {outbox.isSuccess?<RecordPagination page={outboxPage} pages={outbox.data.totalPages??0} onChange={setOutboxPage}/>:null}
      </div>
    </section>
    <section className="mt-10" aria-label="Danh sách thông báo">
      <div className="flex flex-wrap items-center gap-3"><h2 className="text-xl font-bold text-brand-ink">Thông báo cảnh báo</h2>
        <label className="text-sm" htmlFor="alert-filter">Trạng thái</label>
        <select id="alert-filter" value={alertFilter} className="min-h-11 rounded-xl border border-border bg-surface px-3 text-sm" onChange={e=>{setAlertFilter(e.target.value as AlertStatus|'');setAlertPage(0);}}>
          <option value="">Tất cả</option>{alertStatuses.map(status=><option key={status}>{status}</option>)}
        </select>
      </div>
      <div className="mt-5 space-y-3">
        {alerts.isPending?<StatusMessage title="Đang tải thông báo…"/>:null}
        {alerts.isError?<StatusMessage error title="Không tải được thông báo" detail={alerts.error.message}/>:null}
        {alerts.isSuccess&&!alerts.data.data?.length?<StatusMessage title="Không có thông báo phù hợp"/>:null}
        {alerts.data?.data?.map((item,index)=><AlertCard key={item.id??index} item={item} onDone={refresh}/>)}
        {alerts.isSuccess?<RecordPagination page={alertPage} pages={alerts.data.totalPages??0} onChange={setAlertPage}/>:null}
      </div>
    </section>
    </Container>
  </div>;
}
