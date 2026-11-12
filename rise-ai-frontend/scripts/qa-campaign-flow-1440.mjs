/* Campaign Discovery → Detail → Contribution browser E2E.
   Explicitly intercepted API fixtures, never a substitute for live Backend QA.
   Works on Windows (npm.cmd) and POSIX systems. */
/* global document:readonly, window:readonly, getComputedStyle:readonly */
import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const base = 'http://127.0.0.1:5182';
const evidence = resolve('qa-evidence/campaign-flow');
await mkdir(evidence,{recursive:true});
const report = { scope:'Campaign Discovery → Detail → Contribution React / Chromium 1440×900',
  apiMode:'INTERCEPTED_TEST_FIXTURES — NOT LIVE BACKEND', checks:[], requests:[], errors:[], pass:false };
const check=(label,pass,detail)=>{report.checks.push({label,pass:Boolean(pass),detail});if(!pass)throw Error(label+': '+JSON.stringify(detail))};
const command=process.platform==='win32'?'npm.cmd':'npm';
const vite=spawn(command,['run','dev','--','--host','127.0.0.1','--port','5182','--strictPort'],{
  cwd:process.cwd(),stdio:['ignore','pipe','pipe'],detached:process.platform!=='win32',shell:process.platform==='win32'
});
let stderr='';
vite.stderr.on('data',b=>{stderr+=String(b)});
async function waitForVite(){
  for(let i=0;i<100;i++){
    try{const r=await fetch(base+'/campaigns');if(r.ok)return}catch{ /* startup */ }
    await new Promise(r=>setTimeout(r,250));
  }
  throw Error('Vite startup failed: '+stderr.slice(-1200));
}
const active={
  id:'qa-active-id',slug:'qa-active-campaign',title:'Thắp sáng lớp học vùng cao',
  shortDescription:'Cùng tạo không gian học tập công bằng cho cộng đồng.',
  fullDescription:'Một hành trình hỗ trợ giáo dục, thử nghiệm trên Backend QA.',
  problemStatement:'Học sinh còn thiếu sách và thiết bị cơ bản.',
  solutionDescription:'Bổ sung tài liệu và hỗ trợ không gian đọc sách.',
  impactGoals:'Nâng cao điều kiện học tập tại địa phương.',
  beneficiaryDescription:'Học sinh tại vùng cao',
  beneficiaryCount:75,category:'EDUCATION',campaignStatus:'ACTIVE',
  acceptingDonations:true,organization:{name:'Đơn vị giáo dục QA',verificationStatus:'VERIFIED'},
  minimumDonationAmount:50000,targetAmount:10000000,currency:'VND',
  funding:{netRaised:1000000,totalDisbursed:200000},location:'Việt Nam',
};
const closed={...active,id:'qa-closed-id',slug:'qa-closed-campaign',title:'Chiến dịch đã đóng',
  campaignStatus:'PAUSED',acceptingDonations:false};
