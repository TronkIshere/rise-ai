import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const openapi=JSON.parse(readFileSync(new URL('../../rise-ai-backend/docs/api/openapi-v1.json',import.meta.url)));
const escrow=readFileSync(new URL('../src/pages/AdminEscrowPage.tsx',import.meta.url),'utf8');
const admin=readFileSync(new URL('../src/pages/AdminReviewPage.tsx',import.meta.url),'utf8');
const api=readFileSync(new URL('../src/api/admin.ts',import.meta.url),'utf8');

test('FE-04 review retains all 8 reconciliation balance dimensions in global/detail/history UI',()=>{
  const keys=['confirmed','reserved','refundReserved','disbursed','refunded','adjustmentCredits','adjustmentDebits','available'];
  const schema=openapi.components.schemas.Balances.properties;
  for(const key of keys){
    assert.ok(schema[key], 'Backend Balances missing '+key);
    assert.match(escrow,new RegExp("key:'"+key+"'"), 'Escrow UI missing '+key);
  }
  assert.match(escrow,/ReconciliationBalances report=\{report\}/,'Global reports show breakdown');
  assert.match(escrow,/ReconciliationBalances report=\{f\}/,'Historic findings show breakdown');
  assert.match(escrow,/ReconciliationBalances report=\{reconciliation\.data\}/,'Campaign report shows breakdown');
  assert.match(escrow,/stored===computed/,'Mismatch comparison based on actual amounts');
});
test('FE-04 independent adjustment reviewer can distinguish CREDIT and DEBIT before approval',()=>{
  assert.ok(openapi.components.schemas.AdjustmentResponse.properties.direction);
  assert.match(admin,/Chiều điều chỉnh:/);
  assert.match(admin,/TĂNG số dư escrow mô phỏng/);
  assert.match(admin,/GIẢM số dư escrow mô phỏng/);
  assert.match(admin,/!\['CREDIT','DEBIT','NEUTRAL'\]\.includes\(direction\)/);
});
test('FE-04 dispute reviewer must load submitted references and message history',()=>{
  assert.ok(openapi.components.schemas.DisputeResponse.properties.supportingReferences);
  assert.ok(openapi.components.schemas.DisputeMessageResponse.properties.message);
  assert.ok(openapi.paths['/api/v1/disputes/{id}/messages'].get);
  assert.match(api,/getAdminDisputeMessages/);
  assert.match(admin,/supportingReferences/);
  assert.match(admin,/thread\.data\?\.data\?\.map/);
  assert.match(admin,/!thread\.isSuccess/);
  assert.match(admin,/resolve-/);
});

test('FE-04 narrow viewport: reconciliation scroll is contained inside a shrinkable card',()=>{
  const globalCard='<article key={report.campaignId??index} className="min-w-0 max-w-full';
  assert.ok(escrow.includes(globalCard),'Global grid cards must shrink below the 370px table minimum width');
  assert.match(escrow,/return <div className="mt-4 w-full min-w-0 max-w-full">/);
  assert.match(escrow,/className="w-full min-w-0 max-w-full overflow-x-auto"/);
  assert.match(escrow,/className="w-full min-w-\[370px\] border-collapse/);
  assert.match(escrow,/key=\{f\.escrowAccountId\?\?index\} className="mt-5 min-w-0 max-w-full/);
  assert.match(escrow,/reconciliation\.isSuccess\?<div className="mt-4 min-w-0 max-w-full/);
});
