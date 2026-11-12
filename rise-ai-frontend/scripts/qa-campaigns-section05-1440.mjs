/* Browser globals are evaluated by Playwright inside page.evaluate callbacks, not Node. */
/* global document:readonly, getComputedStyle:readonly, innerWidth:readonly */
import {chromium} from 'playwright';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

const root=resolve('../docs/design/art-direction/reviews/campaign-discovery-1440-20261112');
const htmlFile=resolve(root,'section-05-paper-dossier-v13-final-fade.html');
const evidenceDir=resolve(root,'evidence');
await mkdir(evidenceDir,{recursive:true});

const result={
  scope:'Campaign Discovery > Section 05 Paper Dossier v1.3.1',
  source:'Recovered Library v1.3 HTML + upper-edge fade only',
  viewport:'1440x900 DPR 1',
  sourceType:'Standalone HTML visual review, not React/Backend integration',
  checks:[], errors:[], metrics:{}, pass:false, ownerVisualAcceptance:'PENDING'
};
const check=(name,ok,details)=>{result.checks.push({name,pass:Boolean(ok),details}); if(!ok)throw new Error(name+' :: '+JSON.stringify(details));};
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
const page=await browser.newPage({viewport:{width:1440,height:900},deviceScaleFactor:1,reducedMotion:'reduce'});
page.on('pageerror',e=>result.errors.push('JS '+String(e)));
page.on('console',m=>{if(m.type()==='error')result.errors.push('Console '+m.text());});
try{
  await page.goto(pathToFileURL(htmlFile).href,{waitUntil:'load',timeout:60000});
  await page.evaluate(()=>document.fonts.ready);
  const sec=page.locator('main.review-section');
  const metrics=await page.evaluate(()=>{
    const r=document.querySelector('.review-section').getBoundingClientRect();
    const bg=document.querySelector('.bottom-valley');
    const b=bg.getBoundingClientRect();
    const cards=[...document.querySelectorAll('.dossiers > .dossier')].map(el=>{const q=el.getBoundingClientRect();return {x:q.x,y:q.y,width:q.width,height:q.height}});
    const imgs=[...document.querySelectorAll('.photo-frame img')].map(e=>({src:e.currentSrc.slice(0,24),complete:e.complete,naturalWidth:e.naturalWidth}));
    const ctas=[...document.querySelectorAll('.read-profile')].map(el=>{const q=el.getBoundingClientRect();return {x:q.x,y:q.y,width:q.width,height:q.height}});
    return {section:{x:r.x,y:r.y,width:r.width,height:r.height},valley:{x:b.x,y:b.y,width:b.width,height:b.height,mask:getComputedStyle(bg).maskImage},documentWidth:document.documentElement.scrollWidth,viewportWidth:innerWidth,cards,imgs,ctas,font:getComputedStyle(document.documentElement).fontFamily};
  });
  result.metrics=metrics;
  check('Correct Campaign Discovery Section05 HTML',await page.title()==='Rise AI · Section 05 · Editorial Paper Dossier v1.3 · Design Review');
  check('Version marker v1.3.1',await sec.getAttribute('data-version')==='section-05-paper-dossier-v1.3.1');
  check('Exactly 3 paper dossiers',metrics.cards.length===3,metrics.cards.length);
  check('Exactly 3 reader CTAs',metrics.ctas.length===3,metrics.ctas.length);
  check('All photos decode',metrics.imgs.length===3&&metrics.imgs.every(i=>i.complete&&i.naturalWidth>0),metrics.imgs);
  check('Desktop 1440px and no horizontal overflow',metrics.section.width===1440&&metrics.documentWidth<=1440,{sectionWidth:metrics.section.width,scrollWidth:metrics.documentWidth});
  check('Bottom landscape mask present',metrics.valley.mask.includes('linear-gradient'),metrics.valley.mask);
  const unmasked=await page.evaluate(()=>{
    const el=document.querySelector('.bottom-valley');
    el.style.setProperty('mask-image','none','important');
    el.style.setProperty('-webkit-mask-image','none','important');
    return getComputedStyle(el).maskImage;
  });
  check('Before mask toggle effective',unmasked==='none',unmasked);
  const beforeBoxes=await page.locator('.dossiers > .dossier').evaluateAll(els=>els.map(e=>{const r=e.getBoundingClientRect();return [r.x,r.y,r.width,r.height]}));
  await page.screenshot({path:resolve(evidenceDir,'before-v13-1440.png'),fullPage:true,animations:'disabled'});
  await page.locator('.bottom-valley').evaluate(el=>el.removeAttribute('style'));
  const afterBoxes=await page.locator('.dossiers > .dossier').evaluateAll(els=>els.map(e=>{const r=e.getBoundingClientRect();return [r.x,r.y,r.width,r.height]}));
  await page.screenshot({path:resolve(evidenceDir,'after-v131-fade-1440.png'),fullPage:true,animations:'disabled'});
  check('Dossier coordinates remain exactly unchanged',JSON.stringify(beforeBoxes)===JSON.stringify(afterBoxes),{beforeBoxes,afterBoxes});
  const before=await readFile(resolve(evidenceDir,'before-v13-1440.png'));
  const after=await readFile(resolve(evidenceDir,'after-v131-fade-1440.png'));
  check('Fade changes rendered pixels',!before.equals(after),{beforeBytes:before.length,afterBytes:after.length});
  // Capture the actual bottom-landscape element instead of using a document-space
  // clip on a viewport screenshot (which fails when the clip lies below viewport).
  await page.locator('.bottom-valley').screenshot({
    path:resolve(evidenceDir,'after-bottom-transition-1440.png'),
    animations:'disabled'
  });
  await page.locator('.read-profile').first().click();
  check('Reader CTA displays demo-only notice',await page.locator('#toast').isVisible());
  await page.keyboard.press('Escape');
  check('Escape closes demo notice',!(await page.locator('#toast').isVisible()));
  check('No browser errors',result.errors.length===0,result.errors);
  result.pass=true;
}catch(error){result.failure=String(error);process.exitCode=1;console.error(error);}
await writeFile(resolve(evidenceDir,'qa-report.json'),JSON.stringify(result,null,2),'utf8');
console.log(JSON.stringify({pass:result.pass,checks:result.checks.length,errors:result.errors,results:result.checks.map(c=>c.name)},null,2));
await browser.close();
