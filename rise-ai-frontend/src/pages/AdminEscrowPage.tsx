import { AdminMasthead, AdminState } from '@/components/ui/AdminOperations';
import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, BookOpenCheck, Landmark, RefreshCcw, ShieldAlert } from 'lucide-react';
import { Link } from 'react-router';
import {
  campaignEscrow, campaignReconciliation, getReconciliationRun, globalReconciliation, ledgerEntries,
  proposeAdjustment, proposeSettlement, reconciliationRuns, runReconciliation,
} from '@/api/admin';
import { useAuth } from '@/auth/auth-context';
import { Container } from '@/components/layout/Container';
import { Button } from '@/components/ui/Button';
import { AdminChainPanel } from '@/pages/OnchainViews';
import { Input } from '@/components/ui/Input';
import { RecordPagination } from '@/components/ui/RecordPagination';
import { StatusMessage } from '@/components/ui/StatusMessage';
import { amountLabel, dateLabel } from '@/lib/campaign-display';
import { shortId } from '@/lib/admin-display';
import type { ReconciliationReport, EscrowBalances } from '@/api/contracts';


const balanceFields: {key:keyof EscrowBalances;label:string}[]=[
  {key:'available',label:'Khả dụng'},
  {key:'confirmed',label:'Đã xác nhận'},
  {key:'reserved',label:'Đang dự trữ'},
  {key:'refundReserved',label:'Dự trữ hoàn tiền'},
  {key:'disbursed',label:'Đã giải ngân'},
  {key:'refunded',label:'Đã hoàn tiền'},
  {key:'adjustmentCredits',label:'Điều chỉnh tăng'},
  {key:'adjustmentDebits',label:'Điều chỉnh giảm'},
];
const reconciliationState = (value?: boolean) => value === true ? 'CONSISTENT' : value === false ? 'INCONSISTENT' : undefined;
const reconciliationLabel = (value?: boolean) => value === true ? 'Nhất quán' : value === false ? 'Có sai lệch' : 'Chưa xác định';
function ReconciliationBalances({report}:{report:ReconciliationReport}){
  return <div className="mt-4 w-full min-w-0 max-w-full">
    <p className="mb-3 text-xs font-semibold text-brand-ink">Đối chiếu 8 cấu phần · {report.consistent===true?'Nhất quán':report.consistent===false?'Có sai lệch':'Chưa xác định'}</p>
    <div className="w-full min-w-0 max-w-full overflow-x-auto">
      <table className="w-full min-w-[370px] border-collapse text-left admin-ledger-table">
        <thead><tr className="border-b border-border"><th scope="col" className="py-2 pr-3">Cấu phần</th><th scope="col" className="py-2 pr-3">Lưu trong hệ thống</th><th scope="col" className="py-2 pr-3">Tính từ ledger</th><th scope="col" className="py-2">Kết quả</th></tr></thead>
        <tbody>{balanceFields.map(field=>{
          const stored=report.stored?.[field.key];
          const computed=report.computedFromLedger?.[field.key];
          const known=typeof stored==='number'&&Number.isFinite(stored)&&typeof computed==='number'&&Number.isFinite(computed);
          const matched=known&&stored===computed;
          return <tr key={field.key} data-result={!known?"unknown":matched?"match":"mismatch"}>
            <th scope="row" className="py-2 pr-3 font-semibold text-brand-ink">{field.label}</th>
            <td className="py-2 pr-3 tabular-nums">{amountLabel(stored,report.currency)}</td>
            <td className="py-2 pr-3 tabular-nums">{amountLabel(computed,report.currency)}</td>
            <td className="py-2"><AdminState value={known ? (matched?'CONSISTENT':'INCONSISTENT') : undefined}>{!known?'Thiếu dữ liệu':matched?'Khớp':'Sai lệch'}</AdminState></td>
          </tr>;
        })}</tbody>
      </table>
    </div>
  </div>;
}

