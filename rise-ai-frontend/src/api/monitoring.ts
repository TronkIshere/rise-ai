import { apiRequest } from './client';
import type { PageResponse } from './contracts';

export type FindingReviewStatus='OPEN'|'ACKNOWLEDGED'|'CONCERN_CONFIRMED'|'DISMISSED';
export type FindingSeverity='INFO'|'LOW'|'MEDIUM'|'HIGH'|'CRITICAL';
export interface Finding {
  id?: string; findingKey?: string; detectorType?: string; ruleId?: string; ruleVersion?: string;
  category?: string; severity?: FindingSeverity; subjectType?: string; subjectId?: string;
  campaignId?: string; milestoneId?: string; disbursementRequestId?: string;
  explanation?: string; recommendedAction?: string; facts?: Record<string,unknown>[];
  reviewStatus?: FindingReviewStatus; occurrenceCount?: number; newOccurrencesSinceReview?: number;
  firstSeenAt?: string; lastSeenAt?: string; reviewedBy?: string; reviewedAt?: string;
  reviewNote?: string; advisoryOnly?: boolean;
}
export interface FindingOccurrence {
  sourceEventId?: string; actorId?: string; observedAt?: string;
  severity?: FindingSeverity; aiNote?: string;
}
export interface FindingDetail { finding?: Finding; occurrences?: FindingOccurrence[] }
export interface FindingStatusHistory {
  id?: string; entityId?: string; entityType?: string; fromStatus?: string; toStatus?: string;
  reason?: string; changedBy?: string; createdAt?: string;
}
export interface RulePrecision {
  ruleId?: string; total?: number; open?: number; acknowledged?: number;
  concernConfirmed?: number; dismissed?: number; precision?: number|null;
}
export interface AuditEvent {
  id?: string; actorUserId?: string; action?: string; correlationId?: string; createdAt?: string;
  entityId?: string; entityType?: string; ipAddress?: string;
  metadata?: Record<string,unknown>; newState?: string; previousState?: string;
}
export type OutboxStatus='PENDING'|'IN_FLIGHT'|'PUBLISHED'|'FAILED';
export interface OutboxEvent {
  id?: string; eventId?: string; eventType?: string; aggregateId?: string; aggregateType?: string;
  status?: OutboxStatus; attempts?: number; lastError?: string;
  occurredAt?: string; nextAttemptAt?: string; publishedAt?: string;
}
export interface OutboxStats {
  relayEnabled?: boolean; pending?: number; inFlight?: number; published?: number; failed?: number;
  oldestUndeliveredOccurredAt?: string; oldestUndeliveredAgeSeconds?: number;
}
export type AlertStatus='PENDING'|'RETRYING'|'SENT'|'FAILED';
export interface AlertEvent {
  id?: string; kind?: string; status?: AlertStatus; details?: Record<string,string>;
  attempts?: number; createdAt?: string; deliveredAt?: string; failedAt?: string;
  nextAttemptAt?: string; lastError?: string;
}
export interface AlertStats {
  webhookConfigured?: boolean; pending?: number; retrying?: number; sent?: number; failed?: number;
  oldestUndeliveredCreatedAt?: string; oldestUndeliveredAgeSeconds?: number;
}
export interface FindingFilters {status?:FindingReviewStatus|'';severity?:FindingSeverity|'';category?:string;campaignId?:string}
export interface AuditFilters {actorId?:string;entityType?:string;entityId?:string;action?:string}

const encodeId=(value:string)=>encodeURIComponent(value);
const params=(page:number,filters:object={})=>{
  const query=new URLSearchParams({page:String(Math.max(0,Math.floor(page))),size:'10'});
  for(const [key,value] of Object.entries(filters))if(typeof value==='string'&&value.trim())query.set(key,value.trim());
  return query.toString();
};
const requestPath=(route:string)=>route as `/api/v1/admin/${string}`;
export function listFindings(filters:FindingFilters,page:number){
  return apiRequest<PageResponse<Finding>>(requestPath('/api/v1/admin/monitoring/findings?'+params(page,filters)));
}
export const getFinding=(id:string)=>apiRequest<FindingDetail>(requestPath('/api/v1/admin/monitoring/findings/'+encodeId(id)));
export const getFindingHistory=(id:string)=>apiRequest<FindingStatusHistory[]>(requestPath('/api/v1/admin/monitoring/findings/'+encodeId(id)+'/history'));
export const listRulePrecision=()=>apiRequest<RulePrecision[]>('/api/v1/admin/monitoring/findings/precision');
export function reviewFinding(id:string,status:Exclude<FindingReviewStatus,'OPEN'>,note:string){
  return apiRequest<Finding>(requestPath('/api/v1/admin/monitoring/findings/'+encodeId(id)+'/review'),{
    method:'POST',body:JSON.stringify({status,note:note.trim()}),
  });
}
export function listAuditEvents(filters:AuditFilters,page:number){
  return apiRequest<PageResponse<AuditEvent>>(requestPath('/api/v1/admin/audit-logs?'+params(page,filters)));
}
export const getOutboxStats=()=>apiRequest<OutboxStats>('/api/v1/admin/monitoring/outbox/stats');
export const getAlertStats=()=>apiRequest<AlertStats>('/api/v1/admin/monitoring/alerts/stats');
export const listOutbox=(status:OutboxStatus|'',page:number)=>
  apiRequest<PageResponse<OutboxEvent>>(requestPath('/api/v1/admin/monitoring/outbox?'+params(page,{status})));
export const listAlerts=(status:AlertStatus|'',page:number)=>
  apiRequest<PageResponse<AlertEvent>>(requestPath('/api/v1/admin/monitoring/alerts?'+params(page,{status})));
export const requeueOutbox=(id:string,reason:string)=>apiRequest<OutboxEvent>(
  requestPath('/api/v1/admin/monitoring/outbox/'+encodeId(id)+'/requeue'),{method:'POST',body:JSON.stringify({reason:reason.trim()})},
);
export const requeueAlert=(id:string,reason:string)=>apiRequest<AlertEvent>(
  requestPath('/api/v1/admin/monitoring/alerts/'+encodeId(id)+'/requeue'),{method:'POST',body:JSON.stringify({reason:reason.trim()})},
);
