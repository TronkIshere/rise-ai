import { apiRequest } from './client';

export interface ChainConfig {
  onChain: boolean; available: boolean; network?: string | null; chainId?: number | null;
  escrowContract?: string | null; tokenContract?: string | null; anchorContract?: string | null;
  contractVersion?: string | null; paused?: boolean | null; tokenKind?: string | null;
  guardianAddress?: string | null; guardianHoldMaxSeconds?: number | null;
  explorerTxUrl?: string | null; explorerAddressUrl?: string | null;
}
export interface ChainDisbursement {
  disbursementId: string; requestCode?: string | null; requestStatus?: string | null;
  chainStatus: string; releaseAt?: string | null; reserveTxHash?: string | null;
  queueTxHash?: string | null; releaseTxHash?: string | null;
  openGuardianHolds?: number | null; payoutTxHash?: string | null;
  receiptConfirmedAt?: string | null;
}
export interface ChainTransaction {
  action: string; referenceId?: string | null; status: string;
  txHash?: string | null; explorerUrl?: string | null; recordedAt?: string | null;
}
export interface CampaignChain {
  onChain: boolean; campaignId: string; openStatus: string;
  explorerTxUrl?: string | null; disbursements: ChainDisbursement[];
  transactions: ChainTransaction[];
}
export interface ChainAnchor {
  kind: string; status: string; anchoredRoot?: string | null;
  anchoredRecordCount?: number | null; anchoredAt?: string | null;
  recomputedRoot?: string | null; currentRecordCount?: number | null;
  txHash?: string | null; explorerUrl?: string | null;
}
export interface ChainAnchors { onChain: boolean; anchorContract?: string | null; anchors: ChainAnchor[] }
export interface ChainReserves {
  onChain: boolean; available: boolean; attested: boolean;
  period?: number | null; attestedAt?: string | null; attester?: string | null;
  bankBalance?: number | null; heldAtAttestation?: number | null;
  gap?: number | null; currentHeld?: number | null; statementHash?: string | null;
}
export interface ChainActivityItem {
  txHash: string; method: string; signer: string; from: string; timestamp: string;
  blockNumber: number; success: boolean; check: string;
  rvndToBeneficiary?: number | null; explorerUrl?: string | null;
}
export interface ChainActivity {
  onChain: boolean; available: boolean; source?: string | null; escrowContract?: string | null;
  items: ChainActivityItem[]; nextPageToken?: string | null; unrecordedCount: number;
}
export const getChainConfig=()=>apiRequest<ChainConfig>('/api/v1/transparency/chain');
export const getCampaignChain=(id:string)=>apiRequest<CampaignChain>(('/api/v1/transparency/campaigns/'+encodeURIComponent(id)) as `/api/v1/transparency/${string}`);
export const getChainAnchors=()=>apiRequest<ChainAnchors>('/api/v1/transparency/anchors');
export const getChainReserves=()=>apiRequest<ChainReserves>('/api/v1/transparency/reserves');
export const getChainActivity=(token:string|null)=>apiRequest<ChainActivity>(
  ('/api/v1/transparency/activity'+(token?'?pageToken='+encodeURIComponent(token):'')) as `/api/v1/transparency/${string}`
);

/** Only link to the explicitly requested, trusted Fuji explorer. Never trust arbitrary API URLs. */
export function safeSnowtrace(raw:string|null|undefined):string|null {
  if (!raw) return null;
  try {
    const url = new URL(raw);
    const host = url.hostname.toLowerCase();
    return url.protocol==='https:' && (host==='testnet.snowtrace.io'||host==='snowtrace.io') ? url.href : null;
  } catch { return null; }
}
export const isFuji=(config:ChainConfig|undefined)=>Boolean(config?.onChain && config.available && config.chainId===43113);
export const chainStatusVi:Record<string,string>={
  NOT_ON_CHAIN:'Chưa ghi lên blockchain', PENDING:'Đang chờ gửi lệnh',
  SUBMITTED:'Đã gửi lệnh lên chain', CONFIRMED:'Đã xác nhận trên chain',
  FAILED:'Lệnh không thành công', RESERVING:'Đang khóa ký quỹ',
  RESERVED_ON_CHAIN:'Đã ký quỹ on-chain', QUEUING:'Đang gửi lệnh giải ngân',
  IN_OBJECTION_WINDOW:'Đang trong cửa sổ phản đối', BLOCKED_BY_DISPUTE:'Tạm dừng do tranh chấp',
  HELD_BY_GUARDIAN:'Tạm dừng bởi Guardian độc lập',
  RELEASED_ON_CHAIN:'Đã giải ngân on-chain',
  CANCELLING:'Đang hủy lệnh', CANCELLED_ON_CHAIN:'Đã hủy trên chain',
  NOT_ANCHORED:'Chưa ghi dấu', CONSISTENT:'Đối chiếu khớp',
  MISMATCH:'Không khớp — cần xem xét', UNAVAILABLE:'Không thể xác minh',
  RECORDED:'Có trong sổ hệ thống', UNRECORDED:'Ngoài sổ — cần xem xét',
  INDEPENDENT:'Giao dịch bên ngoài hệ thống', REVERTED:'Giao dịch thất bại',
};
export const viChain=(status:string|null|undefined)=>status?chainStatusVi[status]??status:'Chưa có trạng thái';
export function remaining(releaseAt:string|undefined|null,now:number):string|null{
  if (!releaseAt) return null;
  const time=Date.parse(releaseAt);
  if (!Number.isFinite(time)) return null;
  const seconds=Math.max(0,Math.ceil((time-now)/1000));
  if(!seconds)return 'Đã hết thời gian chờ; chờ Backend xác nhận thực thi';
  const days=Math.floor(seconds/86400),hours=Math.floor((seconds%86400)/3600),minutes=Math.ceil((seconds%3600)/60);
  return 'Còn '+(days?days+' ngày ':'')+(hours?hours+' giờ ':'')+minutes+' phút đến thời điểm có thể giải ngân';
}
