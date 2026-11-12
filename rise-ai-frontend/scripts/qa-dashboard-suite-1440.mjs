/* Role Dashboard Suite browser QA — Desktop 1440×900.
   Uses actual Vite React routes with intercepted Backend fixtures.
   Does NOT attest to isolated Spring Boot/MySQL behavior. */
/* global document:readonly, window:readonly */
import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const base='http://127.0.0.1:5186';
const evidence=resolve('qa-evidence/dashboard-suite');
await mkdir(evidence,{recursive:true});
const report={scope:'Donor / Creator / Admin Dashboard — Desktop 1440×900 DPR1',
  backend:'INTERCEPTED FIXTURES ONLY',checks:[],calls:[],errors:[],pass:false};
function check(label,ok,detail){report.checks.push({label,pass:Boolean(ok),detail});if(!ok)throw Error(label+': '+JSON.stringify(detail))}
const server=spawn(process.platform==='win32'?'npm.cmd':'npm',
  ['run','dev','--','--host','127.0.0.1','--port','5186','--strictPort'],
  {cwd:process.cwd(),stdio:['ignore','pipe','pipe'],detached:process.platform!=='win32',shell:process.platform==='win32'});
let serverError='';
server.stderr.on('data',b=>{serverError+=String(b)});
async function ready(){for(let i=0;i<100;i++){try{const r=await fetch(base+'/login');if(r.ok)return}catch{/* startup */}await new Promise(r=>setTimeout(r,250))}throw Error('Vite failed: '+serverError.slice(-1200))}
const envelope=(data,status=200)=>({status,contentType:'application/json',body:JSON.stringify({data})});
const pageList=(data,totalElements=data.length,totalPages=1,currentPage=0)=>
  ({data,currentPage,totalElements,totalPages,pageSize:10});
const donorRecord=(id,status)=>({
  id,donationCode:'DON-'+id,campaignId:'qa-campaign-id',currency:'VND',
  amount:150000,refundedAmount:0,donationStatus:status,createdAt:'2026-11-01T00:00:00Z',
});
const organization={id:'qa-org-id',name:'Tổ chức Cộng đồng QA',
  organizationType:'COMMUNITY_GROUP',verificationStatus:'UNVERIFIED',contactEmail:'creator@example.test'};
const creatorCampaign={id:'qa-draft-id',title:'Sách cho em QA',campaignStatus:'DRAFT',
  targetAmount:6000000,currency:'VND',organization:{name:organization.name}};
