/* global document */
/**
 * PR #42 U1–U5 visual acceptance at Desktop 1440x900 DPR 1.
 * REAL: Spring Boot + disposable MySQL (off-chain), never intercept requests.
 * FIXTURE: Fuji visual-only contract responses, intercepted explicitly; not evidence of live Fuji transactions.
 * No source mutation, wallet actions, financial requests or production access.
 */
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

const origin=process.env.RISE_STAGING_BASE_URL||'http://127.0.0.1:5174';
if(origin!=='http://127.0.0.1:5174')throw new Error('PR42 QA: local isolated staging only');
const dir=resolve('visual-qa/staging-react-real-api');
await mkdir(dir,{recursive:true});
const report={commit:process.env.GITHUB_SHA||'local',viewport:'1440x900@1',realBackend:'Spring Boot + isolated MySQL 8.4',fujiMode:'INTERCEPTED_VISUAL_FIXTURE_NOT_LIVE_FUJI',checks:[],screenshots:[],modes:{},pass:false};
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
const context=()=>browser.newContext({viewport:{width:1440,height:900},deviceScaleFactor:1,reducedMotion:'reduce'});
const errors=[];
function expect(name,truth,detail){const ok=Boolean(truth);report.checks.push({name,pass:ok,detail:detail??null});console.log((ok?'PASS ':'FAIL ')+name);if(!ok)throw new Error(name+': '+JSON.stringify(detail));}
const campaignList=async()=>{const r=await fetch(origin+'/api/v1/campaigns?status=ACTIVE&page=0&size=3');expect('Real campaign list 200',r.status===200,r.status);const j=await r.json();const row=j.data?.data?.find(x=>x.id&&x.slug);expect('Real active campaign with UUID and slug',Boolean(row));return row;};
const getJSON=async(path)=>{const r=await fetch(origin+path);expect('Real '+path+' status 200',r.status===200,r.status);return (await r.json()).data;};
async function capture(page,name){
  await page.evaluate(()=>document.fonts.ready);
  await page.waitForTimeout(260);
  const w=await page.evaluate(()=>document.documentElement.scrollWidth);
  expect(name+' no horizontal overflow',w<=1440,w);
  const file=name+'.png';
  if(name.includes('fixture'))await page.evaluate(()=>{
    const label=document.createElement('div');
    label.textContent='QA FIXTURE FUJI · DỮ LIỆU GIẢ LẬP · KHÔNG PHẢI GIAO DỊCH THẬT';
    label.style.cssText='position:fixed;top:8px;right:8px;z-index:99999;background:#5d230a;color:#fff;padding:8px 12px;font:700 13px Arial;border-radius:7px;pointer-events:none';
    document.body.appendChild(label);
  });
  await page.screenshot({path:resolve(dir,file),fullPage:true,animations:'disabled'});
  report.screenshots.push({file,source:name.includes('fixture')?'INTERCEPTED_FUJI_FIXTURE':'REAL_BACKEND_OFFCHAIN'});
}
function track(page){page.on('pageerror',e=>errors.push(String(e)));}
async function login(page,email){
  await page.goto(origin+'/login',{waitUntil:'domcontentloaded'});
  await page.getByLabel('Địa chỉ email').fill(email);
  await page.getByLabel('Mật khẩu').fill('Demo@12345');
  await page.getByRole('button',{name:/Đăng nhập/}).click();
  await page.waitForURL(u=>u.pathname!=='/login',{timeout:25000});
}
const tx='0x'+'a'.repeat(64),tx2='0x'+'b'.repeat(64);
const explorer='https://testnet.snowtrace.io/tx/';
function mockFuji(id){
 const chain={onChain:true,available:true,network:'FUJI',chainId:43113,
   escrowContract:'0x'+'1'.repeat(40),tokenContract:'0x'+'2'.repeat(40),anchorContract:'0x'+'3'.repeat(40),
   contractVersion:'1',paused:false,guardianAddress:'0x'+'4'.repeat(40),
   explorerTxUrl:explorer,explorerAddressUrl:'https://testnet.snowtrace.io/address/'};
 const chainCampaign={onChain:true,campaignId:id,openStatus:'CONFIRMED',explorerTxUrl:explorer,
   disbursements:[
    {disbursementId:'00000000-0000-4000-8000-000000000001',requestCode:'QA-FUJI-01',requestStatus:'APPROVED',chainStatus:'IN_OBJECTION_WINDOW',releaseAt:new Date(Date.now()+3600000).toISOString(),reserveTxHash:tx,queueTxHash:tx2,openGuardianHolds:0},
    {disbursementId:'00000000-0000-4000-8000-000000000002',requestCode:'QA-FUJI-02',requestStatus:'APPROVED',chainStatus:'HELD_BY_GUARDIAN',releaseAt:new Date(Date.now()+3600000).toISOString(),reserveTxHash:tx,openGuardianHolds:1},
    {disbursementId:'00000000-0000-4000-8000-000000000003',requestCode:'QA-FUJI-03',requestStatus:'RELEASED',chainStatus:'RELEASED_ON_CHAIN',releaseTxHash:tx2,payoutTxHash:tx,receiptConfirmedAt:new Date().toISOString()}
   ],
   transactions:[{action:'RESERVE',referenceId:'00000000-0000-4000-8000-000000000001',status:'CONFIRMED',txHash:tx,explorerUrl:explorer+tx,recordedAt:new Date().toISOString()},
    {action:'QUEUE_RELEASE',referenceId:'00000000-0000-4000-8000-000000000001',status:'CONFIRMED',txHash:tx2,explorerUrl:explorer+tx2,recordedAt:new Date().toISOString()}]};
 const reserves={onChain:true,available:true,attested:true,period:9,attestedAt:new Date().toISOString(),attester:'0x'+'5'.repeat(40),bankBalance:940000,heldAtAttestation:950000,gap:10000,currentHeld:940000,statementHash:'0x'+'6'.repeat(64)};
 const anchors={onChain:true,anchorContract:chain.anchorContract,anchors:[
  {kind:'LEDGER',status:'CONSISTENT',anchoredRecordCount:14,currentRecordCount:14,anchoredAt:new Date().toISOString(),txHash:tx,explorerUrl:explorer+tx},
  {kind:'AUDIT',status:'MISMATCH',anchoredRecordCount:10,currentRecordCount:12,anchoredAt:new Date().toISOString(),txHash:tx2,explorerUrl:explorer+tx2},
  {kind:'FINDINGS',status:'NOT_ANCHORED',currentRecordCount:3}]};
 const activity={onChain:true,available:true,source:'AVALANCHE_DATA_API',escrowContract:chain.escrowContract,items:[
  {txHash:tx,method:'reserve',signer:'PLATFORM',from:'0x'+'7'.repeat(40),timestamp:new Date().toISOString(),blockNumber:12345,success:true,check:'RECORDED',rvndToBeneficiary:null,explorerUrl:explorer+tx},
  {txHash:tx2,method:'recordPayout',signer:'PLATFORM',from:'0x'+'8'.repeat(40),timestamp:new Date().toISOString(),blockNumber:12346,success:true,check:'UNRECORDED',rvndToBeneficiary:200000,explorerUrl:explorer+tx2}],nextPageToken:null,unrecordedCount:1};
 return async route=>{
  const url=new URL(route.request().url());
  let data;
  if(url.pathname==='/api/v1/transparency/chain')data=chain;
  else if(url.pathname.startsWith('/api/v1/transparency/campaigns/'))data=chainCampaign;
  else if(url.pathname==='/api/v1/transparency/reserves')data=reserves;
  else if(url.pathname==='/api/v1/transparency/anchors')data=anchors;
  else if(url.pathname==='/api/v1/transparency/activity')data=activity;
  if(data===undefined){await route.continue();return;}
  await route.fulfill({status:200,contentType:'application/json',headers:{'Cache-Control':'no-store'},body:JSON.stringify({code:200,message:'OK',data})});
 };
}
async function adminEscrow(page,id){
 await page.locator('a[href="/admin"]').first().click();
 await page.waitForURL('**/admin');
 await page.locator('a[href="/admin/escrow"]').first().click();
 await page.waitForURL('**/admin/escrow');
 await page.getByLabel('UUID chiến dịch').fill(id);
 await page.getByRole('button',{name:'Xem sổ'}).click();
 await page.locator('.chain-panel-admin').waitFor({timeout:25000});
}
try{
 const row=await campaignList();
 report.modes.real={engine:'simulated',intercepts:0,realCampaignId:row.id};
 const actual=await getJSON('/api/v1/transparency/chain');
 expect('Real backend off-chain (no gateway)',actual?.onChain===false,actual);
 const campaignChain=await getJSON('/api/v1/transparency/campaigns/'+encodeURIComponent(row.id));
 expect('Real campaign transparency is off-chain',campaignChain?.onChain===false,campaignChain?.onChain);
 const rc=await context();const real=await rc.newPage();track(real);
 await real.goto(origin+'/campaigns/'+encodeURIComponent(row.slug),{waitUntil:'domcontentloaded'});
 await real.getByText('Ký quỹ mô phỏng · Không phải Fuji').waitFor({timeout:15000});
 expect('U1 REAL badge correctly NOT Fuji',await real.getByText('Ký quỹ mô phỏng · Không phải Fuji').count()>0);
 await real.getByText('chưa có lịch sử giao dịch on-chain',{exact:false}).waitFor({timeout:15000});
 expect('U2 REAL off-chain timeline honest',await real.getByText('chưa có lịch sử giao dịch on-chain').count()>0);
 await capture(real,'pr42-u1-u2-real-offchain-campaign-1440');
 await real.goto(origin+'/transparency',{waitUntil:'domcontentloaded'});
 await real.getByText('Chưa thể công bố dữ liệu Fuji').waitFor();
 expect('U4 REAL off-chain no fabricated Fuji data',await real.getByText('Chưa thể công bố dữ liệu Fuji').isVisible());
 await capture(real,'pr42-u4-real-offchain-transparency-1440');
 await rc.close();

 const dc=await context();const donor=await dc.newPage();track(donor);
 await login(donor,'donor1@riseai.example');
 await donor.locator('a[href="/donor"]').first().click();
 await donor.waitForURL('**/donor');
 await donor.locator('.donor-receipt-b2').first().waitFor({timeout:25000});
 expect('U3 donor uses real donation code and no on-chain hash claim',await donor.locator('.donor-receipt-b2').getByText('Chưa có biên nhận on-chain riêng cho khoản đóng góp này', {exact:false}).count()>0);
 await capture(donor,'pr42-u3-real-donor-receipt-1440');await dc.close();

 const ac=await context();const admin=await ac.newPage();track(admin);
 await login(admin,'admin@riseai.example');
 await adminEscrow(admin,row.id);
 await admin.getByText('Chiến dịch đang dùng sổ ký quỹ mô phỏng', {exact:false}).waitFor();
 expect('U5 REAL admin on-chain panel off-chain message',await admin.locator('.chain-panel-admin').count()===1);
 await capture(admin,'pr42-u5-real-admin-offchain-1440');await ac.close();

 const fc=await context();await fc.route('**/api/v1/transparency/**',mockFuji(row.id));
 report.modes.fujiFixture={chainId:43113,intercepts:'ONLY /api/v1/transparency/**',liveFuji:false};
 const fuji=await fc.newPage();track(fuji);
 await fuji.goto(origin+'/campaigns/'+encodeURIComponent(row.slug),{waitUntil:'domcontentloaded'});
 await fuji.getByText('Ký quỹ on-chain · Avalanche Fuji').waitFor({timeout:25000});
 await fuji.getByText('Tạm dừng bởi Guardian độc lập').waitFor();
 await fuji.getByText('Người nhận đã xác nhận:',{exact:false}).waitFor();
 expect('U1 FIXTURE Fuji badge rendered',await fuji.getByText('Ký quỹ on-chain · Avalanche Fuji').count()===1);
 expect('U2 FIXTURE guardian hold and payout receipt displayed',await fuji.getByText('Tạm dừng bởi Guardian độc lập').count()>0);
 expect('U6 FIXTURE Snowtrace links external safe',await fuji.locator('a[href^="https://testnet.snowtrace.io/tx/"][target="_blank"][rel*="noopener"]').count()>0);
 await capture(fuji,'pr42-u1-u2-fixture-fuji-campaign-1440');

 await fuji.goto(origin+'/transparency',{waitUntil:'domcontentloaded'});
 await fuji.getByText('Avalanche Fuji · Chain ID 43113').waitFor();
 await fuji.getByText('Không khớp — cần xem xét').waitFor();
 await fuji.getByText('Ngoài sổ — cần xem xét').waitFor();
 expect('U4 FIXTURE reserve attestation heading shown',await fuji.getByRole('heading',{name:'Chứng thực số dư',exact:true}).isVisible());
 expect('U4 FIXTURE activity history heading shown',await fuji.getByRole('heading',{name:'Lịch sử giao dịch',exact:true}).isVisible());
 expect('U4 FIXTURE Data API activity label shown',await fuji.locator('.chain-public-full').getByText('05 / DATA API · NHẬT KÝ ĐỐI CHIẾU',{exact:true}).isVisible());
 await capture(fuji,'pr42-u4-fixture-fuji-transparency-1440');
 await fc.close();

 const af=await context();await af.route('**/api/v1/transparency/**',mockFuji(row.id));
 const adm=await af.newPage();track(adm);await login(adm,'admin@riseai.example');
 await adminEscrow(adm,row.id);
 await adm.getByText('Tạm dừng bởi Guardian độc lập').waitFor();
 await adm.getByText('Cần xem xét:').waitFor();
 expect('U5 FIXTURE admin pending and warning count',await adm.locator('.chain-panel-admin .chain-state-warning').count()>=1);
 await capture(adm,'pr42-u5-fixture-fuji-admin-1440');
 await af.close();
 expect('No uncaught browser page errors',errors.length===0,errors);
 report.pass=true;
}catch(e){report.failure=String(e);console.error('PR42 targeted visual QA failed',e);}
finally{await writeFile(resolve(dir,'pr42-visual-qa-results.json'),JSON.stringify(report,null,2));await browser.close();}
console.log('PR42 ONCHAIN VISUAL QA '+(report.pass?'PASS':'FAIL'));
if(!report.pass)process.exitCode=1;
