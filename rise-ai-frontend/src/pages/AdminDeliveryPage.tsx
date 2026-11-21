import { AdminMasthead } from '@/components/ui/AdminOperations';
import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Activity, AlertTriangle, Check, Clock3, Info, Radio, RefreshCcw, Send, Webhook } from 'lucide-react';
import { useAuth } from '@/auth/auth-context';
import { listAlerts, listOutbox, getAlertStats, getOutboxStats, requeueAlert, requeueOutbox, type AlertEvent, type AlertStatus, type OutboxEvent, type OutboxStatus } from '@/api/monitoring';
import { Container } from '@/components/layout/Container';
import { AdminMonitoringNav } from '@/components/ui/AdminMonitoringNav';
import { Button } from '@/components/ui/Button';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { countText, readableJson, timeText } from '@/lib/monitoring-display';

const outboxStatuses:OutboxStatus[]=['PENDING','IN_FLIGHT','PUBLISHED','FAILED'];
const alertStatuses:AlertStatus[]=['PENDING','RETRYING','SENT','FAILED'];

const deliveryLabels = { PENDING:'Đang chờ', IN_FLIGHT:'Đang chuyển', PUBLISHED:'Đã xuất bản', FAILED:'Gặp lỗi', RETRYING:'Đang thử lại', SENT:'Đã gửi' };
const deliveryIcons = {
  PENDING: Clock3, IN_FLIGHT: Activity, PUBLISHED: Check,
  FAILED: AlertTriangle, RETRYING: RefreshCcw, SENT: Check,
};

function DeliveryState({status}:{status?:OutboxStatus|AlertStatus}){
  const Icon=status ? deliveryIcons[status] ?? Info : Info;
  return <span className="delivery-state"><Icon size={18} aria-hidden="true"/>{status ? deliveryLabels[status] ?? status : 'Chưa rõ'}</span>;
}

function DeliverySystemState({enabled,label}:{enabled?:boolean;label:string}){
  const Icon=enabled===true?Check:enabled===false?AlertTriangle:Info;
  return <p className="delivery-system-state" data-enabled={enabled}>
    <Icon size={17} aria-hidden="true"/>{label}
  </p>;
}

function DeliverySummaryCounts({counts}:{counts:{status:OutboxStatus|AlertStatus;label:string;value?:number}[]}){
  return <dl className="delivery-summary-counts">
    {counts.map(({status,label,value})=>{
      const Icon=deliveryIcons[status];
      return <div key={status} data-status={status}>
        <dt><Icon size={16} aria-hidden="true"/>{label}</dt>
        <dd>{countText(value)}</dd>
      </div>;
    })}
  </dl>;
}

function DeliveryTiming({attempts,nextAttemptAt,completedAt,completedLabel}:{
  attempts?:number;nextAttemptAt?:string;completedAt?:string;completedLabel:string;
}){
  return <dl className="delivery-timing">
    <div><dt>Số lần thử</dt><dd>{countText(attempts)}</dd></div>
    <div><dt>Lịch thử lại</dt><dd>{timeText(nextAttemptAt)}</dd></div>
    <div><dt>{completedLabel}</dt><dd>{timeText(completedAt)}</dd></div>
  </dl>;
}

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
  return <div className="delivery-retry">
    <Button className="delivery-retry-toggle" variant="outline" type="button" aria-expanded={expanded} onClick={()=>setExpanded(v=>!v)}><Send size={16} aria-hidden="true"/> {type==='outbox'?'Đưa lại vào hàng chờ':'Xếp lại lịch gửi'}</Button>
    {expanded?<form className="delivery-retry-form" onSubmit={e=>void submit(e)}>
      <p className="delivery-retry-guidance">Chỉ áp dụng cho trạng thái FAILED. Đưa lại vào hàng chờ không có nghĩa gửi thành công. Backend quyết định trạng thái và lưu dấu vết.</p>
      <label htmlFor={'retry-'+type+'-'+id} className="delivery-retry-label">Lý do thử lại (bắt buộc)</label>
      <textarea id={'retry-'+type+'-'+id} maxLength={2000} required rows={3} value={reason} onChange={e=>setReason(e.target.value)} className="delivery-retry-input"/>
      <label className="delivery-retry-confirm"><input type="checkbox" checked={confirm} onChange={e=>setConfirm(e.target.checked)} className="mt-1 accent-brand"/> Tôi đã kiểm tra lỗi trước khi thử lại.</label>
      {mutation.isError?<p role="alert" className="text-sm text-danger">{mutation.error.message}</p>:null}
      <Button className="delivery-retry-submit" type="submit" loading={mutation.isPending} disabled={!confirm||!reason.trim()}>Xác nhận xếp lại hàng chờ</Button>
    </form>:null}
  </div>;
}

