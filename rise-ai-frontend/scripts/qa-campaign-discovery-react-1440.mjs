/* Real-browser React acceptance for Campaign Discovery Desktop 1440.
 * Backend responses are explicitly intercepted test fixtures; production UI has NO fixture fallback.
 */
/* global document:readonly, window:readonly, getComputedStyle:readonly */
import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const evidence = resolve('qa-evidence/campaign-discovery-react');
await mkdir(evidence, { recursive: true });
const report = { scope: 'React /campaigns — Campaign Discovery Desktop 1440', viewport: '1440x900 DPR1',
  sourceType: 'Vite React in Chromium, intercepted Backend fixture only', checks: [], errors: [], requests: [], pass: false };
const check = (label, pass, details) => {
  report.checks.push({ label, pass: Boolean(pass), details });
  if (!pass) throw Error(label + ': ' + JSON.stringify(details));
};
const campaigns = Array.from({ length: 19 }, (_, i) => ({
  id: 'qa-' + (i + 1), slug: 'qa-campaign-' + (i + 1),
  title: 'Chương trình hỗ trợ cộng đồng ' + (i + 1),
  category: ['EDUCATION', 'COMMUNITY', 'FOOD_SECURITY', 'WATER_SANITATION', 'SCHOLARSHIP', 'INFRASTRUCTURE', 'HEALTH', 'OTHER'][i % 8],
  shortDescription: 'Cùng cộng đồng tạo thêm không gian học tập và điều kiện hỗ trợ địa phương ' + (i + 1),
  beneficiaryDescription: 'Học sinh và các gia đình tại địa phương',
  solutionDescription: 'Bổ sung sách và hoạt động hỗ trợ học tập',
  campaignStatus: 'ACTIVE', organization: { name: 'Đơn vị thực hiện QA ' + (i + 1) },
  currency: 'VND', targetAmount: 20000000, funding: { netRaised: 5000000 },
}));
const base = 'http://127.0.0.1:5181';
const vite = spawn('npm', ['run', 'dev', '--', '--host', '127.0.0.1', '--port', '5181', '--strictPort'],
  { cwd: process.cwd(), stdio: ['ignore', 'pipe', 'pipe'] });
