import { apiRequest } from './client';
import type {
  AdjustmentResponse, CampaignResponse, DisbursementResponse, DisputeResponse, DonationResponse,
  EscrowSummary, EvidenceResponse, OrganizationResponse, PageResponse, ReconciliationReport,
  ReconciliationRunResponse, RefundResponse, SettlementResponse, SystemStatistics,
} from './contracts';

const id = (value:string)=>encodeURIComponent(value);
const paging=(page:number)=>new URLSearchParams({page:String(Math.max(0,Math.floor(page))),size:'10'}).toString();
const query=(status:string|undefined,page:number)=>((status?'status='+encodeURIComponent(status)+'&':'')+paging(page));

export type AdminQueue='organizations'|'campaigns'|'donations'|'refunds'|'disbursements'|'disputes'|'adjustments'|'settlements';
export const adminQueues:{id:AdminQueue;label:string;defaultStatus?:string}[]=[
  {id:'organizations',label:'Tổ chức',defaultStatus:'PENDING_REVIEW'},
  {id:'campaigns',label:'Chiến dịch',defaultStatus:'PENDING_REVIEW'},
  {id:'donations',label:'Đóng góp',defaultStatus:'PENDING'},
  {id:'refunds',label:'Hoàn tiền',defaultStatus:'REQUESTED'},
  {id:'disbursements',label:'Giải ngân',defaultStatus:'SUBMITTED'},
  {id:'disputes',label:'Tranh chấp',defaultStatus:'OPEN'},
  {id:'adjustments',label:'Điều chỉnh sổ',defaultStatus:'REQUESTED'},
  {id:'settlements',label:'Tất toán',defaultStatus:'PROPOSED'},
];
export type AdminItem=OrganizationResponse|CampaignResponse|DonationResponse|RefundResponse|DisbursementResponse|DisputeResponse|AdjustmentResponse|SettlementResponse;
export type AdminRecord={
  organizations:OrganizationResponse;campaigns:CampaignResponse;donations:DonationResponse;
  refunds:RefundResponse;disbursements:DisbursementResponse;disputes:DisputeResponse;
  adjustments:AdjustmentResponse;settlements:SettlementResponse;
};
const queueUrl:Record<AdminQueue,string>={
  organizations:'/api/v1/organizations',campaigns:'/api/v1/campaigns',
  donations:'/api/v1/admin/donations',refunds:'/api/v1/admin/refunds',
  disbursements:'/api/v1/admin/disbursements',disputes:'/api/v1/admin/disputes',
  adjustments:'/api/v1/admin/adjustments',settlements:'/api/v1/admin/settlements',
};
export function listAdminQueue<K extends AdminQueue>(kind:K,status:string,page:number){
  const url=queueUrl[kind]+'?'+query(status||undefined,page);
  return apiRequest<PageResponse<AdminRecord[K]>>(url as `/api/v1/admin/${string}`);
}
export const adminStatistics=()=>apiRequest<SystemStatistics>('/api/v1/admin/statistics');
export function getAdminItem(kind:AdminQueue,recordId:string):Promise<AdminItem>{
  const path=kind==='organizations'?'/api/v1/organizations/'+id(recordId):
    kind==='campaigns'?'/api/v1/campaigns/'+id(recordId):
    kind==='donations'?'/api/v1/donations/'+id(recordId):
    kind==='refunds'?'/api/v1/refunds/'+id(recordId):
    kind==='disbursements'?'/api/v1/disbursements/'+id(recordId):
    kind==='disputes'?'/api/v1/disputes/'+id(recordId):
    '/api/v1/admin/'+kind+'/'+id(recordId);
  return apiRequest<AdminItem>(path as `/api/v1/admin/${string}`);
}
export function getDisbursementEvidence(recordId:string,page:number){
  return apiRequest<PageResponse<EvidenceResponse>>(`/api/v1/disbursements/${id(recordId)}/evidence?${paging(page)}`);
}
export function getAdminEvidence(recordId:string){return apiRequest<EvidenceResponse>(`/api/v1/evidence/${id(recordId)}`);}
export type AdminOperation='verify'|'reject-verification'|'approve'|'reject'|'confirm'|'fail'|
 'record'|'start-review'|'request-evidence'|'release'|'assign'|'resolve-upheld'|'resolve-dismissed'|
 'pause'|'resume'|'complete'|'accept-evidence'|'reject-evidence'|'cancel';
