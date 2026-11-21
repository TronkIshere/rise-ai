import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const source=readFileSync(new URL('../src/lib/rule-precision-display.ts',import.meta.url),'utf8');
const code=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText;
const {assessRulePrecision,ruleCount}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
const rule={total:13,open:3,acknowledged:2,concernConfirmed:6,dismissed:2,precision:.75};

test('Precision uses only concluded outcomes; OPEN and ACKNOWLEDGED stay separate',()=>{
  const result=assessRulePrecision(Object.freeze({...rule}));
  assert.equal(result.state,'valid');assert.equal(result.concluded,8);assert.equal(result.precisionText,'75,0%');
});
test('No concluded cases are unknown, not 0%; real zero with concluded cases is 0%',()=>{
  const none=assessRulePrecision({...rule,total:5,concernConfirmed:0,dismissed:0,precision:null});
  assert.equal(none.state,'no-conclusion');assert.equal(none.precisionText,'—');
  assert.equal(assessRulePrecision({...rule,total:9,concernConfirmed:0,dismissed:4,precision:0}).precisionText,'0,0%');
});
test('Jackson NON_NULL may omit precision when no concluded cases exist',()=>{
  const payload={total:5,open:3,acknowledged:2,concernConfirmed:0,dismissed:0};
  const result=assessRulePrecision(payload);assert.equal(result.state,'no-conclusion');assert.equal(result.precisionText,'—');assert.equal(result.issue,null);
});
test('Precision is checked at Backend BigDecimal scale4 HALF_UP, not raw floating quotient',()=>{
  assert.equal(assessRulePrecision({...rule,total:8,concernConfirmed:1,dismissed:2,precision:.3333}).state,'valid');
  assert.equal(assessRulePrecision({...rule,total:37,concernConfirmed:1,dismissed:31,precision:.0313}).state,'valid');
});
test('HALF_UP ties use exact integer arithmetic even when floating division falls below a tie',()=>{
  assert.equal(assessRulePrecision({...rule,total:805,concernConfirmed:57,dismissed:743,precision:.0713}).state,'valid');
  assert.equal(assessRulePrecision({...rule,total:805,concernConfirmed:57,dismissed:743,precision:.0712}).state,'unverified');
});
test('Wrong precision and zero-denominator numeric precision cannot silently display a percentage',()=>{
  for(const value of [{...rule,precision:.9},{...rule,total:5,concernConfirmed:0,dismissed:0,precision:0},{...rule,precision:null},{...rule,precision:undefined}]){
    const result=assessRulePrecision(value);assert.equal(result.state,'unverified');assert.equal(result.precisionText,'—');assert.ok(result.issue);
  }
});
test('A precision mismatch at percent-formatting boundary is not swallowed by floating tolerance',()=>{
  const payload={total:2000,open:0,acknowledged:0,concernConfirmed:247,dismissed:1753};
  assert.equal(assessRulePrecision({...payload,precision:.1235}).precisionText,'12,4%');
  assert.equal(assessRulePrecision({...payload,precision:.1234999999999}).state,'unverified');
});
test('Malformed, negative, fractional, nonfinite or missing counts are flagged and never coerced',()=>{
  for(const value of [-1,1.5,NaN,Infinity,undefined,null,'3',Number.MAX_SAFE_INTEGER+1]){
    const result=assessRulePrecision({...rule,open:value});assert.equal(result.state,'unverified');assert.equal(result.precisionText,'—');assert.equal(ruleCount(value),'—');
  }
});
test('Total disagreement and unsafe aggregate counts are flagged',()=>{
  assert.equal(assessRulePrecision({...rule,total:99}).state,'unverified');
  assert.equal(assessRulePrecision({...rule,total:Number.MAX_SAFE_INTEGER,open:Number.MAX_SAFE_INTEGER}).state,'unverified');
});
test('Valid zero counts remain visible; missing data never becomes zero',()=>{
  assert.equal(ruleCount(0),'0');assert.equal(ruleCount(undefined),'—');assert.equal(ruleCount(12345),'12.345');
});
