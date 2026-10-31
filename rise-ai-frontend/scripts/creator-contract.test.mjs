import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const spec=JSON.parse(readFileSync(new URL('../../rise-ai-backend/docs/api/openapi-v1.json',import.meta.url)));
test('FE-03: creator writable routes are separate from admin review/release',()=>{
  for(const [path,method] of [
    ['/api/v1/organizations','post'],['/api/v1/organizations/mine','get'],
    ['/api/v1/organizations/{id}','put'],['/api/v1/organizations/{id}/submit-verification','post'],
    ['/api/v1/campaigns','post'],['/api/v1/campaigns/mine','get'],
    ['/api/v1/campaigns/{id}','put'],['/api/v1/campaigns/{id}/submit','post'],
    ['/api/v1/campaigns/{id}/publish','post'],['/api/v1/campaigns/{campaignId}/milestones','post'],
    ['/api/v1/milestones/{id}','put'],['/api/v1/milestones/{id}/start','post'],
    ['/api/v1/milestones/{milestoneId}/evidence','get'],
    ['/api/v1/milestones/{milestoneId}/evidence','post'],
    ['/api/v1/campaigns/{campaignId}/disbursements','get'],
    ['/api/v1/milestones/{milestoneId}/disbursements','post'],
    ['/api/v1/disbursements/{id}/resubmit','post'],
    ['/api/v1/disbursements/{id}/cancel','post'],
  ]) assert.ok(spec.paths[path]?.[method],method+' '+path);
  for(const path of ['/api/v1/admin/organizations/{id}/verify','/api/v1/disbursements/{id}/release','/api/v1/evidence/{id}/accept'])assert.ok(spec.paths[path]?.post);
});
test('FE-03: required budget and timestamp constraints are API-first',()=>{
  for(const prop of ['organizationId','targetAmount','minimumDonationAmount','startAt','endAt','beneficiaryCount'])assert.ok(spec.components.schemas.CampaignRequest.required.includes(prop));
  for(const prop of ['title','startAt','dueAt','releaseConditions','expectedDeliverables','plannedAmount'])assert.ok(spec.components.schemas.MilestoneRequest.required.includes(prop));
  assert.equal(spec.components.schemas.EvidenceRequest.properties.fileHash.pattern, '^([a-fA-F0-9]{64})?$');
  assert.ok(spec.components.schemas.CreateDisbursementRequest.required.includes('amount'));
  assert.ok(spec.paths['/api/v1/milestones/{milestoneId}/disbursements'].post.parameters.some(x=>x.name==='Idempotency-Key'));
  assert.ok(spec.components.schemas.DisbursementResponse.properties.simulated);
});
