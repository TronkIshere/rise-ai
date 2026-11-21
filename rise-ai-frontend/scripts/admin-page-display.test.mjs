import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

async function loadDisplay(file) {
  const source=readFileSync(new URL(file,import.meta.url),'utf8');
  const {outputText}=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}});
  return import('data:text/javascript;base64,'+Buffer.from(outputText).toString('base64'));
}
const {adminPageStatus}=await loadDisplay('../src/lib/admin-page-display.ts');
const {adminStatusFilters}=await loadDisplay('../src/lib/admin-display.ts');

test('Admin queue filters have readable Vietnamese status labels in every business queue',()=>{
  for(const [queue,filters] of Object.entries(adminStatusFilters)) {
    for(const {value} of filters) assert.notEqual(adminPageStatus(value),value,queue+': untranslated '+value);
  }
  assert.equal(adminPageStatus('SUBMITTED'),'Đã gửi xét duyệt');
  assert.equal(adminPageStatus('APPROVED'),'Đã phê duyệt');
  assert.equal(adminPageStatus('RELEASED'),'Đã ghi giải ngân mô phỏng');
});
test('Unknown Backend states remain visible rather than being assigned a false known status',()=>{
  assert.equal(adminPageStatus('FUTURE_BACKEND_STATE'),'FUTURE_BACKEND_STATE');
});
test('Missing Backend state is explicitly unknown, never a completed or zero state',()=>{
  assert.equal(adminPageStatus(undefined),'Chưa rõ trạng thái');
  assert.equal(adminPageStatus(''),'Chưa rõ trạng thái');
});
