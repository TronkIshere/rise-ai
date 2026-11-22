/* global document, window, PopStateEvent */
// Three formerly BLOCKED acceptance cases. Real Backend HTTP + disposable MySQL + Chromium 1440x900 DPR1.
// Seeded Donor dispute and Creator draft come from the existing backend DemoDataSeeder.
// Admin finding is EXPLICITLY a synthetic QA fixture in disposable MySQL, not Monitoring evidence.
// No mock/intercept, no Fuji, no shared Demo mutations, no unapproved finding-review writes.
import { chromium } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const base = process.env.RISE_STAGING_BASE_URL;
if (process.env.CI !== 'true' || base !== 'http://127.0.0.1:5174') {
  throw new Error('Refusing to run outside isolated localhost GitHub CI staging.');
}
const password = process.env.RISE_QA_DEMO_PASSWORD;
if (!password) throw new Error('Missing disposable demo password');
const findingId = '5f0e9c0f-0c1e-4a50-b7da-55a1a89c9221';
const output = resolve('visual-qa/staging-react-real-api/qa-three-blockers');
await mkdir(output, { recursive: true });
const report = {
  timestamp: new Date().toISOString(), commit: process.env.GITHUB_SHA || 'local',
  environment: 'GitHub Actions isolated Spring Boot + disposable MySQL 8.4 + Nginx',
  browser: 'Chromium 1440x900 DPR1', interceptedRequests: false,
  sharedDemoTouched: false, signedBlockchainTransactions: false,
  findingDataOrigin: 'EXPLICIT_SYNTHETIC_QA_FIXTURE_DISPOSABLE_DATABASE_NOT_MONITORING',
  cases: [], errors: [], screenshots: [], pass: false,
};
function assert(condition, message) { if (!condition) throw new Error(message); }
async function apiLogin(email) {
  const response = await fetch(base + '/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
    signal: AbortSignal.timeout(15000),
  });
  assert(response.status === 200, 'Login API status ' + response.status + ' (' + email + ')');
  const payload = await response.json();
  assert(payload?.data?.accessToken, 'Login lacks real Backend token');
  return payload.data.accessToken;
}
async function apiGet(token, route, expected = 200) {
  const response = await fetch(base + route, {
    headers: { Authorization: 'Bearer ' + token },
    signal: AbortSignal.timeout(15000),
  });
  assert(response.status === expected, route + ' expected ' + expected + ' got ' + response.status);
  if (expected !== 200) return { status: response.status };
  const payload = await response.json();
  assert(payload && typeof payload.data === 'object', route + ' missing JSON envelope');
  return payload.data;
}
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
async function uiSession(email) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, reducedMotion: 'reduce',
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  page.on('response', r => {
    if (r.url().includes('/api/v1/') && r.status() >= 400) errors.push('HTTP ' + r.status() + ' ' + new URL(r.url()).pathname);
  });
  await page.goto(base + '/login', { waitUntil: 'domcontentloaded' });
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await page.waitForURL('**/workspace', { timeout: 30000 });
  return { context, page, errors };
}
async function spaGo(page, path, className) {
  await page.evaluate(next => {
    window.history.pushState({}, '', next);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
  await page.waitForURL(base + path, { timeout: 20000 });
  await page.locator(className).waitFor({ state: 'visible', timeout: 30000 });
}
async function capture(session, name) {
  await session.page.evaluate(async () => {
    await document.fonts.ready;
    window.scrollTo(0, 0);
  });
  const measure = await session.page.evaluate(() => ({
    width: window.innerWidth, height: window.innerHeight, dpr: window.devicePixelRatio,
    scrollWidth: document.documentElement.scrollWidth,
    rootFont: window.getComputedStyle(document.body).fontFamily,
  }));
  assert(measure.width === 1440 && measure.height === 900 && measure.dpr === 1, name + ' viewport mismatch');
  assert(measure.scrollWidth <= 1440, name + ' horizontal overflow ' + measure.scrollWidth);
  assert(session.errors.length === 0, name + ' console/API failures: ' + session.errors.join(' | '));
  const file = name + '-chromium-1440x900-dpr1.png';
  await session.page.screenshot({ path: resolve(output, file), fullPage: true, animations: 'disabled' });
  report.screenshots.push(file);
  return measure;
}
async function run(name, fn) {
  try {
    const data = await fn();
    report.cases.push({ name, status: 'PASS', ...data });
    console.log('PASS ' + name + ' ' + JSON.stringify(data));
  } catch (error) {
    report.cases.push({ name, status: 'FAIL', reason: String(error) });
    report.errors.push(name + ': ' + String(error));
    console.error('FAIL ' + name + ': ' + String(error));
  }
}
try {
  await run('G2-01-donor-populated-dispute', async () => {
    const [donor, otherDonor] = await Promise.all([
      apiLogin('donor3@riseai.example'), apiLogin('donor2@riseai.example'),
    ]);
    const listing = await apiGet(donor, '/api/v1/disputes/me?page=0&size=20');
    const item = listing.data?.find(x => x.id && x.disputeCode);
    assert(item, 'Existing seeder donor3 dispute is missing');
    const detail = await apiGet(donor, '/api/v1/disputes/' + item.id);
    const messages = await apiGet(donor, '/api/v1/disputes/' + item.id + '/messages?page=0&size=10');
    assert(detail.id === item.id && messages.data?.length >= 1, 'Dispute detail/messages not populated');
    const wrongOwner = await fetch(base + '/api/v1/disputes/' + item.id, {
      headers: { Authorization: 'Bearer ' + otherDonor }, signal: AbortSignal.timeout(15000),
    });
    assert(wrongOwner.status === 403 || wrongOwner.status === 404,
      'Cross-donor dispute access was not denied: ' + wrongOwner.status);
    const session = await uiSession('donor3@riseai.example');
    try {
      await spaGo(session.page, '/donor/disputes/' + item.id, '.deep-donor-dispute .b2-dossier');
      await session.page.locator('.b2-message').first().waitFor({ timeout: 25000 });
      const body = await session.page.locator('.b2-dossier').innerText();
      assert(body.includes(detail.disputeCode), 'Live dispute code absent from React');
      assert((await session.page.locator('.b2-case-timeline li').count()) >= 1, 'Missing timeline');
      assert((await session.page.locator('.b2-message').count()) >= 1, 'Missing actual conversation');
      const geometry = await capture(session, '01-donor-dispute');
      return { recordId: item.id, data: 'existing seeded simulated dispute', apiStatus: 200,
        messageCount: messages.data.length, crossDonorAccessStatus: wrongOwner.status, geometry };
    } finally { await session.context.close(); }
  });
  await run('G2-02-creator-edit-draft-save-and-reload', async () => {
    const token = await apiLogin('creator1@riseai.example');
    const listing = await apiGet(token, '/api/v1/campaigns/mine?page=0&size=50');
    const campaign = listing.data?.find(x => x.id && (x.campaignStatus === 'DRAFT' || x.campaignStatus === 'REJECTED'));
    assert(campaign, 'No real owned editable seeder campaign');
    const original = await apiGet(token, '/api/v1/campaigns/' + campaign.id);
    assert(original.creatorId === listing.data.find(x => x.id === campaign.id)?.creatorId, 'Creator ownership mismatch');
    const marker = ' [QA isolated UI edit]';
    const updated = original.shortDescription.includes(marker) ? original.shortDescription : original.shortDescription + marker;
    assert(updated.length <= 500, 'Existing description cannot accept QA suffix');
    const session = await uiSession('creator1@riseai.example');
    try {
      await spaGo(session.page, '/creator/campaigns/' + campaign.id + '/edit', '.deep-creator-editor .b2-editor-form');
      const input = session.page.locator('#creator-shortDescription');
      await input.waitFor({ state: 'visible', timeout: 15000 });
      assert((await input.inputValue()) === original.shortDescription, 'Editor did not load existing Backend data');
      await input.fill(updated);
      await session.page.locator('.b2-studio-chapters button').last().click();
      await session.page.locator('#creator-review').waitFor({ state: 'visible' });
      const [savedResponse] = await Promise.all([
        session.page.waitForResponse(r =>
          r.request().method() === 'PUT' && r.url().includes('/api/v1/campaigns/' + campaign.id), { timeout: 12000 }),
        session.page.locator('.b2-editor-form button[type="submit"]').click(),
      ]);
      assert(savedResponse.status() === 200, 'UI save API returned ' + savedResponse.status());
      await session.page.waitForURL(base + '/creator/campaigns/' + campaign.id, { timeout: 20000 });
      const persisted = await apiGet(token, '/api/v1/campaigns/' + campaign.id);
      assert(persisted.shortDescription === updated, 'Data did not persist after UI Save');
      assert(persisted.campaignStatus === original.campaignStatus, 'Save mutated campaign lifecycle');
      // Check the updated record again via real Backend, without reusing React cached values.
      const secondGet = await apiGet(token, '/api/v1/campaigns/' + campaign.id);
      assert(secondGet.shortDescription === updated, 'Reload API did not reflect edit');
      const geometry = await capture(session, '02-creator-draft-saved');
      return { recordId: campaign.id, data: 'existing seeded DRAFT campaign', apiStatus: 200,
        saveHttpStatus: savedResponse.status(), persisted: true, statePreserved: true, geometry };
    } finally { await session.context.close(); }
  });
  await run('G2-03-admin-populated-monitoring-finding', async () => {
    const token = await apiLogin('admin@riseai.example');
    const detail = await apiGet(token, '/api/v1/admin/monitoring/findings/' + findingId);
    const history = await apiGet(token, '/api/v1/admin/monitoring/findings/' + findingId + '/history');
    assert(detail.finding?.id === findingId && detail.occurrences?.length === 1,
      'Fixture not served by real Backend with one occurrence');
    assert(detail.finding.advisoryOnly === true && detail.finding.reviewStatus === 'OPEN',
      'Finding advisory/status contract wrong');
    assert(Array.isArray(history), 'Finding history unavailable');
    const session = await uiSession('admin@riseai.example');
    try {
      await spaGo(session.page, '/admin/monitoring/findings/' + findingId, '.deep-admin-finding .investigation-desk');
      await session.page.locator('.investigation-fact-list li').first().waitFor({ timeout: 15000 });
      assert((await session.page.locator('.admin-finding-occurrence').count()) === 1,
        'Real occurrence missing from UI');
      const reviewButton = session.page.getByRole('button', { name: 'Lưu đánh giá' });
      assert(await reviewButton.isDisabled(), 'Review button enabled without human selection and note');
      await session.page.locator('#finding-target').selectOption('ACKNOWLEDGED');
      await session.page.locator('#finding-review-note').fill('QA only: verify manual gate without submitting');
      await session.page.getByRole('checkbox').check();
      assert(await reviewButton.isEnabled(), 'Explicit manual review UI gate failed');
      const geometry = await capture(session, '03-admin-finding-review-guard');
      const after = await apiGet(token, '/api/v1/admin/monitoring/findings/' + findingId);
      assert(after.finding.reviewStatus === 'OPEN' && !after.finding.reviewedBy,
        'Finding changed without explicit review submit');
      return { recordId: findingId, data: 'explicit synthetic QA finding in disposable MySQL',
        apiStatus: 200, occurrences: detail.occurrences.length, historyEntries: history.length,
        humanReviewGate: true, noReviewMutation: true, geometry };
    } finally { await session.context.close(); }
  });
} finally {
  await browser.close();
  report.pass = report.cases.length === 3 && report.cases.every(x => x.status === 'PASS');
  await writeFile(resolve(output, 'qa-results.json'), JSON.stringify(report, null, 2));
}
console.log('THREE BLOCKED QA CASES: ' + (report.pass ? 'PASS 3/3' : 'FAIL'));
if (!report.pass) process.exitCode = 1;
