import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const spec=JSON.parse(readFileSync(new URL('../../rise-ai-backend/docs/api/openapi-v1.json',import.meta.url),'utf8'));
const source=(path)=>readFileSync(new URL('../'+path,import.meta.url),'utf8');
test('FE-05 endpoints all exist in immutable Backend OpenAPI',()=>{
  const endpoints=[
    ['/api/v1/admin/monitoring/findings','get'],
    ['/api/v1/admin/monitoring/findings/{id}','get'],
    ['/api/v1/admin/monitoring/findings/{id}/history','get'],
    ['/api/v1/admin/monitoring/findings/{id}/review','post'],
    ['/api/v1/admin/monitoring/findings/precision','get'],
    ['/api/v1/admin/audit-logs','get'],
    ['/api/v1/admin/monitoring/outbox','get'],
    ['/api/v1/admin/monitoring/outbox/stats','get'],
    ['/api/v1/admin/monitoring/outbox/{id}/requeue','post'],
    ['/api/v1/admin/monitoring/alerts','get'],
    ['/api/v1/admin/monitoring/alerts/stats','get'],
    ['/api/v1/admin/monitoring/alerts/{id}/requeue','post'],
  ];
  for(const [path,method] of endpoints)assert.ok(spec.paths[path]?.[method],method+' '+path);
});
test('FE-05 decision schemas preserve all statuses and evidence fields',()=>{
  const s=spec.components.schemas;
  assert.deepEqual(s.ReviewFindingRequest.required,['note','status']);
  assert.deepEqual(s.ReviewFindingRequest.properties.status.enum,['OPEN','ACKNOWLEDGED','CONCERN_CONFIRMED','DISMISSED']);
  for(const f of ['facts','advisoryOnly','reviewStatus','reviewedBy','occurrenceCount','newOccurrencesSinceReview'])assert.ok(s.FindingResponse.properties[f],f);
  assert.ok(s.FindingDetailResponse.properties.occurrences);
  assert.ok(s.StatusHistoryResponse.properties.reason);
  assert.ok(s.AuditLogResponse.properties.metadata);
});
test('FE-05 UI explicitly limits review choices and protects audit history',()=>{
  const display=source('src/lib/monitoring-display.ts');
  const detail=source('src/pages/AdminFindingPage.tsx');
  const nav=source('src/app/router.tsx');
  const delivery=source('src/pages/AdminDeliveryPage.tsx');
  const api=source('src/api/monitoring.ts');
  assert.match(display,/if\(status==='OPEN'\)return \['ACKNOWLEDGED','CONCERN_CONFIRMED','DISMISSED'\]/);
  assert.match(display,/if\(status==='ACKNOWLEDGED'\)return \['CONCERN_CONFIRMED','DISMISSED'\]/);
  assert.match(detail,/!history\.isSuccess/);
  assert.match(detail,/readableJson\(fact\)/);
  assert.match(delivery,/item\.status==='FAILED'/);
  assert.match(delivery,/reason\.trim\(\)/);
  assert.match(api,/reviewFinding\(id:string/);
  for(const page of ['/admin/monitoring','/admin/monitoring/findings/:id','/admin/monitoring/delivery','/admin/audit']){
    assert.ok(nav.includes("path: '"+page+"'"),'missing route '+page);
  }
  const guards=(nav.match(/<RequireRole role="ADMIN">/g)||[]).length;
  assert.ok(guards>=7,'Admin monitoring routes must retain role guards');
  assert.ok(!api.includes('/api/monitoring/analyze'),'Frontend must not directly call Monitoring');
});