function DeliveryError({label,message}:{label:string;message:string}){
  return <div className="admin-delivery-error">
    <AlertTriangle size={22} aria-hidden="true"/>
    <div><strong>{label}</strong><p>{message}</p></div>
  </div>;
}

function OutboxCard({item,onDone}:{item:OutboxEvent;onDone:()=>void}){
  return <article className="admin-delivery-record" data-status={item.status}>
    <DeliveryState status={item.status}/>
    <h3>{item.eventType??'Sự kiện chưa rõ'}</h3>
    <p className="delivery-record-meta"><time dateTime={item.occurredAt}>{timeText(item.occurredAt)}</time> · Event {item.eventId??'—'}</p>
    <p className="delivery-record-meta">{item.aggregateType??'—'} · {item.aggregateId??'—'}</p>
    <DeliveryTiming attempts={item.attempts} nextAttemptAt={item.nextAttemptAt} completedAt={item.publishedAt} completedLabel="Đã xuất bản"/>
    {item.lastError?<DeliveryError label="Lỗi Backend" message={item.lastError}/>:null}
    {item.status==='FAILED'&&item.id?<RetryForm type="outbox" id={item.id} onDone={onDone}/>:null}
  </article>;
}

function AlertCard({item,onDone}:{item:AlertEvent;onDone:()=>void}){
  return <article className="admin-delivery-record" data-status={item.status}>
    <DeliveryState status={item.status}/>
    <h3>{item.kind??'Thông báo chưa rõ'}</h3>
    <p className="delivery-record-meta"><time dateTime={item.createdAt}>{timeText(item.createdAt)}</time> · Alert {item.id??'—'}</p>
    <DeliveryTiming attempts={item.attempts} nextAttemptAt={item.nextAttemptAt} completedAt={item.deliveredAt} completedLabel="Đã gửi"/>
    {item.details&&Object.keys(item.details).length>0?<details className="delivery-details">
      <summary>Chi tiết thông báo</summary>
      <pre>{readableJson(item.details)}</pre>
    </details>:null}
    {item.lastError?<DeliveryError label="Lỗi gửi" message={item.lastError}/>:null}
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
  return <div className="rise-deep-page deep-admin-delivery admin-ops">
    <div className="rise-deep-topline" aria-hidden="true">
      <span>ADMIN / DÒNG SỰ KIỆN</span><span>08 · GIAO NHẬN CẢNH BÁO</span>
    </div>
    <Container className="deep-inner py-10 sm:py-14">
    <AdminMasthead title="Đường đi sự kiện và cảnh báo" eyebrow="Vận hành giao nhận" art="megaphone">
      <p>Theo dõi hai luồng giao nhận. Đưa lại vào hàng chờ chỉ đặt lại lịch, kết quả gửi vẫn do hệ thống ghi nhận.</p>
    </AdminMasthead>
    <AdminMonitoringNav/>
    <section className="delivery-overview" aria-labelledby="delivery-overview-heading">
      <header className="delivery-overview-head">
        <h2 id="delivery-overview-heading">Tình trạng chuyển sự kiện</h2>
        <Button variant="outline" onClick={refresh}><RefreshCcw size={16} aria-hidden="true"/> Làm mới</Button>
      </header>
      <div className="delivery-grid">
        <section className="delivery-summary">
          <header className="delivery-summary-header">
            <h3><Radio aria-hidden="true"/> Backend → Monitoring</h3>
            {outboxStats.data?<DeliverySystemState enabled={outboxStats.data.relayEnabled} label={outboxStats.data.relayEnabled===true?'Đã bật relay':outboxStats.data.relayEnabled===false?'Relay đang tắt':'Chưa rõ trạng thái relay'}/>:null}
          </header>
          {outboxStats.isPending?<p>Đang tải…</p>:null}
          {outboxStats.isError?<p role="alert">{outboxStats.error.message}</p>:null}
          {outboxStats.data?<>
            <dl className="delivery-key-metrics">
              <div className="delivery-count" data-failed={typeof outboxStats.data.failed==='number' && outboxStats.data.failed>0}><dt>Mục gặp lỗi</dt><dd>{countText(outboxStats.data.failed)}</dd></div>
              <div><dt>Sự kiện chưa giao lâu nhất</dt><dd>{countText(outboxStats.data.oldestUndeliveredAgeSeconds)}{typeof outboxStats.data.oldestUndeliveredAgeSeconds==='number' && ' giây'}</dd></div>
            </dl>
            <DeliverySummaryCounts counts={[
              {status:'PENDING',label:'Chờ',value:outboxStats.data.pending},
              {status:'IN_FLIGHT',label:'Đang xử lý',value:outboxStats.data.inFlight},
              {status:'PUBLISHED',label:'Đã xuất bản',value:outboxStats.data.published},
            ]}/>
          </>:null}
        </section>
        <section className="delivery-summary delivery-alerts">
          <header className="delivery-summary-header">
            <h3><Webhook aria-hidden="true"/> Thông báo ra ngoài</h3>
            {alertStats.data?<DeliverySystemState enabled={alertStats.data.webhookConfigured} label={alertStats.data.webhookConfigured===true?'Webhook đã cấu hình':alertStats.data.webhookConfigured===false?'Webhook chưa cấu hình':'Chưa rõ cấu hình webhook'}/>:null}
          </header>
          {alertStats.isPending?<p>Đang tải…</p>:null}
          {alertStats.isError?<p role="alert">{alertStats.error.message}</p>:null}
          {alertStats.data?<>
            <dl className="delivery-key-metrics">
              <div className="delivery-count" data-failed={typeof alertStats.data.failed==='number' && alertStats.data.failed>0}><dt>Mục gặp lỗi</dt><dd>{countText(alertStats.data.failed)}</dd></div>
              <div><dt>Thông báo chưa gửi lâu nhất</dt><dd>{countText(alertStats.data.oldestUndeliveredAgeSeconds)}{typeof alertStats.data.oldestUndeliveredAgeSeconds==='number' && ' giây'}</dd></div>
            </dl>
            <DeliverySummaryCounts counts={[
              {status:'PENDING',label:'Chờ',value:alertStats.data.pending},
              {status:'RETRYING',label:'Thử lại',value:alertStats.data.retrying},
              {status:'SENT',label:'Đã gửi',value:alertStats.data.sent},
            ]}/>
          </>:null}
        </section>
      </div>
    </section>
    <div className="delivery-grid delivery-lanes">
    <section className="delivery-lane" aria-label="Danh sách Outbox">
      <div className="delivery-queue">
      <div className="delivery-queue-head"><h2>Sự kiện Outbox</h2>
        <label className="text-sm" htmlFor="outbox-filter">Trạng thái</label>
        <select id="outbox-filter" value={outboxFilter} className="min-h-11 rounded-xl border border-border bg-surface px-3 text-sm" onChange={e=>{setOutboxFilter(e.target.value as OutboxStatus|'');setOutboxPage(0);}}>
          <option value="">Tất cả</option>{outboxStatuses.map(status=><option key={status} value={status}>{deliveryLabels[status]}</option>)}
        </select>
      </div>
      <div className="delivery-queue-body">
        {outbox.isPending?<StatusMessage title="Đang tải Outbox…"/>:null}
        {outbox.isError?<StatusMessage error title="Không tải được Outbox" detail={outbox.error.message}/>:null}
        {outbox.isSuccess&&!outbox.data.data?.length?<StatusMessage title="Không có sự kiện Outbox phù hợp" detail={outboxFilter?'Không có sự kiện ở trạng thái đang lọc. Chọn “Tất cả” để xem các trạng thái khác.':'Chưa có sự kiện được Backend trả về.'}/>:null}
        {outbox.data?.data?.map((item,index)=><OutboxCard key={item.id??index} item={item} onDone={refresh}/>)}
        {outbox.isSuccess?<RecordPagination page={outboxPage} pages={outbox.data.totalPages??0} onChange={setOutboxPage}/>:null}
      </div>
      </div>
    </section>
    <section className="delivery-lane delivery-alerts" aria-label="Danh sách thông báo">
      <div className="delivery-queue">
      <div className="delivery-queue-head"><h2>Thông báo cảnh báo</h2>
        <label className="text-sm" htmlFor="alert-filter">Trạng thái</label>
        <select id="alert-filter" value={alertFilter} className="min-h-11 rounded-xl border border-border bg-surface px-3 text-sm" onChange={e=>{setAlertFilter(e.target.value as AlertStatus|'');setAlertPage(0);}}>
          <option value="">Tất cả</option>{alertStatuses.map(status=><option key={status} value={status}>{deliveryLabels[status]}</option>)}
        </select>
      </div>
      <div className="delivery-queue-body">
        {alerts.isPending?<StatusMessage title="Đang tải thông báo…"/>:null}
        {alerts.isError?<StatusMessage error title="Không tải được thông báo" detail={alerts.error.message}/>:null}
        {alerts.isSuccess&&!alerts.data.data?.length?<StatusMessage title="Không có thông báo phù hợp" detail={alertFilter?'Không có thông báo ở trạng thái đang lọc. Chọn “Tất cả” để mở rộng kết quả.':'Chưa có thông báo được Backend trả về.'}/>:null}
        {alerts.data?.data?.map((item,index)=><AlertCard key={item.id??index} item={item} onDone={refresh}/>)}
        {alerts.isSuccess?<RecordPagination page={alertPage} pages={alerts.data.totalPages??0} onChange={setAlertPage}/>:null}
      </div>
      </div>
    </section>
    </div>
    </Container>
  </div>;
}
