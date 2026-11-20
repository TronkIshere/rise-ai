import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { ExternalLink, RefreshCw, ShieldCheck } from 'lucide-react';
import { getCampaignChain, getChainConfig, isFuji, remaining, safeSnowtrace, viChain } from '@/api/transparency';
import type { CampaignChain, ChainDisbursement } from '@/api/transparency';
import '../components/ui/onchain-views.css';

const REFRESH_MS=30_000;
const date=(s:string|null|undefined)=>s?new Date(s).toLocaleString('vi-VN'):'Chưa có dữ liệu';
const short=(s:string)=>s.length>25?s.slice(0,12)+'…'+s.slice(-8):s;
function useClock(){
  const [now,setNow]=useState(()=>Date.now());
  useEffect(()=>{const id=window.setInterval(()=>setNow(Date.now()),REFRESH_MS);return()=>window.clearInterval(id);},[]);
  return now;
}
export function ChainBadge(){
  const query=useQuery({queryKey:['transparency','chain'],queryFn:getChainConfig,staleTime:REFRESH_MS,refetchInterval:REFRESH_MS});
  if(query.isPending)return <span className="chain-badge chain-badge-muted">Đang kiểm tra ký quỹ…</span>;
  if(query.isError)return <span className="chain-badge chain-badge-muted">Chưa xác minh trạng thái ký quỹ</span>;
  if(isFuji(query.data))return <span className="chain-badge"><ShieldCheck size={15} aria-hidden="true"/> Ký quỹ on-chain · Avalanche Fuji</span>;
  return <span className="chain-badge chain-badge-muted">Ký quỹ mô phỏng · Không phải Fuji</span>;
}
function ChainExplorer({url,label}:{url?:string|null;label:string}){
  const href=safeSnowtrace(url);
  return href?<a href={href} target="_blank" rel="noopener noreferrer" className="chain-link">{label}<ExternalLink size={14} aria-hidden="true"/></a>:null;
}
function DisbursementItem({item,baseUrl,now}:{item:ChainDisbursement;baseUrl?:string|null;now:number}){
  const isWarning=['FAILED','BLOCKED_BY_DISPUTE','HELD_BY_GUARDIAN'].includes(item.chainStatus);
  const tx=item.releaseTxHash||item.queueTxHash||item.reserveTxHash||item.payoutTxHash;
  const url=baseUrl && tx?baseUrl+tx:null;
  const time=remaining(item.releaseAt,now);
  const semanticTone=isWarning?'review':item.chainStatus==='RELEASED_ON_CHAIN'||item.chainStatus==='RELEASED'?'record':'plan';
  return <li className="chain-flow-step" data-semantic={semanticTone}>
    <div className="chain-flow-step-top">
      <strong>{item.requestCode||'Yêu cầu '+short(item.disbursementId)}</strong>
      <span className={isWarning?'chain-state chain-state-warning':'chain-state'}>{viChain(item.chainStatus)}</span>
    </div>
    {time?<p className="chain-flow-timer" role="status">{time}</p>:null}
    {item.releaseAt?<p>Thời điểm mở giải ngân: {date(item.releaseAt)}</p>:null}
    {item.openGuardianHolds!=null?<p>Guardian hold đang mở: {item.openGuardianHolds}</p>:null}
    {item.receiptConfirmedAt?<p className="chain-flow-received">Người nhận đã xác nhận: {date(item.receiptConfirmedAt)}</p>:null}
    {tx?<ChainExplorer url={url} label="Kiểm tra giao dịch trên Snowtrace"/>:null}
  </li>;
}
function ChainContent({campaign,now,admin=false}:{campaign:CampaignChain;now:number;admin?:boolean}){
  if(!campaign.onChain)return <p className="chain-muted">Chiến dịch đang dùng sổ ký quỹ mô phỏng, chưa có lịch sử giao dịch on-chain.</p>;
  const needReview=campaign.disbursements.filter(x=>['FAILED','BLOCKED_BY_DISPUTE','HELD_BY_GUARDIAN'].includes(x.chainStatus)).length;
  return <>
    <p className="chain-muted">Mở chiến dịch: {viChain(campaign.openStatus)} · {campaign.disbursements.length} yêu cầu giải ngân</p>
    {admin?<p className="chain-summary">Cần xem xét: <strong>{needReview}</strong> · Dữ liệu đọc từ Backend, không tự ra lệnh trên chain.</p>:null}
    {campaign.disbursements.length?
      <ol className="chain-flow-list">{campaign.disbursements.map(item=><DisbursementItem key={item.disbursementId} item={item} baseUrl={campaign.explorerTxUrl} now={now}/>)}</ol>:
      <p className="chain-muted">Chưa có yêu cầu giải ngân on-chain.</p>}
    {admin&&campaign.transactions.length?<div className="chain-transactions"><h4>Nhật ký lệnh on-chain</h4>
      {campaign.transactions.slice(-8).reverse().map((tx,index)=><div key={tx.txHash||tx.action+'-'+index} className="chain-transaction">
        <span>{tx.action} · {viChain(tx.status)} · {date(tx.recordedAt)}</span>
        <ChainExplorer url={tx.explorerUrl} label="Snowtrace"/>
      </div>)}
    </div>:null}
  </>;
}
export function CampaignChainPanel({campaignId}:{campaignId:string}){
  const now=useClock();
  const query=useQuery({queryKey:['transparency','campaign',campaignId],queryFn:()=>getCampaignChain(campaignId),enabled:Boolean(campaignId),refetchInterval:REFRESH_MS});
  return <section className="chain-panel" aria-label="Dòng thời gian giải ngân on-chain">
    <div className="chain-heading"><div><p className="chain-eyebrow">MINH BẠCH GIẢI NGÂN</p><h2>Hành trình giải ngân trên chain</h2></div>
      <Link to="/transparency" className="chain-link">Đối chiếu on-chain</Link></div>
    {query.isPending?<p className="chain-muted">Đang tải trạng thái từ Backend…</p>:null}
    {query.isError?<p role="alert" className="chain-error">Không thể lấy trạng thái on-chain. <button type="button" onClick={()=>void query.refetch()}>Thử lại</button></p>:null}
    {query.data?<ChainContent campaign={query.data} now={now}/>:null}
    <p className="chain-footnote">Tự làm mới mỗi 30 giây · Số tiền và giải ngân trên Rise AI đều là mô phỏng.</p>
  </section>;
}
export function AdminChainPanel({campaignId}:{campaignId:string}){
  const now=useClock();
  const query=useQuery({queryKey:['transparency','campaign',campaignId],queryFn:()=>getCampaignChain(campaignId),enabled:Boolean(campaignId),refetchInterval:REFRESH_MS});
  if(!campaignId)return null;
  return <section className="chain-panel chain-panel-admin" aria-label="Lệnh on-chain của chiến dịch">
    <div className="chain-heading"><div><p className="chain-eyebrow">QUẢN TRỊ / ON-CHAIN</p><h3>Lệnh và thời gian chờ on-chain</h3></div>
      <button className="chain-refresh" type="button" onClick={()=>void query.refetch()}><RefreshCw size={15} aria-hidden="true"/> Làm mới</button></div>
    {query.isPending?<p className="chain-muted">Đang tải nhật ký lệnh…</p>:null}
    {query.isError?<p role="alert" className="chain-error">Không tải được lệnh từ Backend: {query.error.message}</p>:null}
    {query.data?<ChainContent campaign={query.data} now={now} admin/>:null}
    <p className="chain-footnote">Chỉ theo dõi. Các quyết định xét duyệt vẫn do Backend quản lý.</p>
  </section>;
}
export function DonationReceiptNote({code}:{code?:string}){
  return <div className="chain-receipt" aria-label="Biên nhận đóng góp">
    <strong>Biên nhận đóng góp mô phỏng</strong>
    <p>Mã khoản đóng góp: {code||'Chưa có mã'}. Chưa có biên nhận on-chain riêng cho khoản này từ Backend; không suy diễn giao dịch giải ngân thành biên nhận quyên góp.</p>
    <Link className="chain-link" to="/transparency">Xem đối chiếu chung</Link>
  </div>;
}