let serverError = '';
vite.stderr.on('data', (chunk) => { serverError += String(chunk); });
const waitForServer = async () => {
  for (let attempt = 0; attempt < 100; attempt++) {
    try { const response = await fetch(base + '/campaigns'); if (response.ok) return; } catch { /* startup */ }
    await new Promise((r) => setTimeout(r, 250));
  }
  throw Error('Vite startup timeout: ' + serverError.slice(-1600));
};
let browser;
try {
  await waitForServer();
  browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1,
    reducedMotion: 'reduce' });
  page.on('pageerror', e => report.errors.push('JS: ' + String(e)));
  page.on('console', m => { if (m.type() === 'error' && !(forceError && m.text().includes('503'))) report.errors.push('Console: ' + m.text()); });
  let forceError = false;
  await page.route('**/api/v1/campaigns?**', async (route) => {
    const url = new URL(route.request().url());
    report.requests.push(Object.fromEntries(url.searchParams));
    if (forceError) {
      await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'QA Backend unavailable' }) });
      return;
    }
    const selected = url.searchParams.get('category'), q = (url.searchParams.get('q') ?? '').trim().toLowerCase();
    const current = Number(url.searchParams.get('page') ?? 0), size = Number(url.searchParams.get('size') ?? 9);
    const filtered = campaigns.filter(c => (!selected || c.category === selected) &&
      (!q || [c.title, c.shortDescription, c.category].join(' ').toLowerCase().includes(q)));
    const payload = { data: {
      data: filtered.slice(current * size, (current + 1) * size), currentPage: current,
      pageSize: size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size)
    } };
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payload) });
  });
  await page.route('**/api/v1/campaigns/slug/*', async route => {
    const id = route.request().url().split('/').pop();
    const item = campaigns.find(c => c.slug === id);
    await route.fulfill({ status: item ? 200 : 404, contentType: 'application/json',
      body: JSON.stringify({ data: item ?? null }) });
  });
  await page.route('**/api/v1/campaigns/*/milestones', async route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '{"data":[]}' }));

  await page.goto(base + '/campaigns', { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  const facts = () => page.evaluate(() => ({
    width: document.documentElement.scrollWidth, viewport: window.innerWidth,
    hero: !!document.querySelector('.community-journey-art'),
    artNodes: document.querySelectorAll('.community-journey-art .community-node').length,
    lead: document.querySelectorAll('#lead-section .stage').length,
    dossiers: document.querySelectorAll('#reading-results .dossier').length,
    pager: document.querySelector('.pagination-count')?.textContent,
    link: document.querySelector('#reading-results .read-profile')?.getAttribute('href'),
    badge: document.querySelector('#paper-section .section-number')?.textContent,
    disclaimer: document.querySelector('#paper-section .header-copy p')?.textContent,
    paginationVisible: !!document.querySelector('#pagination-landscape'),
    coverArt: !!document.querySelector('.shape-svg image'),
    valley: getComputedStyle(document.querySelector('.bottom-valley')).backgroundImage,
  }));
  const first = await facts();
  report.initial = first;
  check('React route renders real campaign data, no fixture DOM baked in',
    await page.locator('#lead-section .story h2').textContent() === campaigns[0].title, first);
  check('Desktop 1440 has no horizontal overflow', first.width <= 1440 && first.viewport === 1440, first);
  check('One featured + 8 paper dossiers from 9 API records', first.lead === 1 && first.dossiers === 8, first);
  check('Hero approved community landscape, no Section 05 number badge',
    first.hero && first.artNodes >= 3 && !first.badge, first);
  check('Section disclaimer describes Backend source and simulation',
    first.disclaimer?.includes('Backend') && first.disclaimer?.includes('mô phỏng'), first.disclaimer);
  check('Approved paper valley and featured artwork loaded',
    first.coverArt && first.valley?.includes('valley.jpg'), { valley: first.valley, coverArt: first.coverArt });
  check('ACTIVE filter and 9-record Backend page contract',
    report.requests[0]?.status === 'ACTIVE' && report.requests[0]?.size === '9' &&
    report.requests[0]?.page === '0', report.requests[0]);
  check('Detail CTA uses real /campaigns/:slug link', first.link === '/campaigns/qa-campaign-2', first.link);
  await page.screenshot({ path: resolve(evidence, 'campaign-react-1440-full.png'), fullPage: true, animations: 'disabled' });

  await page.locator('#reading-results .read-profile').first().click();
  await page.getByRole('heading', { name: campaigns[1].title }).waitFor();
  check('Details CTA navigates to existing React /campaigns/:slug route',
    page.url().endsWith('/campaigns/qa-campaign-2'), page.url());
  await page.goto(base + '/campaigns');
  await page.locator('#reading-results .dossier').first().waitFor();

  await page.locator('.cat[data-category="EDUCATION"]').click();
  await page.waitForURL('**/campaigns?category=EDUCATION');
  await page.locator('#reading-results .dossier').first().waitFor();
  check('Category filter server-side and page reset', (await facts()).dossiers === 2 &&
    report.requests.at(-1)?.category === 'EDUCATION' && report.requests.at(-1)?.page === '0',
    { requests: report.requests.slice(-1), facts: await facts() });
  await page.locator('.reset').click();
  await page.locator('#reading-results .dossier').nth(7).waitFor();

  await page.getByRole('button', { name: 'Trang 3' }).click();
  await page.waitForURL('**/campaigns?page=2');
  await page.getByRole('heading', { name: campaigns[18].title }).waitFor();
  check('Last page uses 0-based API page=2, 1 lead and zero dossier rows',
    (await facts()).lead === 1 && (await facts()).dossiers === 0 &&
    report.requests.at(-1)?.page === '2', report.requests.slice(-1));

  await page.locator('#campaign-query').fill('khong-co-chien-dich-phu-hop');
  await page.locator('.search-form button').click();
  await page.getByText('Không tìm thấy chiến dịch phù hợp').waitFor();
  check('Unmatched search renders genuine empty state; no fake cards',
    (await facts()).lead === 0 && (await facts()).dossiers === 0 &&
    report.requests.at(-1)?.q === 'khong-co-chien-dich-phu-hop', await facts());

  // React Query retries by default, so disable the network before remounting with a new query key;
  // only assert the error state after the failed request is surfaced.
  forceError = true;
  await page.goto(base + '/campaigns?q=qa-backend-error');
  await page.locator('.cd-error strong').waitFor({ timeout: 30000 });
  check('Backend failure shows error + retry instead of fabricated results',
    await page.getByRole('button', { name: 'Thử lại' }).isVisible(), null);
  check('No uncaught browser errors', report.errors.length === 0, report.errors);
  report.pass = true;
} catch (error) {
  report.failure = String(error);
  process.exitCode = 1;
  console.error(error);
} finally {
  await writeFile(resolve(evidence, 'qa-report.json'), JSON.stringify(report, null, 2), 'utf8');
  if (browser) await browser.close();
  vite.kill('SIGTERM');
}
console.log(JSON.stringify({ pass: report.pass, checks: report.checks.length, errors: report.errors, failure: report.failure }, null, 2));
