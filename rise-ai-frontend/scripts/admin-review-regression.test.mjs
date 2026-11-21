import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const openapi=JSON.parse(readFileSync(new URL('../../rise-ai-backend/docs/api/openapi-v1.json',import.meta.url)));
const escrow=readFileSync(new URL('../src/pages/AdminEscrowPage.tsx',import.meta.url),'utf8');
const admin=readFileSync(new URL('../src/pages/AdminReviewPage.tsx',import.meta.url),'utf8');
const api=readFileSync(new URL('../src/api/admin.ts',import.meta.url),'utf8');

test('FE-04 review retains all 8 balance dimensions in selected account and history; V1.2 master list selects the report',()=>{
  const keys=['confirmed','reserved','refundReserved','disbursed','refunded','adjustmentCredits','adjustmentDebits','available'];
  const schema=openapi.components.schemas.Balances.properties;
  for(const key of keys){
    assert.ok(schema[key], 'Backend Balances missing '+key);
    assert.match(escrow,new RegExp("key:'"+key+"'"), 'Escrow UI missing '+key);
  }
  assert.match(escrow,/reports\.data\.map/,'Backend reports populate the master list');
  assert.match(escrow,/chooseCampaign\(r\.campaignId\)/,'Master list selects the same Backend campaign report');
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
  assert.match(escrow,/u5-tablewrap w-full min-w-0 max-w-full overflow-x-auto/,'Every reconciliation table keeps local scrolling and can shrink');
  assert.match(escrow,/u5-reconciliation-table w-full min-w-\[370px\] border-collapse/,'Legacy narrow table minimum is preserved below desktop styling');
  assert.match(escrow,/key=\{f\.escrowAccountId\?\?index\} className="mt-5 min-w-0 max-w-full/);
  const css=readFileSync(new URL('../src/styles/admin-escrow-approved-v12.css',import.meta.url),'utf8');
  assert.match(css,/\.u5-page > \*,[^}]+min-width:0/,'Approved desktop tracks keep shrinkable children');
});