const adminDonation={id:'qa-admin-don-id',donationCode:'DON-QUEUE',amount:200000,currency:'VND',donationStatus:'PENDING'};
let browser;
try{
  await ready();
  browser=await chromium.launch({headless:true,args:['--no-sandbox']});
  const make=async(role)=>{
    const context=await browser.newContext({viewport:{width:1440,height:900},deviceScaleFactor:1,reducedMotion:'reduce'});
    const page=await context.newPage();
    page.on('pageerror',e=>report.errors.push(role+' JS: '+String(e)));
    page.on('console',m=>{if(m.type()==='error'&&!m.text().includes('503'))report.errors.push(role+' Console: '+m.text())});
    let refundPosts=0;
    const keys=[];
    await page.route('**/api/v1/**',async route=>{
      const u=new URL(route.request().url()),p=u.pathname,method=route.request().method(),params=u.searchParams;
      report.calls.push({role,method,path:p,query:u.search});
      if(p==='/api/v1/auth/login'&&method==='POST'){
        return route.fulfill(envelope({accessToken:'qa-'+role.toLowerCase()+'-token',
          user:{id:'qa-'+role.toLowerCase(),email:role.toLowerCase()+'@example.test',roles:[role]}}));
      }
      if(role==='DONOR'){
        if(p==='/api/v1/donations/me'&&method==='GET'){
          const n=Number(params.get('page')??0),item=n===0?donorRecord('pending','PENDING'):donorRecord('confirmed','CONFIRMED');
          return route.fulfill(envelope(pageList([item],2,2,n)));
        }
        if(p==='/api/v1/campaigns/qa-campaign-id'&&method==='GET')
          return route.fulfill(envelope({id:'qa-campaign-id',title:'Chiến dịch đóng góp QA',slug:'donor-qa',campaignStatus:'ACTIVE'}));
        if((p==='/api/v1/refunds/me'||p==='/api/v1/disputes/me')&&method==='GET')
          return route.fulfill(envelope(pageList([])));
        if(p==='/api/v1/donations/confirmed/refunds'&&method==='POST'){
          refundPosts++;
          keys.push(route.request().headers()['idempotency-key']);
          if(refundPosts===1)return route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({message:'Try again'})});
          return route.fulfill(envelope({id:'qa-refund-id',refundStatus:'REQUESTED',amount:100000},201));
        }
        if(p==='/api/v1/campaigns/qa-campaign-id/disputes'&&method==='POST')
          return route.fulfill(envelope({id:'qa-dispute-id',disputeStatus:'OPEN'},201));
      }
      if(role==='CAMPAIGN_CREATOR'){
        if(p==='/api/v1/organizations/mine'&&method==='GET')
          return route.fulfill(envelope(pageList([organization])));
        if(p==='/api/v1/campaigns/mine'&&method==='GET')
          return route.fulfill(envelope(pageList([creatorCampaign])));
        if(p==='/api/v1/organizations'&&method==='POST')
          return route.fulfill(envelope({...organization,...route.request().postDataJSON(),id:'qa-new-org-id'},201));
        if(p==='/api/v1/organizations/qa-org-id/submit-verification'&&method==='POST')
          return route.fulfill(envelope({...organization,verificationStatus:'PENDING_REVIEW'}));
      }
      if(role==='ADMIN'){
        if(p==='/api/v1/admin/statistics'&&method==='GET')
          return route.fulfill(envelope({pendingDonations:3,openRefundRequests:2,openDisbursementRequests:1,
            openDisputes:4,totalConfirmedContributions:2500000,totalDisbursed:100000,
            totalRefunded:0,currency:'VND',simulated:true}));
        if(p==='/api/v1/organizations'&&method==='GET')
          return route.fulfill(envelope(pageList([{...organization,verificationStatus:'PENDING_REVIEW'}])));
        if(p==='/api/v1/admin/donations'&&method==='GET')
          return route.fulfill(envelope(pageList([adminDonation])));
        if(p.startsWith('/api/v1/admin/')&&method==='GET')
          return route.fulfill(envelope(pageList([])));
      }
      return route.fulfill({status:404,contentType:'application/json',body:JSON.stringify({message:'Unhandled QA route '+p})});
    });
    await page.goto(base+'/login',{waitUntil:'domcontentloaded'});
    await page.locator('#email').fill(role.toLowerCase()+'@example.test');
    await page.locator('#password').fill('qa-password');
    await page.getByRole('button',{name:'Đăng nhập'}).click();
    await page.waitForURL('**/workspace');
    return {page,context,keys};
  };
  const donor=await make('DONOR');
  await donor.page.getByRole('link',{name:'Quản lý đóng góp của tôi'}).click();
  await donor.page.getByRole('heading',{name:'Hành trình đóng góp'}).waitFor();
  check('DONOR metric shows total Backend records, not current page records',
    await donor.page.locator('.dashboard-metric').first().locator('strong').textContent()==='2',null);
  check('DONOR Desktop dashboard width stays within 1440',
    await donor.page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),null);
  await donor.page.screenshot({path:resolve(evidence,'donor-dashboard-1440.png'),fullPage:true,animations:'disabled'});
  await donor.page.getByRole('tab',{name:/Phản ánh/}).click();
  await donor.page.locator('.dashboard-page-picker').getByRole('button',{name:/Sau/}).click();
  await donor.page.locator('#dispute-campaign option[value="qa-campaign-id"]').waitFor();
  check('DONOR dispute selector can find confirmed donation on next history page',
    await donor.page.locator('#dispute-campaign option[value="qa-campaign-id"]').count()===1,
    report.calls.filter(c=>c.role==='DONOR'&&c.path==='/api/v1/donations/me'));
  await donor.page.getByRole('tab',{name:/Đóng góp/}).click();
  await donor.page.getByRole('button',{name:'Yêu cầu hoàn tiền'}).click();
  await donor.page.locator('#refund-amount').fill('100000');
  await donor.page.locator('#refund-reason').fill('Hoàn tiền thử nghiệm QA');
  await donor.page.getByRole('button',{name:'Gửi yêu cầu hoàn tiền'}).click();
  await donor.page.getByText('Try again').waitFor();
  await donor.page.getByRole('button',{name:'Gửi yêu cầu hoàn tiền'}).click();
  await donor.page.getByText('Đã gửi yêu cầu hoàn tiền mô phỏng').waitFor();
  check('DONOR refund retries reuse the same idempotency key',donor.keys.length===2&&
    Boolean(donor.keys[0])&&donor.keys[0]===donor.keys[1],donor.keys);
  await donor.context.close();

  const creator=await make('CAMPAIGN_CREATOR');
  await creator.page.getByRole('link',{name:'Quản lý chiến dịch Creator'}).click();
  await creator.page.getByRole('heading',{name:'Quản lý hành trình thiện nguyện'}).waitFor();
  check('CREATOR shows organization and campaign totals from Backend pages',
    await creator.page.locator('.dashboard-metric strong').allTextContents().then(x=>x[0]==='1'&&x[1]==='1'),null);
  await creator.page.screenshot({path:resolve(evidence,'creator-dashboard-1440.png'),fullPage:true,animations:'disabled'});
  await creator.page.getByRole('button',{name:/Thêm tổ chức/}).click();
  await creator.page.locator('#org-name').fill('  Nhóm thiện nguyện QA  ');
  await creator.page.locator('#org-email').fill(' qa-contact@example.test ');
  await creator.page.getByRole('button',{name:'Lưu tổ chức'}).click();
  await creator.page.getByText('Đã lưu tổ chức trong Backend.').waitFor();
  check('CREATOR creates organization and shows Backend success notification',
    report.calls.some(c=>c.role==='CAMPAIGN_CREATOR'&&c.path==='/api/v1/organizations'&&c.method==='POST'),null);
  check('CREATOR protected management CTA reaches creator campaign',
    await creator.page.locator('a[href="/creator/campaigns/qa-draft-id"]').getAttribute('href')==='/creator/campaigns/qa-draft-id',null);
  check('CREATOR Desktop dashboard width stays within 1440',
    await creator.page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),null);
  await creator.context.close();

  const admin=await make('ADMIN');
  await admin.page.getByRole('link',{name:'Trung tâm quản trị'}).click();
  await admin.page.getByRole('heading',{name:'Trung tâm xét duyệt'}).waitFor();
  await admin.page.getByText('Tổ chức Cộng đồng QA').waitFor();
  check('ADMIN queues source organizations and statistics from Backend',
    report.calls.some(c=>c.role==='ADMIN'&&c.path==='/api/v1/admin/statistics')&&
    report.calls.some(c=>c.role==='ADMIN'&&c.path==='/api/v1/organizations'&&c.query.includes('status=PENDING_REVIEW')),null);
  await admin.page.screenshot({path:resolve(evidence,'admin-dashboard-1440.png'),fullPage:true,animations:'disabled'});
  await admin.page.getByRole('button',{name:'Đóng góp'}).click();
  await admin.page.getByText('DON-QUEUE').waitFor();
  check('ADMIN queue switches to PENDING donations with correct Backend filtering',
    report.calls.some(c=>c.role==='ADMIN'&&c.path==='/api/v1/admin/donations'&&c.query.includes('status=PENDING')),null);
  await Promise.all([
    admin.page.waitForResponse(res=>res.url().includes('/api/v1/admin/statistics')),
    admin.page.getByRole('button',{name:'Làm mới Dashboard'}).click(),
  ]);
  check('ADMIN refresh exposes statistics and records invalidation',
    report.calls.filter(c=>c.role==='ADMIN'&&c.path==='/api/v1/admin/statistics').length>=2,null);
  check('ADMIN Desktop dashboard width stays within 1440',
    await admin.page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),null);
  await admin.context.close();
  check('All role dashboards rendered without unhandled JavaScript errors',report.errors.length===0,report.errors);
  report.pass=true;
}catch(error){report.failure=String(error);console.error(error);process.exitCode=1}
finally{
  await writeFile(resolve(evidence,'qa-report.json'),JSON.stringify(report,null,2),'utf8');
  if(browser)await browser.close();
  try{if(server.pid&&process.platform!=='win32')process.kill(-server.pid,'SIGTERM');else server.kill('SIGTERM')}catch{server.kill('SIGTERM')}
  server.stdout?.destroy();server.stderr?.destroy();
}
console.log(JSON.stringify({pass:report.pass,checks:report.checks.length,failure:report.failure},null,2));
process.exit(report.pass?0:1);