export function performAdminAction(kind:AdminQueue,recordId:string,operation:AdminOperation,reason:string,reviewerId?:string){
  const rid=id(recordId);
  let path:string,body:unknown={reason};
  if(kind==='organizations'){
    path='/api/v1/admin/organizations/'+rid+'/'+(operation==='verify'?'verify':'reject-verification');
    body={notes:reason};
  }else if(kind==='campaigns'){
    path='/api/v1/campaigns/'+rid+'/'+operation;
  }else if(kind==='donations'){
    path='/api/v1/admin/donations/'+rid+'/'+(operation==='confirm'?'confirm':'fail');
    body=operation==='confirm'?{simulatedReference:reason}:{reason};
  }else if(kind==='refunds'){
    path='/api/v1/admin/refunds/'+rid+'/'+operation;
    if(operation==='record')body=undefined;
  }else if(kind==='disbursements'){
    path='/api/v1/disbursements/'+rid+'/'+operation;
    if(operation==='release'||operation==='start-review')body=undefined;
  }else if(kind==='disputes'){
    const tail=operation==='assign'?'assign':'resolve';
    path='/api/v1/admin/disputes/'+rid+'/'+tail;
    body=operation==='assign'?{reviewerId}:
      {outcome:operation==='resolve-upheld'?'UPHELD':'DISMISSED',resolution:reason};
  }else{
    path='/api/v1/admin/'+kind+'/'+rid+'/'+operation;
  }
  return apiRequest<AdminItem>(path as `/api/v1/admin/${string}`,{method:'POST',...(body===undefined?{}:{body:JSON.stringify(body)})});
}
export function reviewAdminEvidence(recordId:string,approve:boolean,note:string){
  return apiRequest<EvidenceResponse>(`/api/v1/evidence/${id(recordId)}/${approve?'accept':'reject'}`,{
    method:'POST',body:JSON.stringify({reason:note}),
  });
}
export const globalReconciliation=()=>apiRequest<ReconciliationReport[]>('/api/v1/admin/escrow/reconciliation');
export const reconciliationRuns=(page:number)=>apiRequest<PageResponse<ReconciliationRunResponse>>(`/api/v1/admin/escrow/reconciliation/runs?${paging(page)}`);
export const runReconciliation=()=>apiRequest<ReconciliationRunResponse>('/api/v1/admin/escrow/reconciliation/runs',{method:'POST'});
export const campaignEscrow=(campaignId:string)=>apiRequest<EscrowSummary>(`/api/v1/campaigns/${id(campaignId)}/escrow`);
export const campaignReconciliation=(campaignId:string)=>apiRequest<ReconciliationReport>(`/api/v1/admin/escrow/campaigns/${id(campaignId)}/reconciliation`);
export const proposeAdjustment=(campaignId:string,amount:number,direction:'CREDIT'|'DEBIT'|'NEUTRAL',reason:string)=>
  apiRequest<AdjustmentResponse>(`/api/v1/admin/escrow/campaigns/${id(campaignId)}/adjustments`,{
    method:'POST',body:JSON.stringify({amount,direction,reason}),
  });
export const proposeSettlement=(campaignId:string,reason:string)=>
  apiRequest<SettlementResponse>(`/api/v1/admin/escrow/campaigns/${id(campaignId)}/settlements`,{
    method:'POST',body:JSON.stringify({reason}),
  });

export const getReconciliationRun=(idValue:string)=>apiRequest<ReconciliationRunResponse>(`/api/v1/admin/escrow/reconciliation/runs/${id(idValue)}`);
export const ledgerEntries=(campaignId:string,page:number)=>apiRequest<PageResponse<import('./contracts').LedgerEntryResponse>>(`/api/v1/campaigns/${id(campaignId)}/escrow/ledger?${paging(page)}`);
