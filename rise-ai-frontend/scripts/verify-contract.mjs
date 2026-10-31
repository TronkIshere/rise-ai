import { readFileSync } from 'node:fs';
const url = new URL('../../rise-ai-backend/docs/api/openapi-v1.json', import.meta.url);
const spec = JSON.parse(readFileSync(url, 'utf8'));
const errors = [];
const endpoints = [
  ['/api/v1/auth/login','post'],
  ['/api/v1/auth/logout','post'],
  ['/api/v1/auth/me','get'],
  ['/api/v1/campaigns','get'],
  ['/api/v1/campaigns/slug/{slug}','get'],
  ['/api/v1/campaigns/{campaignId}/milestones','get'],
  ['/api/v1/campaigns/{id}','get'],
  ['/api/v1/campaigns/{campaignId}/donations','post'],
  ['/api/v1/donations/me','get'],
  ['/api/v1/donations/{id}/cancel','post'],
  ['/api/v1/donations/{donationId}/refunds','post'],
  ['/api/v1/refunds/me','get'],
  ['/api/v1/refunds/{id}/cancel','post'],
  ['/api/v1/campaigns/{campaignId}/disputes','post'],
  ['/api/v1/disputes/me','get'],
  ['/api/v1/disputes/{id}','get'],
  ['/api/v1/disputes/{id}/messages','get'],
  ['/api/v1/disputes/{id}/messages','post'],
  ['/api/v1/disputes/{id}/withdraw','post'],
  ['/api/v1/organizations/mine','get'],
  ['/api/v1/organizations','post'],
  ['/api/v1/organizations/{id}','put'],
  ['/api/v1/organizations/{id}/submit-verification','post'],
  ['/api/v1/campaigns/mine','get'],
  ['/api/v1/campaigns','post'],
  ['/api/v1/campaigns/{id}','put'],
  ['/api/v1/campaigns/{id}/submit','post'],
  ['/api/v1/campaigns/{id}/publish','post'],
  ['/api/v1/campaigns/{campaignId}/milestones','post'],
  ['/api/v1/milestones/{id}','put'],
  ['/api/v1/milestones/{id}/start','post'],
  ['/api/v1/milestones/{milestoneId}/evidence','get'],
  ['/api/v1/milestones/{milestoneId}/evidence','post'],
  ['/api/v1/campaigns/{campaignId}/disbursements','get'],
  ['/api/v1/milestones/{milestoneId}/disbursements','post'],
  ['/api/v1/disbursements/{id}/resubmit','post'],
  ['/api/v1/disbursements/{id}/cancel','post'],
];
for (const [path, method] of endpoints) {
  if (!spec.paths?.[path]?.[method]) errors.push(method + ' ' + path + ' missing');
}
for (const [schema, fields] of Object.entries({
  LoginRequest: ['email','password'],
  TokenResponse: ['accessToken','refreshToken','user'],
  UserResponse: ['roles','email'],
  CampaignResponse: ['slug','title','campaignStatus','targetAmount','coverImageUrl','funding','organization','problemStatement','solutionDescription','impactGoals'],
  FundingSummary: ['netRaised','totalDisbursed','progressPercent'],
  MilestoneResponse: ['id','sequenceNumber','milestoneStatus','plannedAmount','releasedAmount'],
  PageResponseCampaignResponse: ['data','currentPage','totalPages'],
  CreateDonationRequest: ['amount','anonymous','currency','donorMessage','paymentMethod'],
  DonationResponse: ['id','donationStatus','campaignId','amount','refundedAmount'],
  CreateRefundRequest: ['amount','reason'],
  RefundResponse: ['refundStatus','donationId','externalPaymentStatus'],
  OpenDisputeRequest: ['title','description','disputeType'],
  DisputeResponse: ['id','disputeStatus','openedBy','campaignId'],
  DisputeMessageRequest: ['message'],
  DisputeMessageResponse: ['authorId','message'],
  OrganizationRequest: ['name','organizationType','contactEmail'],
  OrganizationResponse: ['id','createdBy','verificationStatus','verificationNotes'],
  CampaignRequest: ['organizationId','title','targetAmount','startAt','endAt','beneficiaryCount','budgetBreakdown'],
  MilestoneRequest: ['plannedAmount','startAt','dueAt','releaseConditions','expectedDeliverables'],
  EvidenceRequest: ['evidenceType','fileUrl','fileHash','externalReference'],
  EvidenceResponse: ['evidenceStatus','reviewNote'],
  CreateDisbursementRequest: ['amount','reason'],
  DisbursementResponse: ['requestStatus','simulated','requestReason'],
})) {
  const props = spec.components?.schemas?.[schema]?.properties;
  for (const field of fields) if (!props?.[field]) errors.push(schema + '.' + field + ' missing');
}
const roles = spec.components?.schemas?.UserResponse?.properties?.roles?.items?.enum ?? [];
for (const role of ['ADMIN','DONOR','CAMPAIGN_CREATOR']) {
  if (!roles.includes(role)) errors.push('role ' + role + ' missing');
}

const queryParams = spec.paths['/api/v1/campaigns']?.get?.parameters ?? [];
for (const param of ['status','q','category','pageable']) {
  if (!queryParams.some((entry) => entry.name === param)) errors.push('campaign search param ' + param + ' missing');
}

if (errors.length) {
  console.error('Contract FAILED: ' + errors.join('; '));
  process.exit(1);
}
console.log('Backend contract PASS (' + Object.keys(spec.paths).length + ' paths)');