const milestones=[
  {id:'m2',sequenceNumber:2,title:'Trao tặng sách',milestoneStatus:'PLANNED',plannedAmount:5000000,releasedAmount:0},
  {id:'m1',sequenceNumber:1,title:'Khảo sát nhu cầu',milestoneStatus:'COMPLETED',plannedAmount:500000,releasedAmount:500000},
];
let browser;
try{
  await waitForVite();
  browser=await chromium.launch({headless:true,args:['--no-sandbox']});
  const page=await browser.newPage({viewport:{width:1440,height:900},deviceScaleFactor:1,reducedMotion:'reduce'});
  let firstContributionFailed=false;
  let donationSucceeded=false;
  const postKeys=[];
  page.on('pageerror',e=>report.errors.push('JS: '+String(e)));
  page.on('console',m=>{if(m.type()==='error'&&!m.text().includes('503'))report.errors.push('Console: '+m.text())});
  page.on('requestfailed',req=>{if(req.url().includes('/api/'))report.errors.push('RequestFailed: '+req.url())});
  await page.route('**/api/v1/campaigns?**',route=>{
    const u=new URL(route.request().url());
    report.requests.push({type:'list',params:Object.fromEntries(u.searchParams)});
    const data=[active,closed].filter(c=>c.campaignStatus==='ACTIVE');
    return route.fulfill({status:200,contentType:'application/json',
      body:JSON.stringify({data:{data,currentPage:0,pageSize:9,totalElements:data.length,totalPages:1}})});
  });
  await page.route('**/api/v1/campaigns/slug/*',route=>{
    const name=decodeURIComponent(route.request().url().split('/').pop()??'');
    const found=name===active.slug ? {...active,acceptingDonations:!donationSucceeded} :
      name===closed.slug ? closed : null;
    report.requests.push({type:'detail',slug:name});
    return route.fulfill({status:found?200:404,contentType:'application/json',
      body:JSON.stringify(found?{data:found}:{message:'Not found'})});
  });
  await page.route('**/api/v1/campaigns/*/milestones',route=>
    route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({data:milestones})}));
  await page.route('**/api/v1/auth/login',async route=>{
    const credentials=route.request().postDataJSON();
    report.requests.push({type:'login',username:credentials?.email});
    await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({data:{
      accessToken:'qa-memory-token',refreshToken:'qa-refresh',user:{id:'qa-donor',email:'qa@example.test',roles:['DONOR']}
    }})});
  });
  await page.route('**/api/v1/campaigns/*/donations',async route=>{
    const key=route.request().headers()['idempotency-key'];
    const payload=route.request().postDataJSON();
    postKeys.push(key);
    report.requests.push({type:'donation',key,amount:payload.amount,method:payload.paymentMethod,auth:route.request().headers().authorization});
    if(!firstContributionFailed){
      firstContributionFailed=true;
      return route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({message:'Temporarily unavailable'})});
    }
    donationSucceeded=true;
    await route.fulfill({status:201,contentType:'application/json',body:JSON.stringify({data:{
      id:'qa-donation-id',donationCode:'QA-2026-001',campaignId:active.id,amount:payload.amount,
      currency:'VND',donationStatus:'PENDING',
    }})});
  });

  await page.goto(base+'/campaigns',{waitUntil:'networkidle'});
  await page.getByRole('heading',{name:active.title}).waitFor();
  check('Discovery is sourced from ACTIVE API page and displays a real campaign',
    report.requests.some(r=>r.type==='list'&&r.params.status==='ACTIVE'&&r.params.size==='9') &&
    await page.locator('#lead-section').isVisible(),report.requests.filter(r=>r.type==='list'));
  await page.getByRole('link',{name:/Đọc hồ sơ đầy đủ/}).click();
  await page.waitForURL('**/campaigns/'+active.slug);
  await page.getByRole('heading',{name:active.title,exact:true}).waitFor();
  check('Real React detail route navigates from discovery',page.url().endsWith('/campaigns/'+active.slug),page.url());
  await page.getByRole('heading',{name:'Hành trình theo cột mốc'}).waitFor();
  await page.getByRole('heading',{name:'Khảo sát nhu cầu'}).waitFor();
  const order=await page.locator('.campaign-flow-milestone-head h3').allTextContents();
  check('Backend milestones appear in sequence order',order[0]==='Khảo sát nhu cầu'&&order[1]==='Trao tặng sách',order);
  check('Detail includes narrative, organization and funding simulation disclosure',
    await page.getByRole('heading',{name:'Vấn đề cần giải quyết'}).isVisible() &&
    await page.getByRole('heading',{name:'Đơn vị giáo dục QA'}).isVisible() &&
    await page.getByText('Đây là hệ thống mô phỏng.',{exact:false}).count()>0,null);
  check('Detail Desktop 1440 has no horizontal scroll',
    await page.evaluate(()=>document.documentElement.scrollWidth<=1440),null);
  await page.screenshot({path:resolve(evidence,'campaign-detail-1440.png'),fullPage:true,animations:'disabled'});

  await page.getByRole('link',{name:/Ghi nhận đóng góp mô phỏng/}).click();
  await page.waitForURL('**/login');
  check('Unauthorized donor is redirected to Login while preserving contribution path',
    await page.getByRole('heading',{name:'Đăng nhập Rise AI'}).isVisible(),page.url());
  await page.locator('#email').fill('qa@example.test');
  await page.locator('#password').fill('qa-safe-password');
  await page.getByRole('button',{name:'Đăng nhập'}).click();
  await page.waitForURL('**/campaigns/'+active.slug+'/contribute');
  await page.getByRole('heading',{name:'Đồng hành cùng một sáng kiến'}).waitFor();
  check('Login returns DONOR to the intended contribution route',
    page.url().endsWith('/campaigns/'+active.slug+'/contribute'),page.url());
  await page.screenshot({path:resolve(evidence,'campaign-contribution-1440.png'),fullPage:true,animations:'disabled'});

  const send=page.getByRole('button',{name:/Gửi yêu cầu mô phỏng/});
  check('Simulation acknowledgement required before submit',await send.isDisabled(),null);
  await page.locator('#donation-amount').fill('1000');
  await page.getByRole('checkbox',{name:/Tôi hiểu đây là yêu cầu mô phỏng/}).check();
  check('Minimum simulated amount enforced by browser input constraints',
    await page.locator('#donation-amount').evaluate(el=>!el.checkValidity()),null);
  await page.locator('#donation-amount').fill('120000');
  await send.click();
  await page.getByText('Không ghi nhận được yêu cầu:',{exact:false}).waitFor();
  check('Failed Backend donation displays retryable error without false success',
    !await page.getByText('Đã gửi yêu cầu đóng góp mô phỏng').count()&&postKeys.length===1,postKeys);
  await send.click();
  await page.getByRole('heading',{name:'Đã gửi yêu cầu đóng góp mô phỏng'}).waitFor();
  check('POST carries Authorization, simulated method and correct campaign',
    report.requests.some(r=>r.type==='donation'&&r.auth==='Bearer qa-memory-token'&&r.amount===120000&&r.method==='SIMULATED_BANK_TRANSFER'),
    report.requests.filter(r=>r.type==='donation'));
  check('Retry reused the same Idempotency-Key and only one confirmation appears',
    postKeys.length===2&&postKeys[0]===postKeys[1]&&
    await page.getByText('QA-2026-001').isVisible(),postKeys);
  check('Success survives status change from Backend after cache invalidation',
    await page.getByRole('heading',{name:'Đã gửi yêu cầu đóng góp mô phỏng'}).isVisible() &&
    !await page.locator('#donation-method').count(),null);
  check('No second donation can be submitted after success',
    await page.getByRole('button',{name:/Gửi yêu cầu mô phỏng/}).count()===0,postKeys);
  await page.screenshot({path:resolve(evidence,'campaign-contribution-success-1440.png'),fullPage:true,animations:'disabled'});

  const fresh=await browser.newPage({viewport:{width:1440,height:900},deviceScaleFactor:1});
  fresh.on('pageerror',e=>report.errors.push('FreshPage JS: '+String(e)));
  await fresh.route('**/api/v1/campaigns/slug/*',route=>{
    const suffix=route.request().url().split('/').pop();
    const found=suffix===closed.slug?closed:null;
    return route.fulfill({status:found?200:404,contentType:'application/json',
      body:JSON.stringify(found?{data:found}:{message:'No such campaign'})});
  });
  await fresh.goto(base+'/campaigns/'+closed.slug);
  await fresh.getByRole('heading',{name:closed.title}).waitFor();
  check('Paused campaign detail correctly blocks contribution CTA',
    await fresh.getByText('Chiến dịch hiện chưa tiếp nhận đóng góp.').isVisible()&&
    await fresh.getByRole('link',{name:/Ghi nhận đóng góp mô phỏng/}).count()===0,null);
  await fresh.goto(base+'/campaigns/no-such-campaign');
  await fresh.getByText('Không tìm thấy chiến dịch').waitFor();
  check('Missing campaign shows truthful 404 empty state',await fresh.getByText('Hồ sơ không tồn tại hoặc chưa được công bố.').isVisible(),null);

  const s=await page.evaluate(()=>({scrollWidth:document.documentElement.scrollWidth,viewportWidth:window.innerWidth}));
  check('Contribution Desktop layout has no horizontal scroll',s.scrollWidth<=s.viewportWidth,s);
  check('No uncaught JavaScript or unexpected browser errors',report.errors.length===0,report.errors);
  report.pass=true;
}catch(error){report.failure=String(error);console.error(error);process.exitCode=1}
finally{
  await writeFile(resolve(evidence,'qa-report.json'),JSON.stringify(report,null,2),'utf8');
  if(browser) await browser.close();
  try{if(vite.pid && process.platform!=='win32')process.kill(-vite.pid,'SIGTERM');else vite.kill('SIGTERM')}
  catch{vite.kill('SIGTERM')}
  vite.stdout?.destroy();vite.stderr?.destroy();
}
console.log(JSON.stringify({pass:report.pass,checks:report.checks.length,errors:report.errors.length,failure:report.failure},null,2));
process.exit(report.pass?0:1);
