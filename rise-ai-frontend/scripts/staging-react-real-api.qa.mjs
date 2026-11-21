/* global document, window, innerWidth */
/**
 * Isolated FE-06 staging browser acceptance against real Spring Boot + MySQL.
 * Target: locally deployed Nginx container (same-origin /api/v1 proxy).
 * No request interception, fixtures, credentials or mutations.
 */
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

const origin = process.env.RISE_STAGING_BASE_URL || 'http://127.0.0.1:5174';
const parsed = new URL(origin);
if (parsed.origin !== 'http://127.0.0.1:5174' || parsed.pathname !== '/' || parsed.search || parsed.hash) {
  throw new Error('This CI-only test intentionally targets http://127.0.0.1:5174 only');
}
const output = resolve('visual-qa/staging-react-real-api');
await mkdir(output, { recursive: true });
const report = {
  timestamp: new Date().toISOString(),
  baseline: process.env.GITHUB_SHA || 'local',
  url: origin,
  viewport: '1440x900@1',
  backend: 'Real Spring Boot HTTP + disposable MySQL 8.4 via Nginx, no intercepted requests',
  public_staging_deployed: false,
  simulation_only: true,
  checks: [],
  requestEvidence: [],
  screenshots: [],
  pass: false,
};
const categoryVibes = {
  EDUCATION: 'education',
  COMMUNITY: 'community',
  FOOD_SECURITY: 'food',
  WATER_SANITATION: 'water',
  SCHOLARSHIP: 'scholarship',
  INFRASTRUCTURE: 'infrastructure',
  HEALTH: 'health',
  OTHER: 'other',
};
function check(name, condition, detail = null) {
  const pass = Boolean(condition);
  report.checks.push({ name, pass, detail });
  console.log((pass ? 'PASS ' : 'FAIL ') + name + (detail ? ': ' + JSON.stringify(detail) : ''));
  if (!pass) throw new Error(name);
}
async function getData(path) {
  const response = await fetch(origin + path, { signal: AbortSignal.timeout(12000) });
  check('API HTTP 200 ' + path, response.status === 200, response.status);
  const envelope = await response.json();
  check('API envelope ' + path, Boolean(envelope && envelope.data && typeof envelope.data === 'object'));
  return envelope.data;
}
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
const ctx = await browser.newContext({
  viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, reducedMotion: 'reduce',
});
const page = await ctx.newPage();
const consoleErrors = [];
const failedAssets = [];
page.on('pageerror', e => consoleErrors.push(String(e)));
page.on('console', e => { if (e.type() === 'error' && !/favicon\\.ico/.test(e.location()?.url || '')) consoleErrors.push(e.text()); });
page.on('response', response => {
  if (response.url().includes('/api/v1/campaigns')) {
    report.requestEvidence.push({ path: new URL(response.url()).pathname + new URL(response.url()).search, status: response.status() });
  } else if (response.status() >= 400 && !/favicon\\.ico/.test(response.url())) {
    failedAssets.push(response.status() + ' ' + response.url());
  }
});
try {
  const campaignData = await getData('/api/v1/campaigns?status=ACTIVE&page=0&size=3');
  const rows = campaignData.data;
  check('Live ACTIVE seed records present', Array.isArray(rows) && rows.length >= 1 && rows.length <= 3, rows?.length);
  check('No inactive campaigns returned', rows.every(c => c.campaignStatus === 'ACTIVE'));
  await page.goto(origin, { waitUntil: 'networkidle', timeout: 60000 });
  await page.locator('#campaigns .campaign-card').first().waitFor({ timeout: 30000 });
  await page.evaluate(() => document.fonts.ready);
  const initialCount = await page.locator('#campaigns .campaign-card').count();
  check('Homepage renders real API rows', initialCount === rows.length && rows.length > 0, { api: rows.length, cards: initialCount });
  const campaignText = await page.locator('#campaigns').innerText();
  check('Live API campaign title appears in rendered React', rows.some(c => campaignText.includes(c.title)), rows.map(c => c.title));
  check('Exactly eight approved category buttons', (await page.locator('#categories button.cat').count()) === 8);
  check('Approved main structure exists', (await page.locator('main').count()) === 1 &&
    (await page.locator('h1').count()) === 1 &&
    (await page.locator('#milestones').count()) === 1 &&
    (await page.locator('#transparency').count()) === 1 &&
    (await page.locator('#footer').count()) === 1);
  const geom = await page.evaluate(() => ({
    viewportWidth: innerWidth,
    documentWidth: document.documentElement.scrollWidth,
    documentHeight: document.documentElement.scrollHeight,
  }));
  report.geometry = geom;
  check('Desktop 1440px has no horizontal overflow', geom.documentWidth <= 1440, geom);
  await page.screenshot({ path: resolve(output, '01-homepage-live-api-1440.png'), fullPage: true });
  report.screenshots.push('01-homepage-live-api-1440.png');

  // Choose a category actually returned by the seed, avoiding assumptions about fixtures.
  const category = rows[0].category;
  const vibe = categoryVibes[category];
  check('Selected live category is represented in approved UI', Boolean(vibe), category);
  const button = page.locator('#categories button[data-vibe="' + vibe + '"]');
  const [filteredResponse] = await Promise.all([
    page.waitForResponse(r => {
      if (!r.url().includes('/api/v1/campaigns?') || r.status() !== 200) return false;
      const url = new URL(r.url());
      return url.searchParams.get('category') === category && url.searchParams.get('status') === 'ACTIVE';
    }, { timeout: 30000 }),
    button.click(),
  ]);
  const filteredBody = await filteredResponse.json();
  const filteredRows = filteredBody?.data?.data;
  check('Real category-filter HTTP response contains matching ACTIVE records',
    Array.isArray(filteredRows) && filteredRows.length > 0 &&
    filteredRows.every(c => c.category === category && c.campaignStatus === 'ACTIVE'), { category, count: filteredRows?.length });
  await page.waitForFunction(expected => document.querySelectorAll('#campaigns .campaign-card').length === expected,
    filteredRows.length, { timeout: 30000 });
  check('Category filter updates React card count and aria-pressed',
    (await page.locator('#campaigns .campaign-card').count()) === filteredRows.length &&
    (await button.getAttribute('aria-pressed')) === 'true');
  await page.screenshot({ path: resolve(output, '02-filtered-live-api-1440.png'), fullPage: true });
  report.screenshots.push('02-filtered-live-api-1440.png');
  const [resetResponse] = await Promise.all([
    page.waitForResponse(r => {
      if (!r.url().includes('/api/v1/campaigns?') || r.status() !== 200) return false;
      const url = new URL(r.url());
      return url.searchParams.get('status') === 'ACTIVE' && !url.searchParams.has('category');
    }, { timeout: 30000 }),
    (async () => {
      await page.locator('#clear-filter').click();
      // React Query may reuse fresh cached unfiltered data; force a reload
      // to prove the restored state also works through real HTTP.
      await page.reload({ waitUntil: 'networkidle' });
    })(),
  ]);
  check('Clear filter and reload re-query unfiltered live Backend', resetResponse.status() === 200);
  await page.waitForFunction(expected => document.querySelectorAll('#campaigns .campaign-card').length === expected,
    rows.length, { timeout: 30000 });
  check('Unfiltered campaign list restored', (await page.locator('#campaigns .campaign-card').count()) === rows.length);
  const images = await page.locator('img').evaluateAll(list => list.map(i => ({ complete: i.complete, width: i.naturalWidth })));
  check('Rendered image assets load', images.every(i => i.complete && i.width > 0), images);
  check('No unhandled browser console error', consoleErrors.length === 0, consoleErrors.slice(0, 5));
  check('No HTTP failures for static assets', failedAssets.length === 0, failedAssets.slice(0, 5));
  check('Browser connected through same-origin API proxy', report.requestEvidence.length >= 3 &&
    report.requestEvidence.every(r => r.status === 200), report.requestEvidence);

  await page.evaluate(() => { window.__riseQaDocument = true; });
  await page.locator('#footer a[href="/campaigns"]').click();
  await page.waitForURL('**/campaigns');
  check('Footer navigation preserves React SPA document',
    await page.evaluate(() => window.__riseQaDocument === true));
  // The SPA can publish /campaigns while the Homepage still owns the document height.
  const discovery = page.locator('.campaign-discovery');
  await discovery.locator('footer#footer').waitFor();
  await discovery.locator('.lead-section, .cd-empty, .cd-error').first().waitFor();
  check('Campaign listing route loads', (await discovery.locator('.cd-error').count()) === 0);
  await page.evaluate(() => document.fonts.ready.then(() => undefined));
  await page.waitForFunction(() => [...document.querySelectorAll('.campaign-discovery img')]
    .every(image => image.complete && image.naturalWidth > 0));
  const getGeometry = () => page.evaluate(() => {
    const footer = document.querySelector('.campaign-discovery footer#footer');
    const documentHeight = Math.max(document.documentElement.scrollHeight, document.body.scrollHeight);
    const footerBottom = Math.round(footer.getBoundingClientRect().bottom + window.scrollY);
    return { documentHeight, footerBottom, trailingGap: documentHeight - footerBottom,
      documentWidth: document.documentElement.scrollWidth };
  });
  let geometry = await getGeometry();
  let stableSamples = 0;
  for (let sample = 0; sample < 20 && stableSamples < 2; sample++) {
    await page.waitForTimeout(180);
    const current = await getGeometry();
    stableSamples = current.documentHeight === geometry.documentHeight &&
      current.footerBottom === geometry.footerBottom ? stableSamples + 1 : 0;
    geometry = current;
  }
  report.discoveryGeometry = geometry;
  check('Campaign Discovery layout settles before capture', stableSamples >= 2, geometry);
  check('Campaign Discovery footer ends document without overflow',
    geometry.trailingGap >= -2 && geometry.trailingGap <= 4 && geometry.documentWidth <= 1440, geometry);
  const png = await page.screenshot({ path: resolve(output, '03-campaigns-route-1440.png'), fullPage: true });
  const screenshot = { width: png.readUInt32BE(16), height: png.readUInt32BE(20) };
  report.discoveryScreenshot = screenshot;
  check('Campaign Discovery PNG dimensions match settled document',
    screenshot.width === 1440 && Math.abs(screenshot.height - geometry.documentHeight) <= 3, screenshot);
  report.screenshots.push('03-campaigns-route-1440.png');
  // Route-level click regression: the approved landing-page preview must link to
  // functional routes, while editorial sections remain in-page navigation.
  await page.locator('.campaign-discovery .site-links a[href="/transparency"]').click();
  await page.waitForURL('**/transparency');
  check('Discovery Minh bạch opens the full Transparency page',
    await page.locator('.chain-public-page.u4-approved-v11').isVisible());
  await page.locator('.b2-sample-header .nav-search').click();
  await page.waitForURL('**/campaigns#search');
  check('Transparency search action opens Campaign Discovery search',
    await page.locator('.campaign-discovery #search input[type="search"]').isVisible());
  await page.locator('.campaign-discovery .site-links a[href="/#story"]').click();
  await page.waitForURL('**/#story');
  check('Discovery Câu chuyện opens the Homepage story section',
    await page.locator('.b2-homepage #story').isVisible());
  await page.locator('.b2-homepage .site-nav .nav-links a[href="/campaigns"]').click();
  await page.waitForURL('**/campaigns');
  check('Homepage Chiến dịch opens Campaign Discovery',
    await page.locator('.campaign-discovery #results').isVisible());
  await page.locator('.campaign-discovery .wordmark').first().click();
  await page.waitForURL(origin + '/');
  await page.locator('.b2-homepage .hero-buttons a[href="/campaigns"]').click();
  await page.waitForURL('**/campaigns');
  check('Homepage hero CTA opens the functional campaign route',
    await page.locator('.campaign-discovery #search').isVisible());
  await page.locator('.campaign-discovery .wordmark').first().click();
  await page.waitForURL(origin + '/');
  await page.locator('.b2-homepage .site-nav .nav-links a[href="/transparency"]').click();
  await page.waitForURL('**/transparency');
  check('Homepage Minh bạch opens the full U4 route',
    await page.locator('.chain-public-page.u4-approved-v11').isVisible());
  report.pass = true;
} catch (error) {
  report.failure = String(error);
  console.error('Staging React + real API browser QA failed:', String(error));
  try {
    await page.screenshot({ path: resolve(output, 'failure-last-browser-state.png'), fullPage: true });
    report.screenshots.push('failure-last-browser-state.png');
  } catch { /* screenshot itself can fail after a crashed page */ }
} finally {
  await writeFile(resolve(output, 'qa-results.json'), JSON.stringify(report, null, 2));
  await ctx.close();
  await browser.close();
}
console.log('STAGING REACT + REAL API BROWSER QA: ' + (report.pass ? 'PASS' : 'FAIL'));
if (!report.pass) process.exitCode = 1;
