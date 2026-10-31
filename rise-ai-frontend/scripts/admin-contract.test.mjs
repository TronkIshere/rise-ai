import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const s=JSON.parse(readFileSync(new URL('../../rise-ai-backend/docs/api/openapi-v1.json',import.meta.url),'utf8'));
test('FE-04 privileged review and simulated escrow APIs exist',()=>{
  const endpoints=[
    ['/api/v1/admin/statistics','get'],['/api/v1/organizations','get'],
    ['/api/v1/admin/organizations/{id}/verify','post'],
    ['/api/v1/admin/organizations/{id}/reject-verification','post'],
    ['/api/v1/campaigns/{id}/approve','post'],['/api/v1/campaigns/{id}/reject','post'],
    ['/api/v1/admin/donations','get'],['/api/v1/admin/donations/{id}/confirm','post'],
    ['/api/v1/admin/donations/{id}/fail','post'],['/api/v1/admin/refunds','get'],
    ['/api/v1/admin/refunds/{id}/approve','post'],['/api/v1/admin/refunds/{id}/record','post'],
    ['/api/v1/admin/disbursements','get'],['/api/v1/disbursements/{id}/start-review','post'],
    ['/api/v1/disbursements/{id}/request-evidence','post'],['/api/v1/disbursements/{id}/release','post'],
    ['/api/v1/milestones/{milestoneId}/evidence','get'],
    ['/api/v1/evidence/{id}/accept','post'],['/api/v1/evidence/{id}/reject','post'],
    ['/api/v1/admin/disputes','get'],['/api/v1/admin/disputes/{id}/assign','post'],
    ['/api/v1/admin/disputes/{id}/resolve','post'],
    ['/api/v1/admin/escrow/reconciliation','get'],['/api/v1/admin/escrow/reconciliation/runs','post'],
    ['/api/v1/admin/escrow/reconciliation/runs','get'],
    ['/api/v1/admin/escrow/campaigns/{campaignId}/reconciliation','get'],
    ['/api/v1/campaigns/{campaignId}/escrow/ledger','get'],
    ['/api/v1/admin/escrow/campaigns/{campaignId}/adjustments','post'],
    ['/api/v1/admin/escrow/campaigns/{campaignId}/settlements','post'],
    ['/api/v1/admin/adjustments/{id}/approve','post'],
    ['/api/v1/admin/settlements/{id}/approve','post'],
  ];
  for(const [path,method] of endpoints)assert.ok(s.paths[path]?.[method],method+' '+path);
});
test('FE-04 high-stakes transitions require typed request schemas',()=>{
  assert.deepEqual(s.components.schemas.VerificationDecisionRequest.required,['notes']);
  assert.deepEqual(s.components.schemas.RequiredReasonRequest.required,['reason']);
  assert.deepEqual(s.components.schemas.ResolveDisputeRequest.required,['outcome','resolution']);
  assert.equal(s.components.schemas.EscrowSummary.properties.simulated.type,'boolean');
  assert.ok(s.components.schemas.ReconciliationReport.properties.consistent);
  assert.ok(s.components.schemas.ReconciliationRunResponse.properties.inconsistentAccounts);
  assert.deepEqual(s.components.schemas.RefundResponse.properties.externalPaymentStatus.enum,['NOT_EXECUTED_SIMULATION']);
  assert.ok(s.components.schemas.DisbursementResponse.properties.simulated);
  assert.ok(s.components.schemas.SettlementResponse.properties.settlementStatus.enum.includes('PROPOSED'));
  assert.ok(s.components.schemas.AdjustmentResponse.properties.adjustmentStatus.enum.includes('REQUESTED'));
});
