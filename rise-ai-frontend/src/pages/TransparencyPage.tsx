import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { ArrowLeft, ExternalLink, RefreshCw, ShieldCheck } from 'lucide-react';
import {
  getChainActivity, getChainAnchors, getChainConfig, getChainReserves,
  isFuji, safeSnowtrace, viChain,
} from '@/api/transparency';
import '../components/ui/onchain-views.css';

const REFRESH_MS=30_000;
const short=(text:string|null|undefined)=>text?(text.length>30?text.slice(0,16)+'…'+text.slice(-10):text):'Chưa có dữ liệu';
const date=(value:string|null|undefined)=>value?new Date(value).toLocaleString('vi-VN'):'Chưa có dữ liệu';
const vnd=(amount:number|null|undefined)=>amount==null?'Chưa có dữ liệu':new Intl.NumberFormat('vi-VN').format(amount)+' VND mô phỏng';
function Explorer({url}:{url:string|null|undefined}){
  const link=safeSnowtrace(url);
  return link?<a className="chain-link" href={link} target="_blank" rel="noopener noreferrer">Xem Snowtrace <ExternalLink size={14} aria-hidden="true"/></a>:null;
}
export function TransparencyPage(){
  const [tokens,setTokens]=useState<(string|null)[]>([null]);
  const token=tokens[tokens.length-1]??null;
  const chain=useQuery({queryKey:['transparency','chain'],queryFn:getChainConfig,refetchInterval:REFRESH_MS});
  const enabled=isFuji(chain.data);
  const anchors=useQuery({queryKey:['transparency','anchors'],queryFn:getChainAnchors,enabled,refetchInterval:REFRESH_MS});
  const reserves=useQuery({queryKey:['transparency','reserves'],queryFn:getChainReserves,enabled,refetchInterval:REFRESH_MS});
  const activity=useQuery({queryKey:['transparency','activity',token],queryFn:()=>getChainActivity(token),enabled,refetchInterval:REFRESH_MS});
  return <div className="chain-public-page"><div className="chain-container">
    <Link to="/campaigns" className="chain-link"><ArrowLeft size={15} aria-hidden="true"/> Trở về chiến dịch</Link>
    <header className="chain-public-hero">
      <p className="chain-eyebrow">MINH BẠCH / DỮ LIỆU BLOCKCHAIN</p>
      <h1>Đối chiếu on-chain</h1>
      <p>Đối chiếu hợp đồng ký quỹ, dấu ghi lịch sử, chứng thực số dư và giao dịch Avalanche Data API. Tất cả số tiền là mô phỏng; đây không phải xác nhận thanh toán bằng tiền thật.</p>
      <span className="chain-badge"><ShieldCheck size={16} aria-hidden="true"/> Chỉ đọc · Không kết nối ví trong ứng dụng</span>
    </header>
    {chain.isPending?<p className="chain-panel">Đang xác minh kết nối chain với Backend…</p>:null}
    {chain.isError?<div className="chain-panel chain-error" role="alert">Không tải được cấu hình chain: {chain.error.message}
      <button type="button" onClick={()=>void chain.refetch()}>Thử lại</button></div>:null}
    {chain.data&&!enabled?<div className="chain-panel" role="status"><h2>Chưa thể công bố dữ liệu Fuji</h2>
      <p>Backend hiện {chain.data.onChain?'không kết nối được Fuji hoặc đang chạy mạng khác':'sử dụng ký quỹ mô phỏng (off-chain)'}. Không tạo dữ liệu blockchain hoặc liên kết Snowtrace giả.</p>
    </div>:null}
    <div className="chain-public-grid">
      <section className="chain-panel">
        <p className="chain-eyebrow">01 · HỢP ĐỒNG</p><h2>Trạng thái ký quỹ</h2>
        <p className="chain-public-value">{enabled?'Avalanche Fuji · Chain ID 43113':chain.isPending?'Đang tải…':'Chưa xác minh Fuji'}</p>
        {enabled&&chain.data?<><div className="chain-public-row"><strong>Hợp đồng ký quỹ</strong>{short(chain.data.escrowContract)}</div>
          <div className="chain-public-row"><strong>Token mô phỏng</strong>{short(chain.data.tokenContract)}</div>
          <div className="chain-public-row"><strong>Hợp đồng ghi dấu</strong>{short(chain.data.anchorContract)}</div>
          <div className="chain-public-row"><strong>Trạng thái hợp đồng</strong>{chain.data.paused===true?'Đang tạm dừng':chain.data.paused===false?'Đang hoạt động':'Chưa có dữ liệu'}</div>
          <p className="chain-public-foot">Guardian và người nhận xác nhận qua công cụ ngoài ứng dụng. Rise AI không thao tác ví.</p>
        </>:null}
      </section>
      <section className="chain-panel">
        <p className="chain-eyebrow">02 · DỰ TRỮ</p><h2>Chứng thực số dư</h2>
        {!enabled?<p className="chain-muted">Chỉ hiển thị khi Backend xác minh đang hoạt động trên Fuji.</p>:null}
        {enabled&&reserves.isPending?<p>Đang lấy dữ liệu Backend…</p>:null}
        {enabled&&reserves.isError?<p className="chain-error" role="alert">{reserves.error.message}</p>:null}
        {enabled&&reserves.data&&!reserves.data.available?<p className="chain-muted">Chưa thể lấy dữ liệu chứng thực.</p>:null}
        {enabled&&reserves.data?.available&&!reserves.data.attested?<p className="chain-muted">Chưa có chứng thực số dư từ Guardian.</p>:null}
        {enabled&&reserves.data?.attested?<><div className="chain-public-row"><strong>Ngân hàng theo chứng thực</strong>{vnd(reserves.data.bankBalance)}</div>
          <div className="chain-public-row"><strong>Ký quỹ tại thời điểm chứng thực</strong>{vnd(reserves.data.heldAtAttestation)}</div>
          <div className="chain-public-row"><strong>Chênh lệch</strong>{vnd(reserves.data.gap)}</div>
          <div className="chain-public-row"><strong>Ngày xác nhận</strong>{date(reserves.data.attestedAt)}</div>
        </>:null}
      </section>
      <section className="chain-panel">
        <p className="chain-eyebrow">03 · BẰNG CHỨNG</p><h2>Dấu ghi lịch sử</h2>
        {enabled&&anchors.isPending?<p className="chain-muted">Đang đối chiếu với Backend…</p>:null}
        {enabled&&anchors.isError?<p className="chain-error" role="alert">{anchors.error.message}</p>:null}
        {enabled&&anchors.data&&!anchors.data.anchors?.length?<p className="chain-muted">Chưa có mốc ghi dấu để đối chiếu.</p>:null}
        {enabled&&anchors.data?.anchors?.map(a=><div className="chain-public-row" key={a.kind}>
          <strong>{a.kind} · {viChain(a.status)}</strong>
          <span>{a.anchoredRecordCount??0} bản ghi được ghi dấu / {a.currentRecordCount??0} hiện tại</span>
          <p>{a.anchoredAt?date(a.anchoredAt):'Chưa ghi dấu'}</p><Explorer url={a.explorerUrl}/>
        </div>)}
        {!enabled?<p className="chain-muted">Chưa có đối chiếu on-chain khi chạy mô phỏng.</p>:null}
      </section>
      <section className="chain-panel">
        <p className="chain-eyebrow">04 · XÁC MINH</p><h2>Trạng thái và nguyên tắc</h2>
        <div className="chain-public-row"><strong>Đã đối chiếu</strong>Chỉ dùng khi hash và dữ liệu Backend khớp.</div>
        <div className="chain-public-row"><strong>Cần xem xét</strong>MISMATCH hoặc giao dịch PLATFORM không có trong sổ.</div>
        <div className="chain-public-row"><strong>Không thể xác minh</strong>Khi Data API hoặc Gateway không khả dụng.</div>
        <p className="chain-public-foot">Không coi dữ liệu không khả dụng là bằng chứng sai phạm.</p>
      </section>
    </div>
    <section className="chain-panel chain-public-full">
      <div className="chain-heading"><div><p className="chain-eyebrow">05 · DATA API</p><h2>Lịch sử giao dịch đối chiếu</h2></div>
        <button type="button" className="chain-refresh" disabled={!enabled} onClick={()=>void activity.refetch()}><RefreshCw size={15} aria-hidden="true"/> Làm mới</button></div>
      {!enabled?<p className="chain-muted">Chưa có dữ liệu Avalanche Fuji để hiển thị.</p>:null}
      {enabled&&activity.isPending?<p className="chain-muted">Đang đọc Avalanche Data API qua Backend…</p>:null}
      {enabled&&activity.isError?<p className="chain-error" role="alert">Không thể tải lịch sử: {activity.error.message}</p>:null}
      {enabled&&activity.data&&!activity.data.available?<p className="chain-muted">Data API chưa khả dụng. Không hiển thị giao dịch giả.</p>:null}
      {enabled&&activity.data?.available&&!activity.data.items.length?<p className="chain-muted">Trang này chưa có giao dịch.</p>:null}
      {enabled&&activity.data?.items.map(x=><article key={x.txHash} className="chain-public-row">
        <strong>{x.method} · {viChain(x.check)}</strong>
        <span>{date(x.timestamp)} · Block #{x.blockNumber} · {x.success?'Thành công':'Thất bại'}</span>
        <p>{short(x.txHash)} · {x.rvndToBeneficiary!=null?vnd(x.rvndToBeneficiary):'Không có chuyển rVND xác định tới người nhận'}</p>
        <Explorer url={x.explorerUrl}/>
      </article>)}
      {enabled&&activity.data?.available?<nav className="chain-pagination" aria-label="Phân trang hoạt động">
        <button type="button" disabled={tokens.length===1} onClick={()=>setTokens(t=>t.slice(0,-1))}>Trang trước</button>
        <button type="button" disabled={!activity.data.nextPageToken} onClick={()=>{const next=activity.data?.nextPageToken;if(next)setTokens(t=>[...t,next]);}}>Trang sau</button>
      </nav>:null}
      <p className="chain-footnote">Làm mới sau mỗi 30 giây. Dữ liệu truy xuất từ Backend; mọi giao dịch token chỉ là mô phỏng.</p>
    </section>
  </div></div>;
}
