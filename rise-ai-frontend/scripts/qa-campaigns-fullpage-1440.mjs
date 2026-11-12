/* Browser globals in page.evaluate; not executed by Node. */
/* global document:readonly, window:readonly, innerWidth:readonly, getComputedStyle:readonly */
import { chromium } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const root=resolve('../docs/design/art-direction/reviews/campaign-discovery-1440-20261112');
const evidence=resolve(root,'evidence-fullpage');
await mkdir(evidence,{recursive:true});
const result={scope:'Campaign Discovery full-page Paper Dossier HTML review',viewport:'1440x900 DPR1',sourceType:'Visual fixture; NO React/API QA',ownerApproval:'PENDING',checks:[],errors:[],pass:false};
function check(label,pass,detail){result.checks.push({label,pass:!!pass,detail});if(!pass)throw Error(label+': '+JSON.stringify(detail));}
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
const page=await browser.newPage({viewport:{width:1440,height:900},deviceScaleFactor:1,reducedMotion:'reduce'});
page.on('pageerror',e=>result.errors.push('JavaScript '+String(e)));
page.on('console',m=>{if(m.type()==='error')result.errors.push('Console '+m.text());});
try{
  await page.goto(pathToFileURL(resolve(root,'campaign-discovery-fullpage-paper-dossier-1440.html')).href,{waitUntil:'load',timeout:60000});
  await page.evaluate(()=>document.fonts.ready);
  const facts=()=>page.evaluate(()=>{
    const state=window.__risePreview?.getState();
    const section=document.getElementById('paper-section');
    return {state,documentWidth:document.documentElement.scrollWidth,viewportWidth:innerWidth,sectionWidth:section?.getBoundingClientRect().width,rows:document.querySelectorAll('#reading-results .dossier').length,arts:document.querySelectorAll('#reading-results .category-artwork svg').length,ctas:document.querySelectorAll('#reading-results .read-profile').length};
  });
  const initial=await facts();result.metrics=initial;
  check('1440px desktop, no horizontal overflow',initial.viewportWidth===1440&&initial.documentWidth<=1440&&initial.sectionWidth===1440,initial);
  check('9 records/page split 1 lead + 8 dossiers',initial.state?.size===9&&initial.state?.leadVisible&&initial.rows===8,initial);
  check('8 dossier illustrations and actions',initial.arts===8&&initial.ctas===8,initial);
  const paperBackground=await page.locator('#reading-results .dossier-paper').first().evaluate(el=>getComputedStyle(el).backgroundImage);
  check('Dynamic paper surface has valid cream gradient',paperBackground.includes('linear-gradient')&&!paperBackground.includes('none'),paperBackground.slice(0,180));
  check('Original Header, Filter, Featured, Pagination and Footer preserved',await page.locator('.site-header').count()===1&&await page.locator('.filter-section').count()===1&&await page.locator('#lead-section').count()===1&&await page.locator('#pagination-landscape').count()===1&&await page.locator('.site-footer').count()===1);
  check('Truthful design-only notice',await page.locator('.preview-ribbon').isVisible());
  const seam=await page.evaluate(()=>{
    const rect=selector=>{const x=document.querySelector(selector).getBoundingClientRect();return {top:x.top+window.scrollY,bottom:x.bottom+window.scrollY,width:x.width};};
    const top=document.querySelector('.campaign-page-dossier > .top-landscape');
    const bottom=document.querySelector('.campaign-page-dossier > .bottom-valley');
    const lead=rect('#lead-section'),paper=rect('#paper-section'),pager=rect('#pagination-landscape'),wave=rect('.footer-wave');
    return {lead,paper,pager,wave,gaps:{leadToPaper:Math.round(paper.top-lead.bottom),paperToPager:Math.round(pager.top-paper.bottom),pagerToWave:Math.round(wave.top-pager.bottom)},
      topMask:getComputedStyle(top).maskImage,bottomMask:getComputedStyle(bottom).maskImage,
      pagerBackground:getComputedStyle(document.querySelector('#pagination-landscape')).backgroundImage,
      footerWaveMargin:getComputedStyle(document.querySelector('.footer-wave')).marginTop};
  });
  result.seams=seam;
  check('Top and bottom landscapes have graded alpha masks',
    seam.topMask.includes('linear-gradient')&&seam.bottomMask.includes('linear-gradient'),
    {topMask:seam.topMask,bottomMask:seam.bottomMask});
  const controlGap=await page.evaluate(()=>{
    const last=document.querySelector('#reading-results .dossier:last-child')?.getBoundingClientRect();
    const controls=document.querySelector('#pagination-slot .pagination-controls')?.getBoundingClientRect();
    return last&&controls?Math.round(controls.top-last.bottom):null;
  });
  result.lastDossierToPagerButtons=controlGap;
  check('Pagination is near the last dossier, but never covers its content',
    controlGap!==null&&controlGap>=40&&controlGap<=180,
    {gapPx:controlGap});
  check('Intended overlap joins the dossier background to pagination without moving Footer',
    seam.gaps.paperToPager>=-145&&seam.gaps.paperToPager<=-135&&Math.abs(seam.gaps.pagerToWave)<=2,
    seam.gaps);
  check('Pagination and footer use shared sage transition palette',
    seam.pagerBackground.includes('linear-gradient')&&seam.footerWaveMargin==='0px',
    {pagerBackground:seam.pagerBackground,footerWaveMargin:seam.footerWaveMargin});
  const landscape=await page.evaluate(()=>{
    const shared=document.querySelector('.continuous-landscape');
    const scene=shared?.querySelector('svg');
    const footer=document.querySelector('.footer-wave');
    const pager=document.getElementById('pagination-landscape');
    return {
      sharedMeadow:!!scene?.querySelector('#meadow-atmosphere')&&getComputedStyle(shared).display!=='none',
      forestTransition:!!footer?.querySelector('#footer-forest-grade'),
      trees:scene?.querySelectorAll('g[transform]').length||0,
      hiddenDecor:shared?.getAttribute('aria-hidden')==='true'&&footer?.getAttribute('aria-hidden')==='true',
      pagerArtHidden:getComputedStyle(pager.querySelector('.pagination-scene')).display==='none',
      footerHeight:Math.round(footer.getBoundingClientRect().height)
    };
  });
  result.landscape=landscape;
  check('Meadow with natural detail extends behind cards and pagination into forest footer',
    landscape.sharedMeadow&&landscape.forestTransition&&landscape.trees>=10,landscape);
  check('Decorative artwork is hidden from assistive technology',landscape.hiddenDecor,landscape);
  check('Pagination uses the shared landscape; footer transition is 135px forest fade',
    landscape.pagerArtHidden&&landscape.footerHeight===135,landscape);
  await page.screenshot({path:resolve(evidence,'campaign-fullpage-1440.png'),fullPage:true,animations:'disabled'});
  await page.locator('#reading-results .read-profile').first().click();
  check('Dossier action shows mock-only disclosure',await page.locator('#demo-feedback').isVisible());
  await page.keyboard.press('Escape');
  check('Escape dismisses mock notice',!(await page.locator('#demo-feedback').isVisible()));
  await page.locator('.cat').first().click();
  const filtered=await facts();
  check('Category filter updates dossier rows',filtered.state?.page===1&&filtered.state?.total===6&&filtered.rows===5,filtered);
  await page.locator('#reset').click();
  check('Reset restores 8 dossier rows',(await facts()).rows===8);
  await page.evaluate(()=>window.__risePreview.goPage(6));
  const last=await facts();
  check('Last page has lead + no phantom rows',last.state?.page===6&&last.state?.leadVisible&&last.rows===0,last);
  await page.evaluate(()=>window.__risePreview.setSearch('___no_campaign_matches_2026___'));
  const empty=await facts();
  check('Empty state hides dossier and pager',empty.state?.total===0&&!(await page.locator('#paper-section').isVisible())&&!(await page.locator('#pagination-landscape').isVisible()),empty);
  check('No browser errors',result.errors.length===0,result.errors);
  result.pass=true;
}catch(e){result.failure=String(e);process.exitCode=1;console.error(e);}
await writeFile(resolve(evidence,'qa-report.json'),JSON.stringify(result,null,2),'utf8');
console.log(JSON.stringify({pass:result.pass,checks:result.checks.length,errors:result.errors},null,2));
await browser.close();
