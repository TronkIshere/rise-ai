/**
 * Desktop-only visual QA for approved B2 Homepage v1.3.2.
 * Execute separately with Playwright installed; NOT part of npm run verify.
 * Fixture is local browser interception, never production data or a Backend change.
 */
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { chromium } from 'playwright';
/* global document, window, scrollX, scrollY */

const out = resolve('visual-qa/b2-v132');
await mkdir(out, { recursive: true });
const campaigns = [
  { id: 'b2-fixture-01', slug: 'hope-kitchen-demo', category: 'FOOD_SECURITY', title: 'Hope Kitchen Weekly Meals', shortDescription: 'Bữa ăn và túi lương thực định kỳ hỗ trợ người cao tuổi sống một mình.', organization: { name: 'Hope Kitchen Network (DEMO)' }, beneficiaryCount: 150, targetAmount: 60_000_000, currency: 'VND', funding: { netRaised: 22_000_000 } },
  { id: 'b2-fixture-02', slug: 'riverside-library-demo', category: 'COMMUNITY', title: 'Riverside Community Library', shortDescription: 'Không gian thư viện miễn phí cho cộng đồng.', organization: { name: 'Green Village Community Group (DEMO)' }, targetAmount: 80_000_000, currency: 'VND', funding: { netRaised: 45_000_000 } },
  { id: 'b2-fixture-03', slug: 'learning-kits-demo', category: 'EDUCATION', title: 'Laptops and Learning Kits for Highland Pupils', shortDescription: 'Thiết bị số và học liệu cho học sinh vùng cao.', organization: { name: 'Sunrise Learning Fund (DEMO)' }, targetAmount: 120_000_000, currency: 'VND', funding: { netRaised: 75_000_000 } },
];
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
const result = { viewport: '1440x900@1', fixture: 'QA intercept only, simulated campaign data', checks: {}, issues: [], geometry: {} };
const fails = [];
function check(name, pass, detail = '') {result.checks[name] = { pass: Boolean(pass), detail }; if (!pass) fails.push(name + (detail ? ': ' + detail : ''));}
function metrics(page) {
  return page.evaluate(() => {
    const selectors = { hero: '#top', journey: '#journey', categories: '#categories', campaigns: '#campaigns', story: '#story', milestones: '#milestones', transparency: '#transparency', footer: '#footer' };
    const boxes = Object.fromEntries(Object.entries(selectors).map(([name, sel]) => {
      const el = document.querySelector(sel); if (!el) return [name, null];
      const rect = el.getBoundingClientRect(); return [name, { x: Math.round(rect.x + scrollX), y: Math.round(rect.y + scrollY), width: Math.round(rect.width), height: Math.round(rect.height) }];
    }));
    return {
      boxes,
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth: window.innerWidth,
      documentHeight: document.documentElement.scrollHeight,
      mainCount: document.querySelectorAll('main').length,
      h1Count: document.querySelectorAll('h1').length,
      categoryCount: document.querySelectorAll('#categories .cat').length,
      milestoneCards: document.querySelectorAll('#milestones .card').length,
      recordCards: document.querySelectorAll('#transparency .record').length,
      campaignCards: document.querySelectorAll('#campaigns .campaign-card').length,
      images: [...document.images].map(img => ({ src: img.getAttribute('src')?.slice(0, 130), complete: img.complete, naturalWidth: img.naturalWidth })),
    };
  });
}
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, reducedMotion: 'reduce' });
const page = await ctx.newPage();
const browserErrors = [], failedHttp = [], calls = [];
page.on('pageerror', e => browserErrors.push(String(e)));
page.on('console', e => { if (e.type() === 'error' && !/favicon\.ico/.test(e.location()?.url || '')) browserErrors.push('console: ' + e.text()); });
page.on('response', response => { if (response.status() >= 400 && !/favicon\.ico/.test(response.url())) failedHttp.push(response.status() + ' ' + response.url()); });
await page.route(/\/api\/v1\/campaigns(?:\?|$)/, async route => {
  const u = new URL(route.request().url()); calls.push(u.searchParams.toString());
  const category = u.searchParams.get('category');
  const data = category ? campaigns.filter(c => c.category === category) : campaigns;
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'OK', data: { data, currentPage: 0, pageSize: 3, totalElements: data.length, totalPages: data.length ? 1 : 0 } }) });
});
await page.goto('http://127.0.0.1:5173/', { waitUntil: 'networkidle', timeout: 45000 });
await page.locator('#campaigns .campaign-card').first().waitFor({ timeout: 20000 });
await page.evaluate(() => document.fonts.ready);
const m = await metrics(page); result.geometry.react = m;
check('section order and presence', ['categories','campaigns','story','milestones','transparency','footer'].every(k => m.boxes[k]) && ['categories','campaigns','story','milestones','transparency','footer'].every((k,i,a) => i === 0 || m.boxes[k].y > m.boxes[a[i-1]].y), JSON.stringify(Object.fromEntries(Object.entries(m.boxes).map(([k,v])=>[k,v?.y]))));
check('one main landmark and h1', m.mainCount===1 && m.h1Count===1, `${m.mainCount} main, ${m.h1Count} h1`);
check('8 category cards', m.categoryCount===8, String(m.categoryCount));
check('3 milestones and 3 transparency records', m.milestoneCards===3 && m.recordCards===3, `K1 ${m.milestoneCards}, T1.2 ${m.recordCards}`);
check('3 Backend-backed campaign cards (fixture)', m.campaignCards===3, String(m.campaignCards));
check('no horizontal scroll 1440', m.documentWidth <= 1440, `${m.documentWidth}px`);
check('images loaded', m.images.every(x=>x.complete && x.naturalWidth>0), m.images.filter(x=>!x.complete || !x.naturalWidth).map(x=>x.src).join(','));
check('no console errors or asset 404', browserErrors.length===0 && failedHttp.length===0, [...browserErrors,...failedHttp].slice(0,10).join('; '));
check('campaigns API filters status=ACTIVE', calls.some(x=>new URLSearchParams(x).get('status')==='ACTIVE'), JSON.stringify(calls));
await page.screenshot({ path: resolve(out,'react-homepage-1440.png'), fullPage: true });
// Selected category must issue a real server-filtered query (intercepted by QA).
await page.locator('#categories button[data-vibe="education"]').click();
await page.waitForFunction(() => document.querySelectorAll('#campaigns .campaign-card').length===1, undefined, {timeout: 15000});
const pressed = await page.locator('#categories button[data-vibe="education"]').getAttribute('aria-pressed');
check('category filter updates Backend query and aria-pressed', pressed==='true' && calls.some(x=>new URLSearchParams(x).get('category')==='EDUCATION'), `pressed=${pressed}; ${calls.at(-1)}`);
await page.locator('#clear-filter').click();
await page.waitForFunction(() => document.querySelectorAll('#campaigns .campaign-card').length===3, undefined, {timeout:15000});
check('clear filter restores 3 campaign cards', await page.locator('#campaigns .campaign-card').count()===3);
const disclosure = page.locator('#transparency-more-button');
await disclosure.focus();await page.keyboard.press('Space');
check('transparency disclosure opens via keyboard', await disclosure.getAttribute('aria-expanded')==='true' && !(await page.locator('#transparency-detail').isHidden()));
await page.keyboard.press('Space');
check('transparency disclosure closes via keyboard', await disclosure.getAttribute('aria-expanded')==='false' && await page.locator('#transparency-detail').isHidden());
// The footer crosses into the shared React routes. Navigation must preserve
// the same JS document, because FE-00 holds session tokens only in memory.
await page.evaluate(() => { window.__b2SpaMarker = 'same-document'; });
await page.locator('#footer a[href="/campaigns"]').click();
await page.waitForURL('**/campaigns');
const sameDocument = await page.evaluate(() => window.__b2SpaMarker === 'same-document');
check('B2 footer navigation uses SPA without full reload', sameDocument, `path=${new URL(page.url()).pathname}; marker=${sameDocument}`);
await page.goBack();
await page.waitForURL(/\\/$/);
await page.locator('#top').waitFor();
// The reference is a standalone design file, not React. Capture for direct two-up review.
const ref = resolve('../docs/design/art-direction/approved/b2-development-master-v1.3.2/B2_DEVELOPMENT_MASTER_V1_3_2_STANDALONE.html');
const refPage = await ctx.newPage();
await refPage.goto(pathToFileURL(ref).href, { waitUntil:'load', timeout:45000 });
await refPage.screenshot({ path: resolve(out,'approved-master-1440.png'), fullPage:true });
result.geometry.approvedStatic = await metrics(refPage);
// Visual-fidelity regression: section geometry alone misses a wrapped Hero title
// and CTAs drifting into the decorative Journey wave.
const heroVisual = async p => p.evaluate(() => {
  const title = document.querySelector('#top .hero-copy h1')?.getBoundingClientRect();
  const buttons = document.querySelector('#top .hero-buttons')?.getBoundingClientRect();
  const journey = document.querySelector('#journey')?.getBoundingClientRect();
  return { titleHeight: title?.height ?? 0, titleY: (title?.top ?? 0) + window.scrollY, buttonsBottom: (buttons?.bottom ?? 0) + window.scrollY, journeyY: (journey?.top ?? 0) + window.scrollY };
});
const reactHeroVisual = await heroVisual(page);
const approvedHeroVisual = await heroVisual(refPage);
result.geometry.heroVisual = { react: reactHeroVisual, approved: approvedHeroVisual };
check('Hero heading wraps as approved 3-line master', Math.abs(reactHeroVisual.titleHeight - approvedHeroVisual.titleHeight) <= 3 && reactHeroVisual.titleHeight < 215, JSON.stringify(result.geometry.heroVisual));
check('Hero CTAs stay above Journey wave and align with master', reactHeroVisual.buttonsBottom < reactHeroVisual.journeyY - 65 && Math.abs(reactHeroVisual.buttonsBottom - approvedHeroVisual.buttonsBottom) <= 5, JSON.stringify(result.geometry.heroVisual));
await refPage.close();
await page.close();
await ctx.close();
// State tests: API errors and empty list must not produce fabricated campaign totals.
for (const [state, code] of [['error',503],['empty',200]]) {
  const c=await browser.newContext({viewport:{width:1440,height:900}});const p=await c.newPage();
  await p.route(/\/api\/v1\/campaigns(?:\?|$)/, async route=>route.fulfill({status:code,contentType:'application/json',body:code===200?JSON.stringify({code:200,data:{data:[],currentPage:0,pageSize:3,totalElements:0,totalPages:0}}):JSON.stringify({status:503,message:'Backend unavailable in QA'})}));
  await p.goto('http://127.0.0.1:5173/',{waitUntil:'networkidle',timeout:45000});
  const expected=state==='error'?'Chưa tải được chiến dịch':'Chưa có chiến dịch đang kêu gọi';
  const msg=p.locator('.b2-api-message');
  await p.getByText(expected, { exact: false }).first().waitFor({ timeout: 25000 });
  check(`${state} API state`,(await msg.innerText()).includes(expected) && (await p.locator('#campaigns .campaign-card').count())===0);
  await c.close();
}
await browser.close();
await writeFile(resolve(out,'qa-results.json'), JSON.stringify({ ...result, pass: fails.length===0 },null,2));
console.log('B2 React Desktop 1440px QA: ' + (fails.length ? 'FAIL' : 'PASS'));
for (const [k,v] of Object.entries(result.checks)) console.log((v.pass?'PASS ':'FAIL ')+k+' — '+v.detail);
console.log('Section geometry:', JSON.stringify(result.geometry.react.boxes));
if (fails.length) {console.error(fails.join('\n'));process.exitCode=1;}
