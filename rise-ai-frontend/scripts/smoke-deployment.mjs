// Safe, read-only live smoke for a deployed Rise AI frontend + same-origin Backend proxy.
const raw=process.env.RISE_FE06_BASE_URL;
if(!raw){console.error('Set RISE_FE06_BASE_URL (e.g. http://127.0.0.1:5174); no requests made.');process.exit(2);}
let root;
try{
  root=new URL(raw);
  if(!['http:','https:'].includes(root.protocol)||root.username||root.password||root.search||root.hash||root.pathname!=='/'){
    throw Error('Expected an HTTP(S) origin with no path, credentials, query or fragment');
  }
  if(root.protocol==='http:'&&!['127.0.0.1','localhost','[::1]'].includes(root.hostname)){
    throw Error('Plain HTTP only permitted on loopback; use HTTPS for remote staging');
  }
}catch(e){console.error('Invalid RISE_FE06_BASE_URL:',e.message);process.exit(2);}
const base=root.origin;
let failures=0;
async function check(name,path,expected,validate){
  try{
    const response=await fetch(base+path,{redirect:'manual',signal:AbortSignal.timeout(10000),headers:{Accept:'application/json, text/html'}});
    const body=await response.text();
    const pass=expected.includes(response.status)&&(!validate||validate(response,body));
    console.log((pass?'PASS':'FAIL')+' '+name+' ('+response.status+')');
    if(!pass)failures++;
  }catch(e){failures++;console.error('FAIL '+name+' ('+e.name+')');}
}
if(process.env.RISE_FE06_TARGET==='docker')await check('Container health','/healthz',[200],(r,body)=>body.trim()==='ok');
for(const route of ['/','/campaigns','/login','/admin/monitoring','/admin/audit']){
  await check('SPA deep link '+route,route,[200],(r,body)=>
    (r.headers.get('content-type')||'').includes('text/html')&&body.includes('id="root"'));
}
await check('Public Backend campaign API','/api/v1/campaigns?status=ACTIVE&page=0&size=1',[200],(r,body)=>{
  try{const value=JSON.parse(body);return value&&typeof value==='object'&&Object.hasOwn(value,'data');}
  catch{return false;}
});
await check('Admin API anonymous denial','/api/v1/admin/monitoring/findings?page=0&size=1',[401,403],(r,body)=>{
  try{const value=JSON.parse(body);return !value||!Object.hasOwn(value,'data')||value.data==null;}
  catch{return true;}
});
console.log(failures?'FE-06 smoke FAILED ('+failures+' checks)':'FE-06 smoke PASS (read-only; no credentials or mutations)');
if(failures)process.exitCode=1;
