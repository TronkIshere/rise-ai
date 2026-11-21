import { AdminState } from '@/components/ui/AdminOperations';
import { useState, type FormEvent, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, RefreshCcw, ShieldAlert } from 'lucide-react';
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
  return <div className="u5-tablewrap w-full min-w-0 max-w-full overflow-x-auto">
      <table className="u5-reconciliation-table w-full min-w-[370px] border-collapse" aria-label="Bảng tám cấu phần số dư">
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
  </div>;
}


function knownConsistency(report?:ReconciliationReport):boolean|undefined{
  if(report?.consistent===false)return false;
  return report?.consistent===true&&balanceFields.every(({key})=>
    typeof report.stored?.[key]==='number'&&Number.isFinite(report.stored[key])&&
    typeof report.computedFromLedger?.[key]==='number'&&Number.isFinite(report.computedFromLedger[key]))?true:undefined;
}
function EscrowPanel({title,tone='sage',className='',children,action}:{title:string;tone?:'mint'|'apricot'|'sky'|'sage';className?:string;children:ReactNode;action?:ReactNode}){
  return <section className={'u5-panel u5-'+tone+' '+className} aria-label={title}>
    <header className="u5-panelhead"><h2>{title}</h2>{action}</header><div className="u5-panelbody">{children}</div>
  </section>;
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
  const listKnown=reports.isSuccess;
  const classificationsKnown=listKnown&&reports.data.every(r=>knownConsistency(r)!==undefined);
  const metrics=[
    {band:'01 / PLAN · TÀI KHOẢN',label:'Tổng tài khoản',tone:'mint' as const,value:listKnown?reports.data.length:undefined,note:'Tài khoản trong báo cáo Backend'},
    {band:'02 / REVIEW · NHẤT QUÁN',label:'Tài khoản có số dư khớp',tone:'sky' as const,value:classificationsKnown?reports.data.filter(r=>knownConsistency(r)===true).length:undefined,note:'Đối chiếu theo tám cấu phần'},
    {band:'03 / RECORD · XEM XÉT',label:'Tài khoản có sai lệch',tone:'apricot' as const,value:classificationsKnown?reports.data.filter(r=>knownConsistency(r)===false).length:undefined,note:'Cần kiểm tra, chưa kết luận sai phạm'},
  ];
  const selectedState=reconciliation.isSuccess?knownConsistency(reconciliation.data):undefined;
  const bannerError=activeId?reconciliation.isError:reports.isError;
  return <div className="rise-deep-page deep-admin-escrow admin-ops u5-approved-v12">
    <Container className="deep-inner u5-page">
      <div className="u5-back"><Link to="/admin"><ArrowLeft size={15} aria-hidden="true"/> Trung tâm quản trị</Link><Button variant="outline" onClick={invalidate}><RefreshCcw size={15} aria-hidden="true"/> Làm mới</Button></div>
      <header className="u5-hero"><div><div className="u5-kicker">✦ 05 / SỔ QUỸ & ĐỐI SOÁT</div>
        <h1>Mỗi bút toán,<br/><em>một dấu vết rõ ràng.</em></h1>
        <p>Kiểm tra số dư, phát hiện sai lệch và truy vết bút toán theo từng chiến dịch. Mọi đề nghị phải qua cơ chế phê duyệt độc lập.</p>
        <span className="u5-pill">Sổ mô phỏng · Không chuyển tiền thật</span></div>
        <img className="u5-hero-art" src="/approved-ui/u5-hero.svg" width="190" height="185" alt="Minh họa sổ kế toán và khiên bảo vệ"/>
      </header>
      <div className={'u5-banner'+(bannerError?' u5-error':selectedState===false?' u5-mismatch':'')} role="status">
        <div className="u5-bannericon" aria-hidden="true">{bannerError?'!':selectedState===true?'✓':selectedState===false?'!':'?'}</div>
        <div><strong>{bannerError?'Không thể tải dữ liệu đối chiếu':!activeId?'Chọn tài khoản để kiểm tra số dư':reconciliation.isPending?'Đang đối chiếu tài khoản…':'Đối chiếu tài khoản đang chọn: '+reconciliationLabel(selectedState)}</strong>
        <p>Chỉ hiển thị kết quả Backend. API lỗi hoặc dữ liệu thiếu không đồng nghĩa số dư bằng 0 hoặc đã khớp.</p></div>
      </div>
      <section aria-label="Toàn cảnh hệ thống"><p className="u5-sectitle">01 / TOÀN CẢNH HỆ THỐNG</p><div className="u5-stats">
        {metrics.map(m=><EscrowPanel key={m.band} title={m.band} tone={m.tone} className="u5-stat"><div className="u5-muted">{m.label}</div><div className="u5-statval">{m.value??'—'}</div><div className="u5-muted">{m.value===undefined?'Chưa xác định':m.note}</div></EscrowPanel>)}
      </div></section>
      <section aria-label="Chọn tài khoản và kiểm tra số dư"><p className="u5-sectitle">02 / CHỌN TÀI KHOẢN VÀ KIỂM TRA SỐ DƯ</p><div className="u5-masterdetail">
        <EscrowPanel title="Danh sách tài khoản"><div className="u5-searchrow"><Input id="escrow-campaign" label="UUID chiến dịch" placeholder="Dán mã chiến dịch từ hồ sơ" value={campaignId} onChange={e=>setCampaignId(e.target.value)}/><Button variant="outline" onClick={()=>chooseCampaign(campaignId)}>Xem sổ</Button></div>
          {validation==='Mã chiến dịch phải là UUID hợp lệ.'?<p role="alert" className="u5-feedback u5-feedback-error">{validation}</p>:null}
          {reports.isPending?<StatusMessage title="Đang đối chiếu số liệu…"/>:null}
          {reports.isError?<StatusMessage error title="Không thể đối chiếu" detail={reports.error.message}/>:null}
          {reports.isSuccess&&!reports.data.length?<StatusMessage title="Chưa có tài khoản escrow để đối chiếu"/>:null}
          {reports.isSuccess?reports.data.map((r,i)=><button type="button" className={'u5-account'+(activeId===r.campaignId?' u5-chosen':'')} key={r.campaignId??i} disabled={!r.campaignId} aria-pressed={activeId===r.campaignId} onClick={()=>r.campaignId&&chooseCampaign(r.campaignId)}><span><strong>Chiến dịch {shortId(r.campaignId)}</strong><small>{r.ledgerEntryCount??'—'} bút toán · Chọn để đối chiếu</small></span><AdminState value={reconciliationState(knownConsistency(r))}>{reconciliationLabel(knownConsistency(r))}</AdminState></button>):null}
        </EscrowPanel>
        <EscrowPanel title="Escrow theo chiến dịch" tone="mint" action={<AdminState value={reconciliationState(selectedState)}>{reconciliationLabel(selectedState)}</AdminState>}>
          {activeId?<><h3 className="u5-account-title">Chiến dịch {shortId(activeId)}</h3><p className="u5-muted">Số dư đang lưu trong hệ thống · VND mô phỏng</p>
        {summary.isPending?<div className="mt-4"><StatusMessage title="Đang tải tài khoản…"/></div>:null}
        {summary.isError?<div className="mt-4"><StatusMessage error title="Không thể tải escrow" detail={summary.error.message}/></div>:null}
        {summary.isSuccess?<div className="u5-account-detail">
          <p className="text-xs font-semibold text-muted">Trạng thái tài khoản: {summary.data.escrowStatus??'Chưa xác định'}</p>
          <div className="u5-balances">
            {([['Số dư khả dụng',summary.data.availableBalance],['Tổng xác nhận',summary.data.totalConfirmedContributions],
              ['Đang dự trữ',summary.data.totalReserved],['Dự trữ hoàn tiền',summary.data.totalRefundReserved],
              ['Đã giải ngân',summary.data.totalDisbursed],['Đã hoàn tiền',summary.data.totalRefunded]] as const).map(([label,value])=><div className="u5-bal" key={label}><small>{label}</small><strong>{amountLabel(value,summary.data.currency)}</strong></div>)}
          </div>
        </div>:null}

          </>:<StatusMessage title="Chọn tài khoản hoặc nhập UUID để xem số dư"/>}
        </EscrowPanel>
      </div></section>
      <section className="u5-reconciliation" aria-label="Đối chiếu tám cấu phần"><p className="u5-sectitle">03 / ĐỐI CHIẾU TÁM CẤU PHẦN SỐ DƯ</p>
        <EscrowPanel title="Hệ thống lưu trữ ↔ Ledger tính toán" tone="mint" action={<AdminState value={reconciliationState(selectedState)}>{reconciliationLabel(selectedState)}</AdminState>}>
          {activeId?<>        {reconciliation.isPending?<p className="mt-4 text-xs text-muted">Đang đối chiếu tài khoản…</p>:null}
        {reconciliation.isError?<p role="alert" className="mt-4 text-sm text-danger">{reconciliation.error.message}</p>:null}
        {reconciliation.isSuccess?<div className="u5-selected-reconciliation">
          <div className="u5-result"><AdminState value={reconciliationState(knownConsistency(reconciliation.data))}>{reconciliationLabel(knownConsistency(reconciliation.data))}</AdminState><span>{reconciliation.data.ledgerEntryCount??'—'} bút toán</span></div>
          <ReconciliationBalances report={reconciliation.data}/>
        </div>:null}
<AdminChainPanel campaignId={activeId}/></>:<StatusMessage title="Chọn tài khoản để đối chiếu tám cấu phần"/>}
        </EscrowPanel>
      </section>
      <div className="u5-split">
        <section className="u5-ledger"><p className="u5-sectitle">04 / BÚT TOÁN LIÊN QUAN</p><EscrowPanel title="Sổ cái của tài khoản đang chọn" tone="sky">
          {activeId?<>        {ledger.isPending?<div className="mt-4"><StatusMessage title="Đang tải bút toán…"/></div>:null}
        {ledger.isError?<div className="mt-4"><StatusMessage error title="Không tải được bút toán" detail={ledger.error.message}/></div>:null}
        {ledger.isSuccess&&!ledger.data.data?.length?<div className="mt-4"><StatusMessage title="Chưa có bút toán"/></div>:null}
        <div className="mt-4 space-y-3">{ledger.data?.data?.map((item,index)=><div key={item.id} className="u5-entry">
          <span className="u5-entrynum" aria-hidden="true">{ledgerPage*10+index+1}</span>
          <div><p className="text-sm font-bold text-brand-ink">{item.transactionType}</p><p className="mt-1 text-xs text-muted">{item.transactionCode??shortId(item.id)} · {dateLabel(item.createdAt)}</p><p className="u5-under">Trạng thái: {item.status==='COMPLETED'?'Đã ghi sổ mô phỏng':'Chưa xác định'}</p></div>
          <p className="u5-value">{item.direction} {amountLabel(item.amount,item.currency)}</p>
        </div>)}</div>
        {ledger.isSuccess?<RecordPagination page={ledgerPage} pages={ledger.data.totalPages??0} onChange={setLedgerPage}/>:null}
</>:<StatusMessage title="Chọn tài khoản để xem bút toán"/>}
        </EscrowPanel></section>
        <section className="u5-actions"><p className="u5-sectitle">05 / HÀNH ĐỘNG KIỂM SOÁT</p><EscrowPanel title="Đề nghị điều chỉnh / tất toán" tone="apricot">
          {activeId?<>        <form className="u5-form" onSubmit={e=>void submit(e)}>
          <p className="u5-infonote u5-full">Đề xuất không phải đã áp dụng. Quyết định duyệt điều chỉnh/tất toán thực hiện qua hàng chờ Admin, với kiểm tra tách người tạo và người duyệt của Backend.</p>
          <div className="u5-field u5-full"><label htmlFor="escrow-proposal-type" className="mb-2 block text-sm font-semibold">Loại đề xuất</label>
            <select id="escrow-proposal-type" value={kind} className="min-h-11 w-full rounded-xl border border-border bg-surface px-3" onChange={e=>{setKind(e.target.value as 'adjustment'|'settlement');setAck(false);}}>
              <option value="adjustment">Điều chỉnh sổ</option><option value="settlement">Tất toán mô phỏng</option>
            </select>
          </div>
          {kind==='adjustment'?<>
            <div className="u5-field"><label htmlFor="escrow-direction" className="mb-2 block text-sm font-semibold">Chiều điều chỉnh</label><select id="escrow-direction" className="min-h-11 w-full rounded-xl border border-border bg-surface px-3" value={direction} onChange={e=>setDirection(e.target.value as 'CREDIT'|'DEBIT'|'NEUTRAL')}>
              <option value="CREDIT">CREDIT</option><option value="DEBIT">DEBIT</option><option value="NEUTRAL">NEUTRAL</option>
            </select></div>
            <Input id="escrow-amount" label="Giá trị điều chỉnh (VND mô phỏng)" required type="number" step="1" min="1" value={amount} onChange={e=>setAmount(e.target.value)}/>
          </>:null}
          <div className="u5-field u5-full"><label className="mb-2 block text-sm font-semibold" htmlFor="escrow-reason">Lý do</label><textarea id="escrow-reason" rows={3} maxLength={2000} required className="w-full rounded-xl border border-border bg-surface p-3 text-sm" value={reason} onChange={e=>setReason(e.target.value)}/></div>
          <label className="u5-ack u5-full"><input type="checkbox" className="mt-1 accent-brand" checked={ack} onChange={e=>setAck(e.target.checked)}/> Xác nhận đây là đề nghị cho sổ mô phỏng, không phải ghi nhận thanh toán thật.</label>
          {validation&&validation!=='Mã chiến dịch phải là UUID hợp lệ.'?<p role="alert" className="u5-feedback u5-feedback-error">{validation}</p>:null}
          {propose.isError?<p role="alert" className="text-sm text-danger">{propose.error.message}</p>:null}
          <Button className="u5-full" type="submit" loading={propose.isPending} disabled={!ack}>Tạo đề nghị (chờ duyệt)</Button>
        </form></>:<StatusMessage title="Chọn tài khoản trước khi tạo đề nghị"/>}
        </EscrowPanel></section>
      </div>
      <section className="u5-history"><p className="u5-sectitle">06 / LỊCH SỬ ĐỐI SOÁT TOÀN HỆ THỐNG</p><EscrowPanel title="Các lần chạy đối soát" action={<Button disabled={execute.isPending} loading={execute.isPending} onClick={()=>{if(window.confirm('Tạo một lượt đối soát toàn hệ thống mô phỏng?'))execute.mutate();}}>Tạo lượt đối soát</Button>}>
      {execute.isError?<p className="mt-3 text-sm text-danger" role="alert">{execute.error.message}</p>:null}
      {notice?<p className="mt-4 rounded-xl bg-soft-mint p-4 text-sm text-brand-ink" role="status">{notice}</p>:null}
      {runs.isPending?<div className="mt-5"><StatusMessage title="Đang tải lịch sử…"/></div>:null}
      {runs.isError?<div className="mt-5"><StatusMessage error title="Không tải được lịch sử" detail={runs.error.message}/></div>:null}
      {runs.isSuccess&&!runs.data.data?.length?<div className="mt-5"><StatusMessage title="Chưa có lượt đối soát"/></div>:null}
      <div className="mt-5 space-y-3">{runs.data?.data?.map(item=><article key={item.id} className="u5-historyrow">
        <div><h3 className="text-sm font-bold text-brand-ink">{item.runCode??shortId(item.id)} · {item.runStatus??'Chưa có trạng thái'}</h3><p className="mt-2 text-xs text-muted">{dateLabel(item.startedAt)} · Kiểm tra {item.accountsChecked??'—'} tài khoản · Sai lệch {item.inconsistentAccounts??'—'}</p></div>
        {item.id?<Button variant="outline" onClick={()=>setRunId(item.id!)}>Xem báo cáo</Button>:null}
      </article>)}</div>
      {runs.isSuccess?<RecordPagination page={runPage} pages={runs.data.totalPages??0} onChange={setRunPage}/>:null}
      {runId?<div className="u5-runinfo min-w-0 max-w-full">
        <h3 className="text-lg font-bold text-brand-ink">Báo cáo lượt đối soát {shortId(runId)}</h3>
        {selectedRun.isPending?<p className="mt-2 text-sm text-muted">Đang tải…</p>:null}
        {selectedRun.isError?<p role="alert" className="mt-3 text-sm text-danger">{selectedRun.error.message}</p>:null}
        {selectedRun.isSuccess?<><p className="mt-3 text-sm font-semibold">{selectedRun.data.runStatus} · {selectedRun.data.accountsChecked??'—'} tài khoản · {selectedRun.data.inconsistentAccounts??'—'} sai lệch</p>
          {selectedRun.data.errorMessage?<p className="mt-2 text-sm text-danger">{selectedRun.data.errorMessage}</p>:null}
          {selectedRun.data.findings?.map((f,index)=><div key={f.escrowAccountId??index} className="mt-5 min-w-0 max-w-full u5-runfinding">
            <div className="u5-runfinding-heading"><p className="font-bold text-brand-ink">Chiến dịch {shortId(f.campaignId)}</p><AdminState value={reconciliationState(knownConsistency(f))}>{reconciliationLabel(knownConsistency(f))}</AdminState></div>
            <ReconciliationBalances report={f}/>
          </div>)}
        </>:null}
      </div>:null}

      </EscrowPanel></section>
      <p className="u5-footnote"><ShieldAlert size={20} aria-hidden="true"/> Nếu báo cáo đối soát có sai lệch, không diễn giải số liệu như đã khớp và không coi việc tạo đề nghị là hoàn tất nghiệp vụ.</p>
    </Container>
  </div>;
}
