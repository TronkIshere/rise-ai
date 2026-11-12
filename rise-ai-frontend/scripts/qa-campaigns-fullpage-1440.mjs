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