const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function AdminEscrowPage(){
  const {user}=useAuth();
  const client=useQueryClient();
  const scope=['admin',user?.id] as const;
  const [runPage,setRunPage]=useState(0);
  const [ledgerPage,setLedgerPage]=useState(0);
  const [campaignId,setCampaignId]=useState('');
  const [activeId,setActiveId]=useState('');
  const [runId,setRunId]=useState('');
  const [kind,setKind]=useState<'adjustment'|'settlement'>('adjustment');
  const [direction,setDirection]=useState<'CREDIT'|'DEBIT'|'NEUTRAL'>('CREDIT');
  const [amount,setAmount]=useState('');
  const [reason,setReason]=useState('');
  const [ack,setAck]=useState(false);
  const [notice,setNotice]=useState('');
  const [validation,setValidation]=useState('');
  const reports=useQuery({queryKey:[...scope,'reconciliation','all'],queryFn:globalReconciliation});
  const runs=useQuery({queryKey:[...scope,'reconciliation','runs',runPage],queryFn:()=>reconciliationRuns(runPage)});
  const selectedRun=useQuery({queryKey:[...scope,'reconciliation','run',runId],queryFn:()=>getReconciliationRun(runId),enabled:!!runId});
  const summary=useQuery({queryKey:[...scope,'escrow',activeId],queryFn:()=>campaignEscrow(activeId),enabled:!!activeId});
  const reconciliation=useQuery({queryKey:[...scope,'reconciliation','campaign',activeId],queryFn:()=>campaignReconciliation(activeId),enabled:!!activeId});
  const ledger=useQuery({queryKey:[...scope,'ledger',activeId,ledgerPage],queryFn:()=>ledgerEntries(activeId,ledgerPage),enabled:!!activeId});
  const invalidate=()=>void client.invalidateQueries({queryKey:scope});
  const execute=useMutation({mutationFn:runReconciliation,onSuccess(result){
    setNotice('Đã tạo lượt đối soát mô phỏng '+(result.runCode??'')+'.');if(result.id)setRunId(result.id);invalidate();
  }});
  const propose=useMutation({
    mutationFn:({value,why}:{value?:number;why:string})=>
      kind==='adjustment'?proposeAdjustment(activeId,value!,direction,why):proposeSettlement(activeId,why),
    onSuccess(){
      setNotice('Đã tạo đề nghị '+(kind==='adjustment'?'điều chỉnh':'tất toán')+'; chờ người duyệt độc lập. Không có khoản tiền thật được chuyển.');
      setAmount('');setReason('');setAck(false);invalidate();
    },
  });
  async function submit(event:FormEvent<HTMLFormElement>){
    event.preventDefault();if(propose.isPending||!ack||!activeId)return;
    const value=Number(amount);
    if(!reason.trim()||(kind==='adjustment'&&(!Number.isSafeInteger(value)||value<=0))){
      setValidation('Nhập lý do cụ thể và số tiền mô phỏng nguyên dương cho điều chỉnh.');return;
    }
    setValidation('');
    try{await propose.mutateAsync({value:kind==='adjustment'?value:undefined,why:reason.trim()});}catch{/* Backend error displayed below. */}
  }
  function chooseCampaign(candidate:string){
    if(!uuid.test(candidate.trim())){setValidation('Mã chiến dịch phải là UUID hợp lệ.');return;}
    setValidation('');setActiveId(candidate.trim());setCampaignId(candidate.trim());setLedgerPage(0);setNotice('');
  }
  return <div className="rise-deep-page deep-admin-escrow admin-ops">
    <div className="rise-deep-topline" aria-hidden="true">
      <span>ADMIN / TÀI CHÍNH MÔ PHỎNG</span><span>05 · SỔ CÁI & ĐỐI SOÁT</span>
    </div>
    <Container className="deep-inner py-10 sm:py-14">
    <Link to="/admin" className="inline-flex min-h-11 items-center gap-2 text-sm font-bold text-brand"><ArrowLeft size={17} aria-hidden="true"/> Trung tâm quản trị</Link>
    <Button type="button" variant="outline" className="admin-escrow-refresh" onClick={invalidate}><RefreshCcw size={17} aria-hidden="true"/> Làm mới</Button>
    <AdminMasthead title="Escrow & đối soát" eyebrow="Sổ cái mô phỏng" art="wallet"><p>Đối chiếu số dư, kiểm tra sai lệch và truy vết bút toán. Mọi khoản tiền trên trang thuộc sổ mô phỏng.</p></AdminMasthead>
    <section className="admin-ledger-section" aria-labelledby="all-reconciliation">
      <h2 id="all-reconciliation" className="flex items-center gap-2 text-2xl font-extrabold text-brand-ink"><BookOpenCheck className="text-brand" size={23} aria-hidden="true"/> Tính nhất quán của các tài khoản</h2>
      <p className="mt-2 text-xs leading-6 text-muted">Đối chiếu số dư lưu trữ với số dư tính từ ledger. Chỉ hiển thị kết quả Backend; số dư không nhất quán phải được xử lý trước khi ghi sổ tiếp.</p>
      {reports.isPending?<div className="mt-5"><StatusMessage title="Đang đối chiếu số liệu…"/></div>:null}
      {reports.isError?<div className="mt-5"><StatusMessage error title="Không thể đối chiếu" detail={reports.error.message}/></div>:null}
      {reports.isSuccess&&!reports.data.length?<div className="mt-5"><StatusMessage title="Chưa có tài khoản escrow để đối chiếu"/></div>:null}
      {reports.isSuccess?<div className="admin-ledger-reports">
        {reports.data.map((report,index)=><article key={report.campaignId??index} className="min-w-0 max-w-full admin-ledger-report">
          <div className="flex flex-wrap items-center justify-between gap-2"><p className="text-sm font-bold text-brand-ink">Chiến dịch {shortId(report.campaignId)}</p><AdminState value={reconciliationState(report.consistent)}>{reconciliationLabel(report.consistent)}</AdminState></div>
          {report.stored && <dl className="admin-ledger-lead">
            <div><dt>Số dư khả dụng lưu trong hệ thống</dt><dd>{amountLabel(report.stored.available,report.currency)}</dd></div>
            <div><dt>Khoản đang dự trữ lưu trong hệ thống</dt><dd>{amountLabel(report.stored.reserved,report.currency)}</dd></div>
          </dl>}
          <ReconciliationBalances report={report}/>
          <p className="mt-1 text-xs text-muted">{report.ledgerEntryCount??'—'} bút toán đã ghi nhận</p>
          {report.campaignId?<Button variant="outline" className="mt-4" onClick={()=>chooseCampaign(report.campaignId!)}>Kiểm tra chi tiết</Button>:null}
        </article>)}
      </div>:null}
    </section>
    <section className="mt-12" aria-labelledby="campaign-escrow">
      <h2 id="campaign-escrow" className="flex items-center gap-2 text-2xl font-extrabold text-brand-ink"><Landmark className="text-brand" size={24} aria-hidden="true"/> Escrow theo chiến dịch</h2>
      <div className="mt-5 flex flex-wrap items-end gap-3">
        <div className="min-w-60 flex-1"><Input id="escrow-campaign" label="UUID chiến dịch" placeholder="Dán mã chiến dịch từ hồ sơ" value={campaignId} onChange={e=>setCampaignId(e.target.value)}/></div>
        <Button variant="outline" onClick={()=>chooseCampaign(campaignId)}>Xem sổ</Button>
      </div>
      {validation?<p role="alert" className="mt-3 text-sm text-danger">{validation}</p>:null}
      {activeId?<div className="mt-7">
        <h3 className="text-lg font-bold text-brand-ink">Chiến dịch {shortId(activeId)}</h3>
        {summary.isPending?<div className="mt-4"><StatusMessage title="Đang tải tài khoản…"/></div>:null}
        {summary.isError?<div className="mt-4"><StatusMessage error title="Không thể tải escrow" detail={summary.error.message}/></div>:null}
        {summary.isSuccess?<div className="mt-4 rounded-card border border-border bg-surface p-6">
          <p className="text-xs font-semibold text-muted">Trạng thái tài khoản: {summary.data.escrowStatus??'Chưa xác định'}</p>
          <div className="admin-ledger-balances">
            {([['Số dư khả dụng',summary.data.availableBalance],['Tổng xác nhận',summary.data.totalConfirmedContributions],
              ['Đang dự trữ',summary.data.totalReserved],['Dự trữ hoàn tiền',summary.data.totalRefundReserved],
              ['Đã giải ngân',summary.data.totalDisbursed],['Đã hoàn tiền',summary.data.totalRefunded]] as const).map(([label,value])=><div key={label}><p className="text-xs text-muted">{label}</p><p className="mt-2 text-lg font-bold text-brand-ink">{amountLabel(value,summary.data.currency)}</p></div>)}
          </div>
        </div>:null}
        {reconciliation.isPending?<p className="mt-4 text-xs text-muted">Đang đối chiếu tài khoản…</p>:null}
        {reconciliation.isError?<p role="alert" className="mt-4 text-sm text-danger">{reconciliation.error.message}</p>:null}
        {reconciliation.isSuccess?<div className="mt-4 min-w-0 max-w-full rounded-xl border border-border bg-surface p-5">
          <div className="admin-ledger-result"><AdminState value={reconciliationState(reconciliation.data.consistent)}>{reconciliationLabel(reconciliation.data.consistent)}</AdminState><span>{reconciliation.data.ledgerEntryCount??'—'} bút toán</span></div>
          <ReconciliationBalances report={reconciliation.data}/>
        </div>:null}
        <AdminChainPanel campaignId={activeId} />
        <h3 className="mt-8 text-lg font-bold text-brand-ink">Bút toán sổ cái</h3>
        {ledger.isPending?<div className="mt-4"><StatusMessage title="Đang tải bút toán…"/></div>:null}
        {ledger.isError?<div className="mt-4"><StatusMessage error title="Không tải được bút toán" detail={ledger.error.message}/></div>:null}
        {ledger.isSuccess&&!ledger.data.data?.length?<div className="mt-4"><StatusMessage title="Chưa có bút toán"/></div>:null}
        <div className="mt-4 space-y-3">{ledger.data?.data?.map(item=><div key={item.id} className="flex flex-wrap justify-between gap-3 rounded-xl border border-border bg-surface p-5">
          <div><p className="text-sm font-bold text-brand-ink">{item.transactionType}</p><p className="mt-1 text-xs text-muted">{item.transactionCode??shortId(item.id)} · {dateLabel(item.createdAt)}</p></div>
          <p className="text-sm font-bold text-brand">{item.direction} {amountLabel(item.amount,item.currency)}</p>
        </div>)}</div>
        {ledger.isSuccess?<RecordPagination page={ledgerPage} pages={ledger.data.totalPages??0} onChange={setLedgerPage}/>:null}
        <form className="admin-ledger-proposal grid gap-4" onSubmit={e=>void submit(e)}>
          <h3 className="text-lg font-bold text-brand-ink">Đề xuất bút toán mô phỏng</h3>
          <p className="text-xs leading-6 text-muted">Đề xuất không phải đã áp dụng. Quyết định duyệt điều chỉnh/tất toán thực hiện qua hàng chờ Admin, với kiểm tra tách người tạo và người duyệt của Backend.</p>
          <div><label htmlFor="escrow-proposal-type" className="mb-2 block text-sm font-semibold">Loại đề xuất</label>
            <select id="escrow-proposal-type" value={kind} className="min-h-11 w-full rounded-xl border border-border bg-surface px-3" onChange={e=>{setKind(e.target.value as 'adjustment'|'settlement');setAck(false);}}>
              <option value="adjustment">Điều chỉnh sổ</option><option value="settlement">Tất toán mô phỏng</option>
            </select>
          </div>
          {kind==='adjustment'?<>
            <div><label htmlFor="escrow-direction" className="mb-2 block text-sm font-semibold">Chiều điều chỉnh</label><select id="escrow-direction" className="min-h-11 w-full rounded-xl border border-border bg-surface px-3" value={direction} onChange={e=>setDirection(e.target.value as 'CREDIT'|'DEBIT'|'NEUTRAL')}>
              <option value="CREDIT">CREDIT</option><option value="DEBIT">DEBIT</option><option value="NEUTRAL">NEUTRAL</option>
            </select></div>
            <Input id="escrow-amount" label="Giá trị điều chỉnh (VND mô phỏng)" required type="number" step="1" min="1" value={amount} onChange={e=>setAmount(e.target.value)}/>
          </>:null}
          <div><label className="mb-2 block text-sm font-semibold" htmlFor="escrow-reason">Lý do</label><textarea id="escrow-reason" rows={3} maxLength={2000} required className="w-full rounded-xl border border-border bg-surface p-3 text-sm" value={reason} onChange={e=>setReason(e.target.value)}/></div>
          <label className="flex items-start gap-3 text-sm"><input type="checkbox" className="mt-1 accent-brand" checked={ack} onChange={e=>setAck(e.target.checked)}/> Xác nhận đây là đề nghị cho sổ mô phỏng, không phải ghi nhận thanh toán thật.</label>
          {propose.isError?<p role="alert" className="text-sm text-danger">{propose.error.message}</p>:null}
          <Button type="submit" loading={propose.isPending} disabled={!ack}>Tạo đề nghị (chờ duyệt)</Button>
        </form>
      </div>:null}
    </section>
    <section className="mt-12" aria-labelledby="run-history">
      <h2 id="run-history" className="text-2xl font-extrabold text-brand-ink">Lịch sử đối soát</h2>
      <div className="mt-5 flex flex-wrap gap-3"><Button disabled={execute.isPending} loading={execute.isPending} onClick={()=>{if(window.confirm('Tạo một lượt đối soát toàn hệ thống mô phỏng?'))execute.mutate();}}>Tạo lượt đối soát</Button></div>
      {execute.isError?<p className="mt-3 text-sm text-danger" role="alert">{execute.error.message}</p>:null}
      {notice?<p className="mt-4 rounded-xl bg-soft-mint p-4 text-sm text-brand-ink" role="status">{notice}</p>:null}
      {runs.isPending?<div className="mt-5"><StatusMessage title="Đang tải lịch sử…"/></div>:null}
      {runs.isError?<div className="mt-5"><StatusMessage error title="Không tải được lịch sử" detail={runs.error.message}/></div>:null}
      {runs.isSuccess&&!runs.data.data?.length?<div className="mt-5"><StatusMessage title="Chưa có lượt đối soát"/></div>:null}
      <div className="mt-5 space-y-3">{runs.data?.data?.map(item=><article key={item.id} className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-border bg-surface p-5">
        <div><h3 className="text-sm font-bold text-brand-ink">{item.runCode??shortId(item.id)} · {item.runStatus??'Chưa có trạng thái'}</h3><p className="mt-2 text-xs text-muted">{dateLabel(item.startedAt)} · Kiểm tra {item.accountsChecked??'—'} tài khoản · Sai lệch {item.inconsistentAccounts??'—'}</p></div>
        {item.id?<Button variant="outline" onClick={()=>setRunId(item.id!)}>Xem báo cáo</Button>:null}
      </article>)}</div>
      {runs.isSuccess?<RecordPagination page={runPage} pages={runs.data.totalPages??0} onChange={setRunPage}/>:null}
      {runId?<div className="mt-6 rounded-card border border-border bg-surface p-6">
        <h3 className="text-lg font-bold text-brand-ink">Báo cáo lượt đối soát {shortId(runId)}</h3>
        {selectedRun.isPending?<p className="mt-2 text-sm text-muted">Đang tải…</p>:null}
        {selectedRun.isError?<p role="alert" className="mt-3 text-sm text-danger">{selectedRun.error.message}</p>:null}
        {selectedRun.isSuccess?<><p className="mt-3 text-sm font-semibold">{selectedRun.data.runStatus} · {selectedRun.data.accountsChecked??'—'} tài khoản · {selectedRun.data.inconsistentAccounts??'—'} sai lệch</p>
          {selectedRun.data.errorMessage?<p className="mt-2 text-sm text-danger">{selectedRun.data.errorMessage}</p>:null}
          {selectedRun.data.findings?.map((f,index)=><div key={f.escrowAccountId??index} className="mt-5 min-w-0 max-w-full rounded-xl border border-border p-4">
            <p className="font-bold text-brand-ink">Chiến dịch {shortId(f.campaignId)}</p>
            <ReconciliationBalances report={f}/>
          </div>)}
        </>:null}
      </div>:null}
    </section>
    <p className="mt-10 flex items-start gap-3 rounded-card bg-soft-sand p-5 text-xs leading-6 text-warning"><ShieldAlert className="shrink-0" size={20} aria-hidden="true"/> Nếu báo cáo đối soát có sai lệch, không diễn giải số liệu như đã khớp và không coi việc tạo đề nghị là hoàn tất nghiệp vụ.</p>
    </Container>
  </div>;
}
