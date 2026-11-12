/* Rise AI — Functional Pages real-browser visual acceptance, 1440×900 DPR1.
   Runs on configured QA/staging Frontend+Backend (no intercept, no mock data).
   Do NOT use production credentials or production writes.
   Required RISE_QA_BASE_URL; optional user roles and record IDs described below. */
/* global window, document, PopStateEvent */
import { chromium } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';

const base = process.env.RISE_QA_BASE_URL?.replace(/\/$/,'');
if(!base)throw new Error('Set RISE_QA_BASE_URL to your isolated Frontend connected to real Backend QA.');
const root=resolve('qa-evidence/functional-pages');
await mkdir(root,{recursive:true});
const report={mode:'REAL_BROWSER_REAL_BACKEND_NO_INTERCEPTION',viewport:'1440x900 DPR1',
  base:new URL(base).origin,head:process.env.GIT_HEAD??'unknown',
  checks:[],blocked:[],errors:[],pass:false};
const routes={
  guest:[['login','/login','rise-entry-page'],['not-found','/rise-qa-nonexistent-path','rise-notfound-page']],
  donor:[['workspace','/workspace','rise-workspace-page'],['dispute-thread',process.env.RISE_QA_DISPUTE_ID?
    '/donor/disputes/'+encodeURIComponent(process.env.RISE_QA_DISPUTE_ID):null,'deep-donor-dispute']],
  creator:[['workspace','/workspace','rise-workspace-page'],['campaign-editor-new','/creator/campaigns/new','deep-creator-editor'],
    ['campaign-detail',process.env.RISE_QA_CREATOR_CAMPAIGN_ID?
      '/creator/campaigns/'+encodeURIComponent(process.env.RISE_QA_CREATOR_CAMPAIGN_ID):null,'deep-creator-campaign'],
    ['campaign-editor',process.env.RISE_QA_CREATOR_CAMPAIGN_ID?
      '/creator/campaigns/'+encodeURIComponent(process.env.RISE_QA_CREATOR_CAMPAIGN_ID)+'/edit':null,'deep-creator-editor']],
  admin:[['workspace','/workspace','rise-workspace-page'],['review',process.env.RISE_QA_REVIEW_KIND&&process.env.RISE_QA_REVIEW_ID?
      '/admin/review/'+encodeURIComponent(process.env.RISE_QA_REVIEW_KIND)+'/'+encodeURIComponent(process.env.RISE_QA_REVIEW_ID):null,'deep-admin-review'],
    ['escrow','/admin/escrow','deep-admin-escrow'],
    ['monitoring','/admin/monitoring','deep-admin-monitoring'],
    ['finding',process.env.RISE_QA_FINDING_ID?
      '/admin/monitoring/findings/'+encodeURIComponent(process.env.RISE_QA_FINDING_ID):null,'deep-admin-finding'],
    ['delivery','/admin/monitoring/delivery','deep-admin-delivery'],
    ['audit','/admin/audit','deep-admin-audit']]
};
let browser;
async function go(page,path){
  // Do not reload: Rise AI auth intentionally holds Bearer tokens only in memory.
  await page.evaluate(next=>{
    window.history.pushState({},'',next);
    window.dispatchEvent(new PopStateEvent('popstate'));
  },path);
  await page.waitForURL(base+path,{timeout:12000});
}
async function recordPage(page,role,name,path,klass){
  if(!path){report.blocked.push({role,name,reason:'Missing QA record ID in environment.'});return}
  try{
    await go(page,path);
    await page.locator('.rise-deep-page.'+klass).waitFor({timeout:14000});
    await page.locator('h1').first().waitFor({state:'visible',timeout:14000});
    await page.waitForFunction(() => [...document.querySelectorAll('.rise-deep-page [role=status]')]
      .every(element => !/^Đang (tải|xác minh)/.test(element.textContent?.trim() ?? '')));
    const geometry=await page.evaluate(async()=>{
      await document.fonts.ready;
      window.scrollTo({top:0,behavior:'instant'});
      const content=document.querySelector('.rise-deep-page');
      const art=content?.querySelector('.rise-entry-art');
      const grid=content?.querySelector('.rise-workspace-grid');
      const singleCard=grid?.querySelector('.rise-workspace-card:only-child');
      return {
        scroll:document.documentElement.scrollWidth,
        width:window.innerWidth,
        height:window.innerHeight,
        dpr:window.devicePixelRatio,
        title:content?.querySelector('h1')?.textContent?.trim().slice(0,150)??'',
        errors:[...content.querySelectorAll('[role=alert]')].map(element=>element.textContent?.trim()),
        hasFooter:Boolean(document.querySelector('footer')),
        loginLandscape:art ? window.getComputedStyle(art).backgroundImage.includes('linear-gradient') : null,
        workspaceCardRatio:singleCard ? singleCard.clientWidth/grid.clientWidth : null,
      };
    });
    if(klass==='rise-entry-page'){
      await page.locator('#email').focus();
      await page.keyboard.press('Tab');
      const password=page.locator('#password');
      geometry.keyboardFocus=await password.evaluate(el=>{
        const outline=window.getComputedStyle(el);
        return document.activeElement===el && el.matches(':focus-visible') &&
          outline.outlineStyle!=='none' && parseFloat(outline.outlineWidth)>=2;
      });
      await password.blur();
    }
    const okay=geometry.width===1440 && geometry.height===900 && geometry.dpr===1 &&
      geometry.scroll<=geometry.width && Boolean(geometry.title) && geometry.hasFooter &&
      geometry.errors.length===0 && geometry.loginLandscape!==false && geometry.keyboardFocus!==false &&
      (geometry.workspaceCardRatio===null || geometry.workspaceCardRatio>=0.95);
    report.checks.push({role,name,path,pass:okay,geometry});
    await page.screenshot({path:join(root,role+'-'+name+'-1440.png'),
      fullPage:true,animations:'disabled'});
    if(!okay)report.errors.push(role+':'+name+' failed visual or loaded-state checks');
  }catch(e){
    report.errors.push(role+':'+name+' '+String(e));
    report.checks.push({role,name,path,pass:false,error:String(e)});
  }
}
function observe(page,role){
  page.on('pageerror',error=>report.errors.push(role+' JS '+String(error)));
  page.on('console',message=>{
    if(message.type()==='error') report.errors.push(role+' console '+message.text());
  });
  page.on('response',response=>{
    const url=new URL(response.url());
    if(url.pathname.startsWith('/api/v1/') && response.status()>=400){
      report.errors.push(role+' HTTP '+response.status()+' '+url.pathname);
    }
  });
}
async function authRole(role,email,password){
  const context=await browser.newContext({viewport:{width:1440,height:900},deviceScaleFactor:1,reducedMotion:'reduce'});
  const page=await context.newPage();
  observe(page,role);
  await page.goto(base+'/login',{waitUntil:'domcontentloaded',timeout:20000});
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.getByRole('button',{name:'Đăng nhập'}).click();
  await page.waitForURL('**/workspace',{timeout:20000});
  return {page,context};
}
try{
  browser=await chromium.launch({headless:true,args:['--no-sandbox']});
  const guestContext=await browser.newContext({viewport:{width:1440,height:900},deviceScaleFactor:1,reducedMotion:'reduce'});
  const guest=await guestContext.newPage();
  observe(guest,'guest');
  await guest.goto(base+'/login',{waitUntil:'domcontentloaded',timeout:20000});
  for(const [name,path,klass] of routes.guest)await recordPage(guest,'guest',name,path,klass);
  await guestContext.close();
  for(const role of ['donor','creator','admin']){
    const prefix='RISE_QA_'+role.toUpperCase();
    const email=process.env[prefix+'_EMAIL'],password=process.env[prefix+'_PASSWORD'];
    if(!email||!password){
      for(const [name] of routes[role])report.blocked.push({role,name,reason:'Missing '+prefix+'_EMAIL or PASSWORD'});
      continue;
    }
    try{
      const {page,context}=await authRole(role,email,password);
      for(const [name,path,klass] of routes[role])await recordPage(page,role,name,path,klass);
      await context.close();
    }catch(e){report.errors.push(role+' auth '+String(e))}
  }
  report.pass=report.errors.length===0&&report.blocked.length===0&&
    report.checks.length===routes.guest.length+routes.donor.length+routes.creator.length+routes.admin.length&&
    report.checks.every(x=>x.pass);
}finally{
  await writeFile(join(root,'qa-report.json'),JSON.stringify(report,null,2),'utf8');
  if(browser)await browser.close();
}
console.log(JSON.stringify({pass:report.pass,checked:report.checks.length,blocked:report.blocked.length,
  errors:report.errors.length,evidence:root},null,2));
process.exit(report.pass?0:report.errors.length?1:2);
