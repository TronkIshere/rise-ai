import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowUpRight, ClipboardCheck, Landmark, RefreshCcw, ShieldCheck, ScanSearch, BookOpenText } from 'lucide-react';
import { Link } from 'react-router';
import { useAuth } from '@/auth/auth-context';
import { adminQueues, adminStatistics, listAdminQueue, type AdminItem, type AdminQueue } from '@/api/admin';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { adminStatusFilters, shortId } from '@/lib/admin-display';
import { amountLabel } from '@/lib/campaign-display';

function recordInfo(kind:AdminQueue,item:AdminItem){
  const o=item as unknown as Record<string,unknown>;
  const title=String(o.name??o.title??o.donationCode??o.refundCode??o.requestCode??o.disputeCode??o.adjustmentCode??o.settlementCode??('Bản ghi '+shortId(String(o.id??''))));
  const status=String(o.verificationStatus??o.campaignStatus??o.donationStatus??o.refundStatus??o.requestStatus??o.disputeStatus??o.adjustmentStatus??o.settlementStatus??'');
  const amount=typeof o.amount==='number'?o.amount:typeof o.requestedAmount==='number'?o.requestedAmount:typeof o.targetAmount==='number'?o.targetAmount:undefined;
  const details=String(o.contactEmail??o.shortDescription??o.reason??o.requestReason??o.description??'');
  return {title,status,amount,details,id:String(o.id??''),kind,currency:String(o.currency??'VND')};
}
export function AdminDashboardPage(){
  const {user}=useAuth();
  const [kind,setKind]=useState<AdminQueue>('organizations');
  const [status,setStatus]=useState('PENDING_REVIEW');
  const [page,setPage]=useState(0);
  const key=['admin',user?.id] as const;
  const stats=useQuery({queryKey:[...key,'statistics'],queryFn:adminStatistics});
  const records=useQuery({queryKey:[...key,'queue',kind,status,page],queryFn:()=>listAdminQueue(kind,status,page)});
  function choose(value:AdminQueue){
    const next=adminQueues.find(x=>x.id===value);
    setKind(value);setStatus(next?.defaultStatus??'');setPage(0);
  }
  const metrics=[
    ['Đang chờ xác nhận',stats.data?.pendingDonations],
    ['Yêu cầu hoàn tiền mở',stats.data?.openRefundRequests],
    ['Đề nghị giải ngân mở',stats.data?.openDisbursementRequests],
    ['Tranh chấp mở',stats.data?.openDisputes],
  ];
  return <Container className="py-10 sm:py-14">
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p className="text-xs font-extrabold uppercase tracking-widest text-brand">ADMIN · QUY TRÌNH MÔ PHỎNG</p>
        <h1 className="mt-3 text-3xl font-extrabold text-brand-ink sm:text-4xl">Trung tâm xét duyệt</h1>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-muted">Trạng thái và số liệu lấy từ Backend. Không có giao dịch tiền thật. Hệ thống kiểm soát quyền Admin và ngăn tự phê duyệt ở phía máy chủ.</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Link to="/admin/escrow" className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-brand px-5 text-sm font-bold text-white"><Landmark size={18} aria-hidden="true"/> Escrow & đối soát</Link>
        <Link to="/admin/monitoring" className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-border bg-surface px-5 text-sm font-bold text-brand"><ScanSearch size={18} aria-hidden="true"/> Cảnh báo AI</Link>
        <Link to="/admin/audit" className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-border bg-surface px-5 text-sm font-bold text-brand"><BookOpenText size={18} aria-hidden="true"/> Audit</Link>
      </div>
    </div>
    <section className="mt-9" aria-label="Thống kê hoạt động">
      {stats.isPending?<StatusMessage title="Đang tải thống kê…"/>:null}
      {stats.isError?<StatusMessage error title="Không tải được thống kê" detail={stats.error.message}/>:null}
      {stats.isSuccess?<div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {metrics.map(([label,value])=><div key={label} className="rounded-card border border-border bg-surface p-5"><p className="text-xs font-semibold text-muted">{label}</p><p className="mt-3 text-3xl font-extrabold text-brand-ink">{typeof value==='number'?new Intl.NumberFormat('vi-VN').format(value):'—'}</p></div>)}
      </div>:null}
      {stats.isSuccess?<div className="mt-5 rounded-card bg-soft-mint p-5 text-sm leading-7 text-brand-ink">
        <strong>Quỹ mô phỏng:</strong> Đã xác nhận {amountLabel(stats.data.totalConfirmedContributions,stats.data.currency)} · Đã giải ngân {amountLabel(stats.data.totalDisbursed,stats.data.currency)} · Đã hoàn {amountLabel(stats.data.totalRefunded,stats.data.currency)}.
      </div>:null}
    </section>
    <section className="mt-12" aria-labelledby="admin-queues">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 id="admin-queues" className="flex items-center gap-2 text-2xl font-extrabold text-brand-ink"><ClipboardCheck className="text-brand" aria-hidden="true"/> Hàng chờ xử lý</h2>
        <Button variant="outline" onClick={()=>void records.refetch()}><RefreshCcw size={16} aria-hidden="true"/> Làm mới</Button>
      </div>
      <div className="mt-5 flex flex-wrap gap-2" aria-label="Nhóm xét duyệt">
        {adminQueues.map(x=><Button key={x.id} variant={kind===x.id?'primary':'outline'} onClick={()=>choose(x.id)}>{x.label}</Button>)}
      </div>
      <div className="mt-5 flex flex-wrap items-center gap-3">
        <label htmlFor="admin-queue-status" className="text-sm font-semibold text-copy">Trạng thái</label>
        <select id="admin-queue-status" className="min-h-11 rounded-xl border border-border bg-surface px-4 text-sm" value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
          <option value="">Tất cả</option>
          {adminStatusFilters[kind]?.map(x=><option key={x.value} value={x.value}>{x.label}</option>)}
        </select>
        {records.isSuccess?<p className="text-xs text-muted">{records.data.totalElements??0} bản ghi</p>:null}
      </div>
      <div className="mt-6 space-y-4">
        {records.isPending?<StatusMessage title="Đang tải hàng chờ xét duyệt…"/>:null}
        {records.isError?<div className="space-y-3"><StatusMessage error title="Không tải được hàng chờ" detail={records.error.message}/><Button variant="outline" onClick={()=>void records.refetch()}>Thử lại</Button></div>:null}
        {records.isSuccess&&!records.data.data?.length?<StatusMessage title="Không có bản ghi phù hợp" detail="Thay đổi bộ lọc hoặc chọn nhóm khác; không có dữ liệu demo giả được chèn vào danh sách."/>:null}
        {records.data?.data?.map((item,index)=>{
          const r=recordInfo(kind,item);
          return <article key={r.id||index} className="rounded-card border border-border bg-surface p-6">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div className="min-w-0 flex-1">
                <p className="text-xs font-semibold text-brand">{adminQueues.find(x=>x.id===kind)?.label} · {shortId(r.id)}</p>
                <h3 className="mt-2 break-words text-lg font-bold text-brand-ink">{r.title}</h3>
                {r.details?<p className="mt-2 line-clamp-2 text-sm leading-6 text-muted">{r.details}</p>:null}
                {r.amount!==undefined?<p className="mt-2 text-sm font-bold text-brand">{amountLabel(r.amount,r.currency)}</p>:null}
              </div>
              <span className="rounded-full bg-soft-sand px-3 py-2 text-xs font-semibold text-warning">{r.status}</span>
            </div>
            {r.id?<Link className="mt-4 inline-flex min-h-11 items-center gap-2 font-bold text-brand hover:underline" to={'/admin/review/'+kind+'/'+encodeURIComponent(r.id)}>Xem và xử lý <ArrowUpRight size={16} aria-hidden="true"/></Link>:null}
          </article>;
        })}
      </div>
      {records.isSuccess?<RecordPagination page={page} pages={records.data.totalPages??0} onChange={setPage}/>:null}
    </section>
    <div className="mt-9 flex items-start gap-3 rounded-xl bg-soft-mint p-5 text-sm text-brand-ink"><ShieldCheck className="shrink-0 text-brand" size={20} aria-hidden="true"/> Quyết định chỉ có hiệu lực sau khi Backend chấp nhận. Cảnh báo AI và Audit có khu vực độc lập để kiểm tra dữ kiện; AI chỉ khuyến nghị, Admin đưa ra quyết định.</div>
  </Container>;
}
