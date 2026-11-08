/** Real deployment smoke: built Nginx frontend + Spring Boot/MySQL demo seed.
 * Desktop Chromium 1440 only, no Playwright network interception, no fund mutations.
 */
import { chromium } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
/* global document, window */

const base = (process.env.RISE_FE06_BASE_URL || 'http://127.0.0.1:5174').replace(/\/$/, '');
const out = resolve(process.env.RISE_STAGING_REPORT_DIR || 'visual-qa/b2-v132/staging');
await mkdir(out, { recursive: true });
const checks = [];
const errors = [];
const apiCalls = [];
let browser;

function record(name, ok, details = '') {
  const pass = Boolean(ok);
  checks.push({ name, pass, details });
  console.log((pass ? 'PASS' : 'FAIL') + ' ' + name + (details ? ': ' + details : ''));
  if (!pass) throw new Error('Staging check failed: ' + name);
}
async function json(path) {
  const response = await fetch(base + path, { signal: AbortSignal.timeout(15000), headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error('HTTP ' + response.status + ' for ' + path);
  return response.json();
}
async function metrics(page) {
  return page.evaluate(() => {
    const sel = (s) => document.querySelector(s)?.getBoundingClientRect();
    return {
      headlineHeight: sel('#top .hero-copy h1')?.height || 0,
      headlineTop: (sel('#top .hero-copy h1')?.top || 0) + window.scrollY,
      ctaBottom: (sel('#top .hero-buttons')?.bottom || 0) + window.scrollY,
      journeyTop: (sel('#journey')?.top || 0) + window.scrollY,
      docWidth: document.documentElement.scrollWidth,
      categoryCount: document.querySelectorAll('#categories button.cat').length,
      campaignCount: document.querySelectorAll('#campaigns .campaign-card').length,
      milestoneCount: document.querySelectorAll('#milestones .card').length,
      transparencyCount: document.querySelectorAll('#transparency .record').length,
      mainCount: document.querySelectorAll('main').length,
      h1Count: document.querySelectorAll('h1').length,
    };
  });
}
try {
  const api = await json('/api/v1/campaigns?status=ACTIVE&page=0&size=3');
  const rows = api?.data?.data;
  record('same-origin real Backend campaign list', Array.isArray(rows) && rows.length === 3 && rows.every(c => c.campaignStatus === 'ACTIVE'), 'active=' + rows?.length);
  record('test database is clearly labelled DEMO', rows.every(c => String(c.title).includes('(Demo)')));
  browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, reducedMotion: 'reduce' });
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', msg => { if (msg.type() === 'error') errors.push('console: ' + msg.text().slice(0, 250)); });
  page.on('response', response => {
    if (response.url().includes('/api/v1/')) apiCalls.push({ method: response.request().method(), path: new URL(response.url()).pathname, status: response.status(), query: new URL(response.url()).search });
  });

  await page.goto(base + '/', { waitUntil: 'networkidle', timeout: 45000 });
  await page.locator('#campaigns .campaign-card').first().waitFor({ timeout: 25000 });
  await page.evaluate(() => document.fonts.ready);
  const actual = await metrics(page);
  record('1440 homepage all approved sections', (await Promise.all(['#top','#journey','#categories','#campaigns','#story','#milestones','#transparency','#footer'].map(sel => page.locator(sel).count()))).every(count => count === 1) &&
      actual.categoryCount === 8 && actual.campaignCount === 3 && actual.milestoneCount === 3 && actual.transparencyCount === 3,
      JSON.stringify(actual));
  record('desktop no horizontal overflow and valid landmarks', actual.docWidth <= 1440 && actual.mainCount === 1 && actual.h1Count === 1);
  record('homepage Browser called real ACTIVE API via Nginx', apiCalls.some(x => x.path === '/api/v1/campaigns' && x.query.includes('status=ACTIVE') && x.status === 200));
  record('staging financial amounts explicitly simulated', (await page.locator('#campaigns').innerText()).includes('MÔ PHỎNG'));

  // Force lazy image assets to load before the full-page acceptance screenshot.
  const imageStatuses = await page.evaluate(async () => {
    const images = [...document.images];
    for (const img of images) img.loading = 'eager';
    await Promise.all(images.map(img => img.decode().catch(() => null)));
    return images.map(img => ({ src: img.getAttribute('src')?.slice(0, 100), ok: img.complete && img.naturalWidth > 0 }));
  });
  record('production image assets complete', imageStatuses.every(x => x.ok), JSON.stringify(imageStatuses.filter(x => !x.ok)));

  const ref = resolve('../docs/design/art-direction/approved/b2-development-master-v1.3.2/B2_DEVELOPMENT_MASTER_V1_3_2_STANDALONE.html');
  const refPage = await ctx.newPage();
  await refPage.goto(pathToFileURL(ref).href, { waitUntil: 'load', timeout: 45000 });
  const approved = await metrics(refPage);
  record('Hero 3-line title matches signed-off HTML', Math.abs(actual.headlineHeight - approved.headlineHeight) <= 3 && actual.headlineHeight < 215,
      JSON.stringify({ staging: actual.headlineHeight, approved: approved.headlineHeight }));
  record('Hero CTA position matches approved HTML', Math.abs(actual.ctaBottom - approved.ctaBottom) <= 5 && actual.ctaBottom < actual.journeyTop - 65,
      JSON.stringify({ staging: actual.ctaBottom, approved: approved.ctaBottom }));
  await refPage.close();
  await page.screenshot({ path: resolve(out, 'staging-homepage-1440.png'), fullPage: true });

  await page.locator('#categories button[data-vibe="food"]').click();
  await page.waitForFunction(() => document.querySelectorAll('#campaigns .campaign-card').length === 1, undefined, { timeout: 15000 });
  const food = await json('/api/v1/campaigns?status=ACTIVE&category=FOOD_SECURITY&page=0&size=3');
  record('food filter matches real Backend response', food?.data?.data?.length === 1 &&
    (await page.locator('#campaigns .campaign-card').count()) === 1 &&
    (await page.locator('#categories button[data-vibe="food"]').getAttribute('aria-pressed')) === 'true' &&
    apiCalls.some(x => x.path === '/api/v1/campaigns' && x.query.includes('category=FOOD_SECURITY') && x.status === 200));

  await page.locator('#clear-filter').click();
  await page.waitForFunction(() => document.querySelectorAll('#campaigns .campaign-card').length === 3, undefined, { timeout: 15000 });
  record('reset filter restores real 3 cards', await page.locator('#campaigns .campaign-card').count() === 3);

  // SPA navigation must preserve JS context to avoid losing memory-only auth.
  await page.evaluate(() => { window.__stagingSpaMarker = 'same-document'; });
  await page.locator('#campaigns .campaign-main .card-link').click();
  await page.waitForURL('**/campaigns/*');
  const first = rows[0];
  const title = await page.locator('main h1').innerText();
  record('real campaign detail SPA navigation', await page.evaluate(() => window.__stagingSpaMarker === 'same-document') &&
    title.includes(first.title) && page.url().endsWith('/campaigns/' + encodeURIComponent(first.slug)), first.slug);
  record('campaign detail shows simulated-funds disclaimer', (await page.locator('main').innerText()).includes('mô phỏng'));

  await page.reload({ waitUntil: 'networkidle' });
  record('production deep link refresh serves React SPA', (await page.locator('main h1').innerText()).includes(first.title));

  // Explicit anonymous Admin API denial is verified separately by deploy:smoke.
  await page.goto(base + '/admin/monitoring', { waitUntil: 'networkidle' });
  record('anonymous Admin browser guard', new URL(page.url()).pathname === '/login');

  // Read-only demo identity test: no donation or escrow mutations.
  await page.locator('#email').fill('donor1@riseai.example');
  await page.locator('#password').fill(process.env.RISE_STAGING_DEMO_PASSWORD || 'Demo@12345');
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL('**/workspace');
  record('real Backend demo DONOR login through Nginx', new URL(page.url()).pathname === '/workspace' &&
    apiCalls.some(x => x.path === '/api/v1/auth/login' && x.status === 200));
  await page.evaluate(() => {
    window.history.pushState(null, '', '/admin/monitoring');
    window.dispatchEvent(new window.PopStateEvent('popstate'));
  });
  await page.waitForURL('**/admin/monitoring');
  await page.getByRole('heading', { name: 'Tài khoản không có quyền truy cập' }).waitFor();
  record('logged-in DONOR cannot access Admin browser route',
    new URL(page.url()).pathname === '/admin/monitoring' &&
    (await page.getByRole('heading', { name: 'Tài khoản không có quyền truy cập' }).count()) === 1);
  await page.reload({ waitUntil: 'networkidle' });
  record('memory-only session cleared on full reload', new URL(page.url()).pathname === '/login');
  record('no uncaught browser errors', errors.length === 0, JSON.stringify(errors.slice(0, 8)));
} catch (error) {
  errors.push('FAIL: ' + String(error));
  console.error(String(error));
  process.exitCode = 1;
} finally {
  const report = {
    source: 'GitHub isolated CI; actual Nginx production container + Spring Boot/MySQL demo data + Chromium',
    no_api_mocks: true, test_viewport: '1440x900@1', production_certified: false,
    source_revision: process.env.GITHUB_SHA || null,
    checks, errors, apiCalls, pass: process.exitCode !== 1 && checks.every(x => x.pass),
  };
  await writeFile(resolve(out, 'staging-results.json'), JSON.stringify(report, null, 2));
  if (browser) await browser.close();
  console.log('STAGING B2 ' + (report.pass ? 'PASS' : 'FAIL') + ' ' + checks.filter(c => c.pass).length + '/' + checks.length);
  if (!report.pass) process.exitCode = 1;
}
