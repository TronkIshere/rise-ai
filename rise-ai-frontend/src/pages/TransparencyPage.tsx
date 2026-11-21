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
  return <div className="chain-public-page u4-approved-v11"><div className="chain-container">
    <Link to="/campaigns" className="chain-link"><ArrowLeft size={15} aria-hidden="true"/> Trở về chiến dịch</Link>
    <header className="chain-public-hero">
      <div className="u4-hero-copy">
      <div className="u4-kicker">✦ 04 / MINH BẠCH VÀ ĐỐI CHIẾU</div>
      <h1>Mỗi dữ liệu,<br/><em>một cơ sở xác minh.</em></h1>
      <p>Tra cứu tình trạng ký quỹ, chứng thực số dư, dấu ghi lịch sử và kết quả đối chiếu. Phân biệt dữ liệu đã xác minh với thông tin chưa khả dụng.</p>
      <span className="chain-badge"><ShieldCheck size={16} aria-hidden="true"/> Chỉ đọc · Không kết nối ví trong ứng dụng</span>
      </div>
      <div className="u4-hero-art"><img src="/approved-ui/u4-hero.svg" width="230" height="210" alt="Minh họa sổ ghi chép và khiên xác minh"/></div>
    </header>
    {chain.isPending?<div className="chain-panel u4-state-banner" role="status"><span className="u4-state-icon" aria-hidden="true">▤</span><p>Đang xác minh kết nối chain với Backend…</p></div>:null}
    {chain.isError?<div className="chain-panel chain-error u4-state-banner u4-state-error" role="alert"><span className="u4-state-icon" aria-hidden="true">!</span><div>Không tải được cấu hình chain: {chain.error.message}
      <button type="button" onClick={()=>void chain.refetch()}>Thử lại</button></div></div>:null}
    {chain.data&&!enabled?<div className={'chain-panel u4-state-banner'+(chain.data.onChain?' u4-state-warn':'')} role="status"><span className="u4-state-icon" aria-hidden="true">▤</span><div><h2>Chưa thể công bố dữ liệu Fuji</h2>
      <p>Backend hiện {chain.data.onChain?'không kết nối được Fuji hoặc đang chạy mạng khác':'sử dụng ký quỹ mô phỏng (off-chain)'}. Không tạo dữ liệu blockchain hoặc liên kết Snowtrace giả.</p>
    </div></div>:null}
    <div className="u4-section-label">01—04 / NGUỒN DỮ LIỆU VÀ QUY TẮC</div>
    <div className="chain-public-grid">
      <section className="chain-panel u4-card u4-mint">
        <div className="u4-card-band"><span>01 / PLAN · HỢP ĐỒNG</span><span aria-hidden="true">▧</span></div>
        <div className="u4-card-body"><h2>Trạng thái ký quỹ</h2>
        {!enabled?<div className="u4-infobox"><strong>{chain.isPending?'Đang tải…':chain.isError?'Không thể xác minh Fuji':'Chưa xác minh Fuji'}</strong><p>{chain.isPending?'Đang xác minh kết nối chain với Backend…':chain.isError?'Không tải được cấu hình chain. Chưa thể xác minh hợp đồng và mạng blockchain.':'Hợp đồng và mạng blockchain chưa được công bố ở trạng thái hiện tại.'}</p></div>:null}
        {enabled?<p className="chain-public-value">Avalanche Fuji · Chain ID 43113</p>:null}
        {enabled&&chain.data?<><div className="chain-public-row"><strong>Hợp đồng ký quỹ</strong><span>{short(chain.data.escrowContract)}</span></div>
          <div className="chain-public-row"><strong>Token mô phỏng</strong><span>{short(chain.data.tokenContract)}</span></div>
          <div className="chain-public-row"><strong>Hợp đồng ghi dấu</strong><span>{short(chain.data.anchorContract)}</span></div>
          <div className="chain-public-row"><strong>Trạng thái hợp đồng</strong><span>{chain.data.paused===true?'Đang tạm dừng':chain.data.paused===false?'Đang hoạt động':'Chưa có dữ liệu'}</span></div>
        </>:null}
          <p className="chain-public-foot">Guardian và người nhận xác nhận qua công cụ ngoài ứng dụng. Rise AI không thao tác ví.</p>
        </div>
      </section>
      <section className="chain-panel u4-card u4-apricot">
        <div className="u4-card-band"><span>02 / RECORD · DỰ TRỮ</span><span aria-hidden="true">◇</span></div>
        <div className="u4-card-body"><h2>Chứng thực số dư</h2>
        {!enabled?<div className="u4-infobox"><strong>{chain.isPending?'Đang xác minh kết nối…':chain.isError||chain.data?.onChain?'Chưa thể xác minh chứng thực':'Chưa có chứng thực số dư'}</strong><p>Chỉ hiển thị khi Backend xác minh đang hoạt động trên Fuji.</p></div>:null}
        {enabled&&reserves.isPending?<p>Đang lấy dữ liệu Backend…</p>:null}
        {enabled&&reserves.isError?<p className="chain-error" role="alert">{reserves.error.message}</p>:null}
        {enabled&&reserves.data&&!reserves.data.available?<p className="chain-muted">Chưa thể lấy dữ liệu chứng thực.</p>:null}
        {enabled&&reserves.data?.available&&!reserves.data.attested?<p className="chain-muted">Chưa có chứng thực số dư từ Guardian.</p>:null}
        {enabled&&reserves.data?.attested?<><div className="chain-public-row"><strong>Ngân hàng theo chứng thực</strong><span>{vnd(reserves.data.bankBalance)}</span></div>
          <div className="chain-public-row"><strong>Ký quỹ tại thời điểm chứng thực</strong><span>{vnd(reserves.data.heldAtAttestation)}</span></div>
          <div className="chain-public-row"><strong>Chênh lệch</strong><span>{vnd(reserves.data.gap)}</span></div>
          <div className="chain-public-row"><strong>Ngày xác nhận</strong><span>{date(reserves.data.attestedAt)}</span></div>
        </>:null}
        </div>
      </section>
      <section className="chain-panel u4-card u4-sky">
        <div className="u4-card-band"><span>03 / REVIEW · BẰNG CHỨNG</span><span aria-hidden="true">♧</span></div>
        <div className="u4-card-body"><h2>Dấu ghi lịch sử</h2>
        {enabled&&anchors.isPending?<p className="chain-muted">Đang đối chiếu với Backend…</p>:null}
        {enabled&&anchors.isError?<p className="chain-error" role="alert">{anchors.error.message}</p>:null}
        {enabled&&anchors.data&&!anchors.data.anchors?.length?<p className="chain-muted">Chưa có mốc ghi dấu để đối chiếu.</p>:null}
        {enabled&&anchors.data?.anchors?.map(a=><div className="chain-public-row u4-anchor-row" key={a.kind}>
          <div className="u4-anchor-summary"><strong>{a.kind} · {a.anchoredRecordCount??0}/{a.currentRecordCount??0} bản ghi</strong>
          <span className={'u4-tag'+(a.status==='CONSISTENT'?'':a.status==='MISMATCH'?' u4-tag-coral':a.status==='NOT_ANCHORED'?' u4-tag-amber':' u4-tag-sky')}>{viChain(a.status)}</span></div>
          <p>{a.anchoredRecordCount??0} bản ghi được ghi dấu / {a.currentRecordCount??0} hiện tại</p>
          <p>{a.anchoredAt?date(a.anchoredAt):'Chưa ghi dấu'}</p><Explorer url={a.explorerUrl}/>
        </div>)}
        {!enabled?<div className="u4-infobox"><strong>{chain.isPending?'Đang xác minh kết nối…':chain.isError||chain.data?.onChain?'Chưa thể xác minh dấu ghi':'Chưa có dấu ghi on-chain'}</strong><p>{chain.isPending||chain.isError||chain.data?.onChain?'Chưa thể đối chiếu với Backend.':'Chưa có đối chiếu on-chain khi chạy mô phỏng.'}</p></div>:null}
        </div>
      </section>
      <section className="chain-panel u4-card u4-sage">
        <div className="u4-card-band"><span>04 / REVIEW · XÁC MINH</span><span aria-hidden="true">✳</span></div>
        <div className="u4-card-body"><h2>Hiểu đúng các trạng thái</h2>
        <div className="u4-rules">
        <div className="u4-rule"><span className="u4-tag">Khớp</span><div><strong>Đã đối chiếu</strong><p>Chỉ dùng khi hash và dữ liệu Backend khớp.</p></div></div>
        <div className="u4-rule"><span className="u4-tag u4-tag-amber">Xem xét</span><div><strong>Cần xem xét</strong><p>MISMATCH hoặc giao dịch PLATFORM không có trong sổ.</p></div></div>
        <div className="u4-rule"><span className="u4-tag u4-tag-sky">Chưa rõ</span><div><strong>Không thể xác minh</strong><p>Khi Data API hoặc Gateway không khả dụng.</p></div></div>
        </div>
        <p className="chain-public-foot u4-rules-foot">Không coi dữ liệu không khả dụng là bằng chứng sai phạm.</p>
        </div>
      </section>
    </div>
    <section className="chain-panel chain-public-full">
      <div className="chain-heading"><div><p className="chain-eyebrow">05 / DATA API · NHẬT KÝ ĐỐI CHIẾU</p><h2>Lịch sử giao dịch</h2></div>
        <button type="button" className="chain-refresh" disabled={!enabled} onClick={()=>void activity.refetch()}><RefreshCw size={15} aria-hidden="true"/> Làm mới</button></div>
      <div className="u4-history-main">
      {!enabled?<div className="u4-empty"><div className={'u4-empty-icon'+(chain.isError?' u4-empty-error':'')} aria-hidden="true">{chain.isError?'!':'▤'}</div><h3>{chain.isPending?'Đang xác minh kết nối…':chain.isError?'Không thể tải dữ liệu Fuji':chain.data?.onChain?'Dữ liệu Fuji chưa khả dụng':'Chưa có giao dịch Fuji để hiển thị'}</h3><p className="chain-muted">{chain.isPending?'Đang xác minh kết nối chain với Backend…':chain.isError?'Không tải được cấu hình chain. Có thể thử lại ở thông báo phía trên.':chain.data?.onChain?'Chưa thể xác minh mạng Fuji từ Backend.':'Chưa có dữ liệu Avalanche Fuji để hiển thị.'}</p></div>:null}
      {enabled&&activity.isPending?<p className="chain-muted">Đang đọc Avalanche Data API qua Backend…</p>:null}
      {enabled&&activity.isError?<p className="chain-error" role="alert">Không thể tải lịch sử: {activity.error.message}</p>:null}
      {enabled&&activity.data&&!activity.data.available?<p className="chain-muted">Data API chưa khả dụng. Không hiển thị giao dịch giả.</p>:null}
      {enabled&&activity.data?.available&&!activity.data.items.length?<p className="chain-muted">Trang này chưa có giao dịch.</p>:null}
      {enabled&&activity.data?.items.map((x,index)=><article key={x.txHash} className="chain-public-row u4-transaction">
        <span className="u4-tx-number" aria-hidden="true">{index+1}</span><div className="u4-tx-description">
        <strong>{x.method} · {viChain(x.check)}</strong>
        <span>{date(x.timestamp)} · Block #{x.blockNumber} · {x.success?'Thành công':'Thất bại'}</span>
        <p>{short(x.txHash)} · {x.rvndToBeneficiary!=null?vnd(x.rvndToBeneficiary):'Không có chuyển rVND xác định tới người nhận'}</p>
        <Explorer url={x.explorerUrl}/></div>
      </article>)}
      {enabled&&activity.data?.available?<nav className="chain-pagination" aria-label="Phân trang hoạt động">
        <button type="button" disabled={tokens.length===1} onClick={()=>setTokens(t=>t.slice(0,-1))}>Trang trước</button>
        <button type="button" disabled={!activity.data.nextPageToken} onClick={()=>{const next=activity.data?.nextPageToken;if(next)setTokens(t=>[...t,next]);}}>Trang sau</button>
      </nav>:null}
      </div>
      <p className="chain-footnote">Làm mới sau mỗi 30 giây. Dữ liệu truy xuất từ Backend; mọi giao dịch token chỉ là mô phỏng.</p>
    </section>
  </div></div>;
}
