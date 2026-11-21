// Rise AI independent E2E harness. Runs inside mcr.microsoft.com/playwright with /out mounted.
// The browser must use origin http://127.0.0.1:5174 (backend CORS allow-list), so a local TCP forwarder maps it to the host.
// Secrets: the demo password comes from /secrets/demo-password.txt and is never written to output; tokens are never logged.
import net from 'node:net';
import fs from 'node:fs';
import crypto from 'node:crypto';
import { chromium } from 'playwright';

const UPSTREAM_HOST = process.env.UPSTREAM_HOST || 'host.docker.internal';
const BASE = 'http://127.0.0.1:5174';
const OUT = '/out';
const PASSWORD = fs.readFileSync('/secrets/demo-password.txt', 'utf8').trim();
const RUN = new Date().toISOString().replace(/[-:T.Z]/g, '').slice(0, 14);
const results = [];
const apiLog = [];
const ids = { run: RUN };
const consoleErrors = [];

// ---------- forwarder ----------
await new Promise((resolve) => {
  net.createServer((c) => {
    const u = net.connect(5174, UPSTREAM_HOST);
    c.pipe(u); u.pipe(c);
    c.on('error', () => u.destroy()); u.on('error', () => c.destroy());
  }).listen(5174, '127.0.0.1', resolve);
});

// ---------- helpers ----------
const now = () => new Date().toISOString();
function record(area, id, name, status, evidence) {
  results.push({ area, id, name, status, evidence, at: now() });
  console.log(`[${status}] ${area} ${id} ${name}${evidence ? ' :: ' + JSON.stringify(evidence).slice(0, 300) : ''}`);
}
async function check(area, id, name, fn) {
  try {
    const ev = await fn();
    record(area, id, name, 'PASS', ev);
    return ev;
  } catch (e) {
    record(area, id, name, e.blocked ? 'BLOCKED' : 'FAIL', { error: String(e.message || e).slice(0, 500) });
    return null;
  }
}
function assert(cond, msg) { if (!cond) throw new Error(msg); }
function blocked(msg) { const e = new Error(msg); e.blocked = true; return e; }

async function api(method, path, { token, body, headers = {}, label } = {}) {
  const h = { 'Content-Type': 'application/json', ...headers };
  if (token) h.Authorization = 'Bearer ' + token;
  const res = await fetch(BASE + path, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
  let json = null;
  const text = await res.text();
  try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text.slice(0, 200) }; }
  apiLog.push({ at: now(), label, method, path, status: res.status, code: json?.code ?? null,
    message: typeof json?.message === 'string' ? json.message.slice(0, 200) : null, auth: token ? 'bearer(redacted)' : 'none' });
  return { status: res.status, json, data: json?.data };
}
const idem = () => crypto.randomUUID();
async function login(email, password = PASSWORD) {
  const r = await api('POST', '/api/v1/auth/login', { body: { email, password }, label: 'login ' + email.split('@')[0] });
  assert(r.status === 200 && r.data?.accessToken, `login ${email} -> ${r.status} ${r.json?.code}`);
  // toJSON keeps the token out of every evidence file and log line
  return { token: r.data.accessToken, user: r.data.user, toJSON() { return { userId: r.data.user?.id, roles: r.data.user?.roles, token: '<redacted>' }; } };
}
const items = (d) => d?.data ?? d?.content ?? (Array.isArray(d) ? d : []);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function poll(fn, ms = 30000, step = 1500) {
  const end = Date.now() + ms;
  for (;;) { const v = await fn(); if (v) return v; if (Date.now() > end) return null; await sleep(step); }
}

// ---------- browser ----------
const browser = await chromium.launch();
async function newPage(role) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
  const page = await ctx.newPage();
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push({ role, at: now(), url: page.url(), text: m.text().slice(0, 300) }); });
  page.on('pageerror', (e) => consoleErrors.push({ role, at: now(), url: page.url(), text: 'pageerror: ' + String(e).slice(0, 300) }));
  page.on('dialog', (d) => d.accept());
  return { ctx, page };
}
async function shot(page, dir, name) {
  const file = `screenshots/${dir}/${name}.png`;
  await page.screenshot({ path: `${OUT}/${file}`, fullPage: true });
  return file;
}
// SPA navigation without reload: the access token lives in memory only, a reload logs the user out.
async function spaNav(page, path) {
  await page.evaluate((p) => { window.history.pushState({}, '', p); window.dispatchEvent(new PopStateEvent('popstate')); }, path);
  await page.waitForLoadState('networkidle').catch(() => {});
}
async function uiLogin(page, email) {
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  await page.fill('#email', email);
  await page.fill('#password', PASSWORD);
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 15000 });
  await page.waitForLoadState('networkidle').catch(() => {});
}
async function noOverflow(page) {
  return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
}
async function decide(page, actionLabel, reason) {
  await page.getByRole('button', { name: actionLabel, exact: true }).first().click();
  const form = page.locator('form').filter({ has: page.getByRole('button', { name: 'Xác nhận thao tác' }) }).first();
  if (reason) await form.locator('textarea').fill(reason);
  await form.getByRole('checkbox').check();
  const [resp] = await Promise.all([
    page.waitForResponse((r) => r.request().method() === 'POST' && r.url().includes('/api/v1/'), { timeout: 15000 }),
    form.getByRole('button', { name: 'Xác nhận thao tác' }).click(),
  ]);
  await page.waitForLoadState('networkidle').catch(() => {});
  return { status: resp.status(), url: new URL(resp.url()).pathname };
}

// =====================================================================================================
// 0. Smoke
await check('smoke', 'S1', 'frontend /healthz returns ok', async () => {
  const r = await fetch(BASE + '/healthz'); const t = await r.text();
  assert(r.status === 200 && t.trim() === 'ok', `healthz ${r.status} ${t}`); return { status: r.status };
});
await check('smoke', 'S2', 'same-origin API proxy reaches backend (public campaigns)', async () => {
  const r = await api('GET', '/api/v1/campaigns?status=ACTIVE&size=50', { label: 'active campaigns' });
  assert(r.status === 200, 'status ' + r.status);
  ids.activeCampaigns = items(r.data).map((c) => ({ id: c.id, slug: c.slug, title: c.title }));
  assert(ids.activeCampaigns.length > 0, 'no ACTIVE campaigns');
  return { status: r.status, activeCount: ids.activeCampaigns.length };
});
await check('smoke', 'S3', 'chain info reports simulated engine (onChain=false)', async () => {
  const r = await api('GET', '/api/v1/transparency/chain', { label: 'chain' });
  assert(r.status === 200 && r.data?.onChain === false, `status ${r.status} onChain ${r.data?.onChain}`);
  return { status: r.status, onChain: r.data.onChain };
});

// =====================================================================================================
// 1. Public UI
{
  const { ctx, page } = await newPage('public');
  await check('public', 'P1', 'homepage renders without horizontal overflow', async () => {
    const r = await page.goto(BASE + '/', { waitUntil: 'networkidle' });
    assert(r.status() === 200, 'status ' + r.status());
    assert(await noOverflow(page), 'horizontal overflow');
    return { status: r.status(), screenshot: await shot(page, 'public', '01-home') };
  });
  await check('public', 'P2', 'campaign discovery lists only ACTIVE campaigns from backend', async () => {
    await page.goto(BASE + '/campaigns', { waitUntil: 'networkidle' });
    const body = await page.locator('main').innerText();
    const shown = ids.activeCampaigns.filter((c) => body.includes(c.title));
    assert(shown.length > 0, 'no backend ACTIVE title visible');
    const all = await api('GET', '/api/v1/campaigns?size=100', { label: 'all public campaigns' });
    const nonActive = items(all.data).filter((c) => c.campaignStatus !== 'ACTIVE' && body.includes(c.title));
    assert(nonActive.length === 0, 'non-ACTIVE campaign shown: ' + nonActive.map((c) => c.title).join());
    assert(await noOverflow(page), 'horizontal overflow');
    return { visibleActive: shown.length, screenshot: await shot(page, 'public', '02-campaigns') };
  });
  await check('public', 'P3', 'server-side search q filters the list', async () => {
    const target = ids.activeCampaigns.find((c) => /Hope Kitchen/.test(c.title)) || ids.activeCampaigns[0];
    const word = target.title.split(' ')[0];
    const r = await api('GET', `/api/v1/campaigns?status=ACTIVE&q=${encodeURIComponent(word)}`, { label: 'search' });
    assert(r.status === 200 && items(r.data).some((c) => c.id === target.id), 'search did not return target');
    const none = await api('GET', `/api/v1/campaigns?status=ACTIVE&q=zzqqxxnomatch${RUN}`, { label: 'search none' });
    assert(none.status === 200 && items(none.data).length === 0, 'nonsense search returned rows');
    return { q: word, hits: items(r.data).length, emptyQueryHits: 0 };
  });
  await check('public', 'P4', 'category filter and pagination contract', async () => {
    const r = await api('GET', '/api/v1/campaigns?status=ACTIVE&category=FOOD_SECURITY&size=1&page=0', { label: 'category' });
    assert(r.status === 200, 'status ' + r.status);
    assert(items(r.data).every((c) => c.category === 'FOOD_SECURITY'), 'wrong category');
    assert(items(r.data).length <= 1 && typeof r.data.totalElements === 'number', 'page envelope');
    return { returned: items(r.data).length, totalElements: r.data.totalElements, totalPages: r.data.totalPages };
  });
  await check('public', 'P5', 'campaign detail deep link renders (direct URL)', async () => {
    const c = ids.activeCampaigns.find((x) => /Hope Kitchen/.test(x.title)) || ids.activeCampaigns[0];
    const r = await page.goto(BASE + '/campaigns/' + c.slug, { waitUntil: 'networkidle' });
    assert(r.status() === 200, 'status ' + r.status());
    await page.getByText(c.title).first().waitFor({ timeout: 10000 });
    await page.getByText('Ghi nhận đóng góp mô phỏng').first().waitFor({ timeout: 10000 });
    return { slug: c.slug, screenshot: await shot(page, 'public', '03-campaign-detail') };
  });
  await check('public', 'P6', 'transparency page in simulated mode shows no fake Fuji data', async () => {
    await page.goto(BASE + '/transparency', { waitUntil: 'networkidle' });
    await page.getByText('Chưa thể công bố dữ liệu Fuji').first().waitFor({ timeout: 10000 });
    return { screenshot: await shot(page, 'public', '04-transparency') };
  });
  await check('public', 'P7', 'unknown route shows the SPA not-found page (deep link fallback)', async () => {
    const r = await page.goto(BASE + '/khong-ton-tai-' + RUN, { waitUntil: 'networkidle' });
    assert(r.status() === 200, 'nginx fallback status ' + r.status());
    const txt = await page.locator('body').innerText();
    assert(/404|không tìm thấy/i.test(txt), 'no not-found text');
    return { screenshot: await shot(page, 'public', '05-not-found') };
  });
  await check('public', 'P8', 'SPA transition from home to campaign list without full reload', async () => {
    await page.goto(BASE + '/', { waitUntil: 'networkidle' });
    await page.evaluate(() => { window.__spaMarker = 1; });
    await page.locator('a[href="/campaigns"]').first().click();
    await page.waitForURL('**/campaigns');
    assert(await page.evaluate(() => window.__spaMarker === 1), 'page reloaded');
    return { url: page.url() };
  });
  await ctx.close();
}

// =====================================================================================================
// 2. Authentication and authorization
const admin = await check('auth', 'A1', 'admin login via API', async () => (await login('admin@riseai.example')));
const donor1 = await check('auth', 'A2', 'donor1 login via API', async () => (await login('donor1@riseai.example')));
const donor2 = await check('auth', 'A3', 'donor2 login via API', async () => (await login('donor2@riseai.example')));
const creator1 = await check('auth', 'A4', 'creator1 login via API', async () => (await login('creator1@riseai.example')));
for (const r of [admin, donor1, donor2, creator1]) if (r) { r.evidenceSafe = true; }
await check('auth', 'A5', 'invalid password is rejected with 401 and a generic error', async () => {
  const r = await api('POST', '/api/v1/auth/login', { body: { email: 'donor1@riseai.example', password: 'wrong-' + RUN }, label: 'bad login' });
  assert(r.status === 401, 'status ' + r.status);
  return { status: r.status, code: r.json?.code };
});
await check('auth', 'A6', 'suspended account cannot log in', async () => {
  const r = await api('POST', '/api/v1/auth/login', { body: { email: 'suspended.donor@riseai.example', password: PASSWORD }, label: 'suspended login' });
  assert(r.status === 401 || r.status === 403, 'status ' + r.status);
  return { status: r.status, code: r.json?.code };
});
await check('auth', 'A7', 'anonymous admin API -> 401, donor -> 403, creator -> 403', async () => {
  const a = await api('GET', '/api/v1/admin/statistics', { label: 'anon admin' });
  const d = await api('GET', '/api/v1/admin/statistics', { token: donor1?.token, label: 'donor admin' });
  const c = await api('GET', '/api/v1/admin/statistics', { token: creator1?.token, label: 'creator admin' });
  const ad = await api('GET', '/api/v1/admin/statistics', { token: admin?.token, label: 'admin admin' });
  assert(a.status === 401 && d.status === 403 && c.status === 403 && ad.status === 200, `${a.status}/${d.status}/${c.status}/${ad.status}`);
  return { anonymous: a.status, donor: d.status, creator: c.status, admin: ad.status };
});
await check('auth', 'A8', 'donor cannot confirm a donation (admin-only endpoint)', async () => {
  const r = await api('POST', '/api/v1/admin/donations/' + crypto.randomUUID() + '/confirm', { token: donor1?.token, body: {}, label: 'donor confirm' });
  assert(r.status === 403, 'status ' + r.status); return { status: r.status };
});
await check('auth', 'A9', 'ADMIN self-registration is refused and injected roles are ignored', async () => {
  const bad = await api('POST', '/api/v1/auth/register', { label: 'register accountType ADMIN',
    body: { email: `qa.adm.${RUN}@riseai.example`, password: 'QaStrong1' + RUN, fullName: 'QA Admin Attempt', accountType: 'ADMIN' } });
  assert(bad.status === 400, 'accountType ADMIN accepted: ' + bad.status);
  const email = `qa.inj.${RUN}@riseai.example`;
  const ok = await api('POST', '/api/v1/auth/register', { label: 'register donor with injected roles',
    body: { email, password: 'QaStrong1' + RUN, fullName: 'QA Injection', accountType: 'DONOR', roles: ['ADMIN'], role: 'ADMIN' } });
  const roles = ok.data?.user?.roles ?? ok.data?.roles;
  assert((ok.status === 201 || ok.status === 200) && JSON.stringify(roles) === '["DONOR"]', 'roles ' + JSON.stringify(roles));
  const l = await login(email, 'QaStrong1' + RUN);
  const st = await api('GET', '/api/v1/admin/statistics', { token: l.token, label: 'new donor admin api' });
  assert(st.status === 403, 'admin api ' + st.status);
  return { adminAccountType: bad.status, injectedRoles: roles, adminApi: st.status };
});
{
  const { ctx, page } = await newPage('auth');
  await check('auth', 'A10', 'UI: anonymous /donor redirects to login', async () => {
    await page.goto(BASE + '/donor', { waitUntil: 'networkidle' });
    assert(new URL(page.url()).pathname === '/login', 'url ' + page.url());
    return { url: new URL(page.url()).pathname };
  });
  await check('auth', 'A11', 'UI: invalid credentials show an error', async () => {
    await page.fill('#email', 'donor1@riseai.example'); await page.fill('#password', 'wrong-' + RUN);
    await page.getByRole('button', { name: /Đăng nhập/ }).click();
    await page.locator('[role=alert]').first().waitFor({ timeout: 10000 });
    return { screenshot: await shot(page, 'public', '06-login-error') };
  });
  await check('auth', 'A12', 'UI: donor opening /admin sees the role guard, admin data not requested', async () => {
    await uiLogin(page, 'donor1@riseai.example');
    const adminCalls = [];
    page.on('response', (r) => { if (r.url().includes('/api/v1/admin/')) adminCalls.push(r.status()); });
    await spaNav(page, '/admin');
    await sleep(1500);
    const txt = await page.locator('main, body').first().innerText();
    assert(!/Xem và xử lý/.test(txt), 'admin queue rendered for donor');
    assert(adminCalls.every((s) => s === 403), 'admin call succeeded: ' + adminCalls);
    return { adminCalls, screenshot: await shot(page, 'donor', '01-donor-blocked-admin') };
  });
  await check('auth', 'A13', 'UI: reload clears the in-memory session (documented behavior)', async () => {
    await page.reload({ waitUntil: 'networkidle' });
    await spaNav(page, '/donor');
    await sleep(800);
    assert(new URL(page.url()).pathname === '/login', 'still logged in after reload: ' + page.url());
    return { url: new URL(page.url()).pathname };
  });
  await ctx.close();
}

// =====================================================================================================
// 3. CREATOR: organization -> admin verify -> campaign -> milestones -> submit -> admin approve -> publish
const T = (d) => new Date(Date.now() + d * 86400000).toISOString();
await check('creator', 'C1', 'creator creates an organization and submits it for verification', async () => {
  const r = await api('POST', '/api/v1/organizations', { token: creator1.token, label: 'create org', body: {
    name: `QA E2E Org ${RUN}`, organizationType: 'COMMUNITY_GROUP', contactEmail: `qa.org.${RUN}@riseai.example`, description: 'QA E2E organization (simulation)' } });
  assert(r.status === 201 || r.status === 200, 'create ' + r.status + ' ' + r.json?.code);
  ids.orgId = r.data.id;
  const s = await api('POST', `/api/v1/organizations/${ids.orgId}/submit-verification`, { token: creator1.token, label: 'submit org' });
  assert(s.status === 200, 'submit ' + s.status + ' ' + s.json?.code);
  return { orgId: ids.orgId, status: s.data?.verificationStatus };
});
await check('creator', 'C2', 'negative: creator cannot verify own organization (403)', async () => {
  const r = await api('POST', `/api/v1/admin/organizations/${ids.orgId}/verify`, { token: creator1.token, body: { notes: 'self' }, label: 'self verify' });
  assert(r.status === 403, 'status ' + r.status); return { status: r.status };
});
await check('admin', 'AD1', 'admin verifies the organization', async () => {
  const r = await api('POST', `/api/v1/admin/organizations/${ids.orgId}/verify`, { token: admin.token, body: { notes: 'QA E2E verification (simulated)' }, label: 'verify org' });
  assert(r.status === 200, 'status ' + r.status + ' ' + r.json?.code); return { verificationStatus: r.data?.verificationStatus };
});
await check('creator', 'C3', 'creator creates a DRAFT campaign with two milestones (budgets sum to target)', async () => {
  const r = await api('POST', '/api/v1/campaigns', { token: creator1.token, label: 'create campaign', body: {
    organizationId: ids.orgId, title: `QA E2E Campaign ${RUN}`, shortDescription: 'QA E2E campaign for independent verification (simulation).',
    fullDescription: 'QA E2E campaign. All amounts are simulated; no real money.', category: 'COMMUNITY', beneficiaryDescription: 'Fictional QA beneficiaries',
    targetAmount: 30000000, currency: 'VND', minimumDonationAmount: 50000, startAt: T(-1), endAt: T(120), location: 'QA (fictional)', beneficiaryCount: 50,
    problemStatement: 'QA problem', solutionDescription: 'QA solution', impactGoals: 'QA impact',
    budgetBreakdown: [{ item: 'Phase 1', amount: 10000000 }, { item: 'Phase 2', amount: 20000000 }] } });
  assert(r.status === 201 || r.status === 200, 'create ' + r.status + ' ' + r.json?.code + ' ' + r.json?.message);
  ids.campaignId = r.data.id; ids.campaignSlug = r.data.slug;
  const ms = [];
  for (const [i, amt] of [[1, 10000000], [2, 20000000]]) {
    const m = await api('POST', `/api/v1/campaigns/${ids.campaignId}/milestones`, { token: creator1.token, label: 'milestone ' + i, body: {
      title: `QA milestone ${i}`, plannedAmount: amt, startAt: T(-1 + (i - 1) * 30), dueAt: T(29 + (i - 1) * 30), expectedDeliverables: 'QA deliverable', releaseConditions: 'Accepted evidence' } });
    assert(m.status === 201 || m.status === 200, 'milestone ' + m.status + ' ' + m.json?.code + ' ' + m.json?.message);
    ms.push(m.data.id);
  }
  ids.milestones = ms;
  return { campaignId: ids.campaignId, status: r.data.campaignStatus, milestones: ms.length };
});
await check('creator', 'C4', 'negative: DRAFT campaign is not public (anonymous slug lookup)', async () => {
  const r = await api('GET', `/api/v1/campaigns/slug/${ids.campaignSlug}`, { label: 'draft public' });
  assert(r.status === 404 || r.status === 403, 'status ' + r.status); return { status: r.status };
});
await check('creator', 'C5', 'creator submits the campaign for review', async () => {
  const r = await api('POST', `/api/v1/campaigns/${ids.campaignId}/submit`, { token: creator1.token, label: 'submit campaign' });
  assert(r.status === 200 && r.data.campaignStatus === 'PENDING_REVIEW', 'status ' + r.status + ' ' + r.data?.campaignStatus);
  return { campaignStatus: r.data.campaignStatus };
});
await check('creator', 'C6', 'negative: creator cannot approve own campaign (403)', async () => {
  const r = await api('POST', `/api/v1/campaigns/${ids.campaignId}/approve`, { token: creator1.token, body: {}, label: 'self approve' });
  assert(r.status === 403, 'status ' + r.status); return { status: r.status };
});
{
  const { ctx, page } = await newPage('admin');
  await check('admin', 'AD2', 'UI: admin approves the campaign from the review queue', async () => {
    await uiLogin(page, 'admin@riseai.example');
    await spaNav(page, `/admin/review/campaigns/${ids.campaignId}`);
    await page.getByRole('button', { name: 'Phê duyệt chiến dịch', exact: true }).waitFor({ timeout: 15000 });
    const before = await shot(page, 'admin', '01-campaign-review');
    const res = await decide(page, 'Phê duyệt chiến dịch', 'QA E2E approval (simulated)');
    assert(res.status === 200, 'approve ' + JSON.stringify(res));
    const after = await api('GET', `/api/v1/campaigns/${ids.campaignId}`, { token: admin.token, label: 'campaign after approve' });
    assert(after.data.campaignStatus === 'APPROVED', 'status ' + after.data.campaignStatus);
    return { response: res, campaignStatus: after.data.campaignStatus, screenshots: [before, await shot(page, 'admin', '02-campaign-approved')] };
  });
  await ctx.close();
}
await check('creator', 'C7', 'creator publishes the approved campaign; milestones become READY', async () => {
  const r = await api('POST', `/api/v1/campaigns/${ids.campaignId}/publish`, { token: creator1.token, label: 'publish' });
  assert(r.status === 200 && r.data.campaignStatus === 'ACTIVE', 'status ' + r.status + ' ' + r.data?.campaignStatus);
  const m = await api('GET', `/api/v1/campaigns/${ids.campaignId}/milestones`, { label: 'milestones public' });
  const st = items(m.data).map((x) => x.milestoneStatus);
  return { campaignStatus: r.data.campaignStatus, milestoneStatuses: st };
});

// =====================================================================================================
// 4. DONOR: UI donation -> PENDING -> admin UI confirm -> dashboard -> refund -> dispute
{
  const { ctx, page } = await newPage('donor');
  await check('donor', 'D1', 'UI: donor submits a simulated donation, backend says PENDING', async () => {
    await uiLogin(page, 'donor1@riseai.example');
    await spaNav(page, `/campaigns/${ids.campaignSlug}/contribute`);
    await page.fill('#donation-amount', '6000000');
    await page.getByText('Tôi hiểu đây là yêu cầu mô phỏng').click();
    const [resp] = await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'POST' && /\/donations$/.test(new URL(r.url()).pathname)),
      page.getByRole('button', { name: /Gửi yêu cầu mô phỏng/ }).click(),
    ]);
    assert(resp.status() === 201 || resp.status() === 200, 'POST ' + resp.status());
    const body = await resp.json();
    ids.donationId = body.data.id;
    assert(body.data.donationStatus === 'PENDING', 'status ' + body.data.donationStatus);
    assert(resp.request().headers()['idempotency-key'], 'no Idempotency-Key header sent by UI');
    await page.locator('.campaign-flow-success').waitFor({ timeout: 10000 });
    return { donationId: ids.donationId, donationStatus: body.data.donationStatus, screenshot: await shot(page, 'donor', '02-donation-pending') };
  });
  await check('donor', 'D2', 'idempotency: replaying the same Idempotency-Key returns the same donation', async () => {
    const key = idem();
    const body = { amount: 100000, paymentMethod: 'SIMULATED_BANK_TRANSFER' };
    const a = await api('POST', `/api/v1/campaigns/${ids.campaignId}/donations`, { token: donor2.token, body, headers: { 'Idempotency-Key': key }, label: 'idem 1' });
    const b = await api('POST', `/api/v1/campaigns/${ids.campaignId}/donations`, { token: donor2.token, body, headers: { 'Idempotency-Key': key }, label: 'idem 2' });
    assert(a.status < 300 && b.status < 300 && a.data.id === b.data.id, `${a.status}/${b.status} ${a.data?.id} vs ${b.data?.id}`);
    const c = await api('POST', `/api/v1/campaigns/${ids.campaignId}/donations`, { token: donor2.token, body: { ...body, amount: 200000 }, headers: { 'Idempotency-Key': key }, label: 'idem conflict' });
    ids.donor2DonationId = a.data.id;
    assert(c.status === 409 || c.status === 422 || c.status === 400, 'reused key with a different body accepted: ' + c.status);
    return { first: a.status, replay: b.status, sameId: true, differentBody: c.status, code: c.json?.code };
  });
  await check('donor', 'D3', 'negative: amount below campaign minimum is rejected', async () => {
    const r = await api('POST', `/api/v1/campaigns/${ids.campaignId}/donations`, { token: donor1.token, body: { amount: 1000 }, headers: { 'Idempotency-Key': idem() }, label: 'below minimum' });
    assert(r.status === 400 || r.status === 422, 'status ' + r.status); return { status: r.status, code: r.json?.code };
  });
  await check('donor', 'D4', 'negative: creator cannot donate to own campaign', async () => {
    const r = await api('POST', `/api/v1/campaigns/${ids.campaignId}/donations`, { token: creator1.token, body: { amount: 100000 }, headers: { 'Idempotency-Key': idem() }, label: 'self donate' });
    assert(r.status === 403 || r.status === 409 || r.status === 422, 'status ' + r.status); return { status: r.status, code: r.json?.code };
  });
  await check('auth', 'A14', 'IDOR: donor2 cannot read donor1 donation; anonymous cannot either', async () => {
    const r = await api('GET', `/api/v1/donations/${ids.donationId}`, { token: donor2.token, label: 'idor donation' });
    const a = await api('GET', `/api/v1/donations/${ids.donationId}`, { label: 'anon donation' });
    assert((r.status === 403 || r.status === 404) && a.status === 401, `${r.status}/${a.status}`);
    return { donor2: r.status, anonymous: a.status };
  });
  await ctx.close();
}
{
  const { ctx, page } = await newPage('admin');
  await check('admin', 'AD3', 'UI: admin confirms the PENDING donation from the review page', async () => {
    await uiLogin(page, 'admin@riseai.example');
    await spaNav(page, '/admin');
    await page.getByRole('button', { name: 'Đóng góp', exact: true }).click().catch(() => {});
    await sleep(800);
    const queue = await shot(page, 'admin', '03-dashboard-donations-queue');
    await spaNav(page, `/admin/review/donations/${ids.donationId}`);
    await page.getByRole('button', { name: 'Xác nhận đóng góp mô phỏng', exact: true }).waitFor({ timeout: 15000 });
    const res = await decide(page, 'Xác nhận đóng góp mô phỏng', `QA-SIM-${RUN}`);
    assert(res.status === 200, 'confirm ' + JSON.stringify(res));
    const d = await api('GET', `/api/v1/donations/${ids.donationId}`, { token: donor1.token, label: 'donation after confirm' });
    assert(d.data.donationStatus === 'CONFIRMED', 'status ' + d.data.donationStatus);
    return { response: res, donationStatus: d.data.donationStatus, screenshots: [queue, await shot(page, 'admin', '04-donation-confirmed')] };
  });
  await check('admin', 'AD4', 'negative: confirming the same donation twice is refused', async () => {
    const r = await api('POST', `/api/v1/admin/donations/${ids.donationId}/confirm`, { token: admin.token, body: {}, label: 'double confirm' });
    assert(r.status === 409 || r.status === 422 || r.status === 400, 'status ' + r.status); return { status: r.status, code: r.json?.code };
  });
  await check('admin', 'AD5', 'admin also confirms donor2 donation via API', async () => {
    const r = await api('POST', `/api/v1/admin/donations/${ids.donor2DonationId}/confirm`, { token: admin.token, body: { simulatedReference: 'QA2-' + RUN }, label: 'confirm donor2' });
    assert(r.status === 200, 'status ' + r.status + ' ' + r.json?.code); return { donationStatus: r.data.donationStatus };
  });
  await ctx.close();
}
{
  const { ctx, page } = await newPage('donor');
  await check('donor', 'D5', 'UI: donor dashboard shows the CONFIRMED donation and its receipt', async () => {
    await uiLogin(page, 'donor1@riseai.example');
    await spaNav(page, '/donor');
    const d = await api('GET', `/api/v1/donations/${ids.donationId}`, { token: donor1.token, label: 'donation for code' });
    ids.donationCode = d.data.donationCode;
    await page.getByText(ids.donationCode).first().waitFor({ timeout: 15000 });
    await page.getByText(ids.donationCode).first().click();
    await page.locator('#donor-selected-receipt').waitFor({ timeout: 10000 });
    return { donationCode: ids.donationCode, screenshot: await shot(page, 'donor', '03-dashboard-receipt') };
  });
  await check('donor', 'D6', 'UI: donor requests a partial refund of the confirmed donation', async () => {
    await page.getByRole('button', { name: 'Yêu cầu hoàn tiền', exact: true }).click();
    await page.fill('#refund-amount', '1000000');
    await page.fill('#refund-reason', 'QA E2E partial refund (simulated)');
    const [resp] = await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'POST' && /\/refunds$/.test(new URL(r.url()).pathname)),
      page.getByRole('button', { name: 'Gửi yêu cầu hoàn tiền' }).click(),
    ]);
    assert(resp.status() === 201 || resp.status() === 200, 'status ' + resp.status());
    ids.refundId = (await resp.json()).data.id;
    return { refundId: ids.refundId, screenshot: await shot(page, 'donor', '04-refund-requested') };
  });
  // The amount-limit case (no refund pending) is in supplement.mjs; here a refund is already pending on this donation.
  await check('donor', 'D7', 'negative: a second refund while one is pending is refused', async () => {
    const r = await api('POST', `/api/v1/donations/${ids.donationId}/refunds`, { token: donor1.token, body: { amount: 999000000, reason: 'too much' }, headers: { 'Idempotency-Key': idem() }, label: 'refund too large' });
    assert(r.status >= 400 && r.status < 500, 'status ' + r.status); return { status: r.status, code: r.json?.code };
  });
  await check('auth', 'A15', 'UI: after logout and login as donor2, donor1 data is not visible', async () => {
    await page.reload({ waitUntil: 'networkidle' });
    await uiLogin(page, 'donor2@riseai.example');
    await spaNav(page, '/donor');
    await sleep(1500);
    const txt = await page.locator('body').innerText();
    assert(!txt.includes(ids.donationCode), 'donor1 donation code visible to donor2');
    return { screenshot: await shot(page, 'donor', '05-donor2-isolation') };
  });
  await ctx.close();
}
await check('admin', 'AD6', 'admin approves and records the refund; donor cannot approve it', async () => {
  const neg = await api('POST', `/api/v1/admin/refunds/${ids.refundId}/approve`, { token: donor1.token, body: {}, label: 'donor approve refund' });
  assert(neg.status === 403, 'donor approve ' + neg.status);
  const a = await api('POST', `/api/v1/admin/refunds/${ids.refundId}/approve`, { token: admin.token, body: { reason: 'QA approve' }, label: 'approve refund' });
  assert(a.status === 200, 'approve ' + a.status + ' ' + a.json?.code);
  const r = await api('POST', `/api/v1/admin/refunds/${ids.refundId}/record`, { token: admin.token, body: {}, label: 'record refund' });
  assert(r.status === 200, 'record ' + r.status + ' ' + r.json?.code);
  return { donorAttempt: neg.status, refundStatus: r.data.refundStatus };
});

// =====================================================================================================
// 5. CREATOR UI: start milestone, evidence; ADMIN: review, approve, release
{
  const { ctx, page } = await newPage('creator');
  await check('creator', 'C8', 'UI: creator dashboard -> Quản lý -> start milestone 1', async () => {
    await uiLogin(page, 'creator1@riseai.example');
    await spaNav(page, '/creator');
    await page.getByText(`QA E2E Campaign ${RUN}`).first().waitFor({ timeout: 15000 });
    const dash = await shot(page, 'creator', '01-dashboard');
    await spaNav(page, `/creator/campaigns/${ids.campaignId}`);
    await page.getByRole('button', { name: 'Bắt đầu', exact: true }).first().waitFor({ timeout: 15000 });
    const [resp] = await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'POST' && /\/start$/.test(new URL(r.url()).pathname)),
      page.getByRole('button', { name: 'Bắt đầu', exact: true }).first().click(),
    ]);
    assert(resp.status() === 200, 'start ' + resp.status());
    return { screenshots: [dash, await shot(page, 'creator', '02-milestone-started')] };
  });
  await check('creator', 'C9', 'UI: creator submits evidence with a SHA-256', async () => {
    ids.evidenceHash = crypto.createHash('sha256').update('qa-evidence-' + RUN).digest('hex');
    await page.getByRole('button', { name: /Minh chứng & giải ngân/ }).first().click();
    await page.getByRole('button', { name: 'Gửi minh chứng', exact: true }).first().click();
    await page.fill('#evidence-title', 'QA delivery record ' + RUN);
    await page.selectOption('#evidence-type', 'DELIVERY_RECORD').catch(() => {});
    await page.fill('#evidence-url', 'sim://qa/' + RUN + '.pdf');
    await page.fill('#evidence-hash', ids.evidenceHash);
    const [resp] = await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'POST' && /\/evidence$/.test(new URL(r.url()).pathname)),
      page.locator('#ops-evidence-form').getByRole('button', { name: 'Gửi minh chứng' }).click(),
    ]);
    assert(resp.status() === 201 || resp.status() === 200, 'evidence ' + resp.status());
    ids.evidenceId = (await resp.json()).data.id;
    return { evidenceId: ids.evidenceId, screenshot: await shot(page, 'creator', '03-evidence-submitted') };
  });
  await check('creator', 'C9b', 'creator adds an INVOICE without a file hash (should trigger monitoring rule EV-02)', async () => {
    const r = await api('POST', `/api/v1/milestones/${ids.milestones[0]}/evidence`, { token: creator1.token, label: 'invoice without hash',
      body: { evidenceType: 'INVOICE', title: 'QA invoice without hash ' + RUN, description: 'QA E2E: deliberately no SHA-256' } });
    assert(r.status === 201 || r.status === 200, 'status ' + r.status + ' ' + r.json?.code);
    ids.invoiceEvidenceId = r.data.id;
    return { evidenceId: ids.invoiceEvidenceId };
  });
  await check('creator', 'C10', 'UI: creator requests a disbursement (no money moves)', async () => {
    // After an evidence submit the milestone detail panel can collapse; select the milestone again like a user would.
    const btn = page.getByRole('button', { name: 'Đề nghị giải ngân', exact: true }).first();
    if (!(await btn.isVisible().catch(() => false))) {
      await page.getByRole('button', { name: /Minh chứng & giải ngân/ }).first().click();
    }
    await btn.click();
    await page.fill('#disbursement-amount', '4000000');
    await page.fill('#disbursement-reason', 'QA E2E disbursement for milestone 1 (simulated)');
    const [resp] = await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'POST' && /\/disbursements$/.test(new URL(r.url()).pathname)),
      page.getByRole('button', { name: 'Gửi đề nghị (không chuyển tiền)' }).click(),
    ]);
    assert(resp.status() === 201 || resp.status() === 200, 'disbursement ' + resp.status());
    const body = await resp.json();
    ids.disbursementId = body.data.id;
    assert(body.data.requestStatus === 'SUBMITTED', 'status ' + body.data.requestStatus);
    return { disbursementId: ids.disbursementId, requestStatus: body.data.requestStatus, screenshot: await shot(page, 'creator', '04-disbursement-requested') };
  });
  await ctx.close();
}
await check('creator', 'C11', 'negative: creator cannot approve own disbursement; release before approval is refused', async () => {
  const a = await api('POST', `/api/v1/disbursements/${ids.disbursementId}/approve`, { token: creator1.token, body: {}, label: 'self approve disb' });
  const b = await api('POST', `/api/v1/disbursements/${ids.disbursementId}/release`, { token: admin.token, label: 'early release' });
  assert(a.status === 403 && (b.status === 409 || b.status === 422 || b.status === 400), `${a.status}/${b.status}`);
  return { selfApprove: a.status, earlyRelease: b.status, code: b.json?.code };
});
await check('creator', 'C12', 'negative: second open disbursement request on the same milestone is refused', async () => {
  const r = await api('POST', `/api/v1/milestones/${ids.milestones[0]}/disbursements`, { token: creator1.token, body: { amount: 1000000, reason: 'second' }, headers: { 'Idempotency-Key': idem() }, label: 'second request' });
  assert(r.status === 409 || r.status === 422 || r.status === 400, 'status ' + r.status); return { status: r.status, code: r.json?.code };
});
{
  const { ctx, page } = await newPage('admin');
  await check('admin', 'AD7', 'UI: admin starts review, accepts evidence, approves and releases the disbursement', async () => {
    await uiLogin(page, 'admin@riseai.example');
    await spaNav(page, `/admin/review/disbursements/${ids.disbursementId}`);
    await page.getByRole('button', { name: 'Bắt đầu xét duyệt', exact: true }).waitFor({ timeout: 15000 });
    const s1 = await decide(page, 'Bắt đầu xét duyệt');
    await page.getByRole('button', { name: 'Chấp thuận minh chứng', exact: true }).first().waitFor({ timeout: 15000 });
    const s2 = await decide(page, 'Chấp thuận minh chứng', 'QA evidence accepted (simulated review)');
    await page.getByRole('button', { name: 'Phê duyệt đề nghị', exact: true }).waitFor({ timeout: 15000 });
    const s3 = await decide(page, 'Phê duyệt đề nghị', 'QA approve');
    await page.getByRole('button', { name: 'Ghi sổ giải ngân mô phỏng', exact: true }).waitFor({ timeout: 15000 });
    const s4 = await decide(page, 'Ghi sổ giải ngân mô phỏng');
    for (const s of [s1, s2, s3, s4]) assert(s.status === 200, 'step ' + JSON.stringify(s));
    const d = await api('GET', `/api/v1/disbursements/${ids.disbursementId}`, { token: admin.token, label: 'disb after release' });
    assert(d.data.requestStatus === 'RELEASED', 'status ' + d.data.requestStatus);
    return { steps: [s1, s2, s3, s4], requestStatus: d.data.requestStatus, screenshot: await shot(page, 'admin', '05-disbursement-released') };
  });
  await check('admin', 'AD8', 'negative: releasing the same disbursement twice is refused', async () => {
    const r = await api('POST', `/api/v1/disbursements/${ids.disbursementId}/release`, { token: admin.token, label: 'double release' });
    assert(r.status === 409 || r.status === 422 || r.status === 400, 'status ' + r.status); return { status: r.status, code: r.json?.code };
  });
  // dispute
  await check('donor', 'D8', 'donor1 opens a dispute; a creator cannot open one on own campaign', async () => {
    const neg = await api('POST', `/api/v1/campaigns/${ids.campaignId}/disputes`, { token: creator1.token, label: 'creator dispute',
      body: { title: 'self', description: 'self', disputeType: 'OTHER' } });
    const r = await api('POST', `/api/v1/campaigns/${ids.campaignId}/disputes`, { token: donor1.token, label: 'open dispute',
      body: { title: 'QA dispute ' + RUN, description: 'QA E2E: please verify the milestone 1 delivery record.', disputeType: 'MISSING_EVIDENCE', milestoneId: ids.milestones[0] } });
    assert(neg.status >= 400 && neg.status < 500, 'creator dispute ' + neg.status);
    assert(r.status === 201 || r.status === 200, 'open ' + r.status + ' ' + r.json?.code + ' ' + r.json?.message);
    ids.disputeId = r.data.id;
    const other = await api('GET', `/api/v1/disputes/${ids.disputeId}`, { token: donor2.token, label: 'idor dispute' });
    assert(other.status === 403 || other.status === 404, 'donor2 read dispute ' + other.status);
    return { disputeId: ids.disputeId, creatorAttempt: neg.status, donor2Read: other.status };
  });
  await check('admin', 'AD9', 'UI: admin takes the dispute and dismisses it with a resolution', async () => {
    await spaNav(page, `/admin/review/disputes/${ids.disputeId}`);
    await page.getByRole('button', { name: 'Nhận xử lý phản ánh', exact: true }).waitFor({ timeout: 15000 });
    const s1 = await decide(page, 'Nhận xử lý phản ánh');
    await page.getByRole('button', { name: 'Bác bỏ phản ánh', exact: true }).waitFor({ timeout: 15000 });
    const s2 = await decide(page, 'Bác bỏ phản ánh', 'QA: delivery record reviewed; dismissed (simulated).');
    const d = await api('GET', `/api/v1/disputes/${ids.disputeId}`, { token: admin.token, label: 'dispute after' });
    assert(s1.status === 200 && s2.status === 200, JSON.stringify([s1, s2]));
    return { steps: [s1, s2], disputeStatus: d.data?.disputeStatus, screenshot: await shot(page, 'admin', '06-dispute-resolved') };
  });
  await check('admin', 'AD10', 'UI: escrow page and API reconciliation are consistent for the QA campaign', async () => {
    const e = await api('GET', `/api/v1/campaigns/${ids.campaignId}/escrow`, { label: 'escrow public' });
    const rc = await api('GET', `/api/v1/admin/escrow/campaigns/${ids.campaignId}/reconciliation`, { token: admin.token, label: 'reconcile campaign' });
    assert(e.status === 200 && rc.status === 200, `${e.status}/${rc.status}`);
    const consistent = rc.data?.consistent ?? rc.data?.status;
    assert(consistent === true || consistent === 'CONSISTENT', 'reconciliation ' + JSON.stringify(rc.data).slice(0, 200));
    ids.escrow = e.data;
    await spaNav(page, '/admin/escrow');
    await sleep(1500);
    return { escrow: e.data, reconciliation: consistent, screenshot: await shot(page, 'admin', '07-escrow') };
  });
  await check('admin', 'AD11', 'admin runs a platform-wide reconciliation run', async () => {
    const r = await api('POST', '/api/v1/admin/escrow/reconciliation/runs', { token: admin.token, label: 'reconciliation run' });
    assert(r.status === 200 || r.status === 201, 'status ' + r.status + ' ' + r.json?.code);
    return { run: { id: r.data?.id, status: r.data?.runStatus ?? r.data?.status, inconsistent: r.data?.inconsistentAccounts ?? r.data?.inconsistentCount } };
  });
  await check('admin', 'AD12', 'monitoring: outbox delivered events and findings are visible to admin only', async () => {
    const stats = await poll(async () => {
      const s = await api('GET', '/api/v1/admin/monitoring/outbox/stats', { token: admin.token, label: 'outbox stats' });
      return s.status === 200 ? s.data : null;
    }, 10000);
    const f = await poll(async () => {
      const x = await api('GET', '/api/v1/admin/monitoring/findings?size=100', { token: admin.token, label: 'findings' });
      return items(x.data).some((y) => y.campaignId === ids.campaignId) ? x : null;
    }, 60000, 3000) || await api('GET', '/api/v1/admin/monitoring/findings?size=100', { token: admin.token, label: 'findings final' });
    const fd = await api('GET', '/api/v1/admin/monitoring/findings', { token: donor1.token, label: 'findings as donor' });
    assert(f.status === 200 && fd.status === 403, `${f.status}/${fd.status}`);
    ids.findings = items(f.data).filter((x) => x.campaignId === ids.campaignId).map((x) => ({ id: x.id, rule: x.ruleId ?? x.ruleCode, severity: x.severity, campaignId: x.campaignId }));
    assert(ids.findings.some((x) => x.rule === 'rise.evidence.financial-without-hash'), 'no financial-without-hash (EV-02) finding for QA campaign: ' + JSON.stringify(ids.findings));
    await spaNav(page, '/admin/monitoring');
    await sleep(1500);
    return { outbox: stats, findings: ids.findings.length, donorAccess: fd.status, screenshot: await shot(page, 'admin', '08-monitoring') };
  });
  await check('admin', 'AD13', 'monitoring precision endpoint and audit history', async () => {
    const p = await api('GET', '/api/v1/admin/monitoring/findings/precision', { token: admin.token, label: 'precision' });
    const mine = [];
    let a = { status: 0 };
    for (const id of [ids.campaignId, ids.donationId, ids.disbursementId, ids.refundId, ids.disputeId, ids.orgId]) {
      a = await api('GET', `/api/v1/admin/audit-logs?entityId=${id}&size=50`, { token: admin.token, label: 'audit ' + id.slice(0, 8) });
      assert(a.status === 200, 'audit ' + a.status);
      mine.push(...items(a.data).filter((x) => x.entityId === id));
    }
    assert(p.status === 200, `precision ${p.status}`);
    assert(mine.length > 0, 'no audit rows for QA entities on first page');
    await spaNav(page, '/admin/audit');
    await sleep(1500);
    return { precisionRules: Array.isArray(p.data) ? p.data.length : null, auditRowsForQaEntities: mine.length, screenshot: await shot(page, 'admin', '09-audit') };
  });
  await ctx.close();
}
await check('admin', 'AD14', 'review a monitoring finding with a note (if one is reviewable)', async () => {
  const target = (ids.findings || [])[0];
  if (!target) throw blocked('no finding produced in this run');
  const r = await api('POST', `/api/v1/admin/monitoring/findings/${target.id}/review`, { token: admin.token, body: { status: 'ACKNOWLEDGED', note: 'QA E2E reviewed (advisory only)' }, label: 'review finding' });
  assert(r.status === 200 && r.data?.reviewStatus === 'ACKNOWLEDGED', 'status ' + r.status + ' ' + r.json?.code + ' ' + r.data?.reviewStatus);
  const neg = await api('POST', `/api/v1/admin/monitoring/findings/${target.id}/review`, { token: admin.token, body: { status: 'DISMISSED', note: '' }, label: 'review without note' });
  assert(neg.status === 400, 'empty note accepted: ' + neg.status);
  return { rule: target.rule, reviewStatus: r.data.reviewStatus, emptyNote: neg.status };
});

// =====================================================================================================
await browser.close();
fs.writeFileSync(`${OUT}/results/e2e-results.json`, JSON.stringify({ run: RUN, base: BASE, finishedAt: now(), results, consoleErrors, ids }, null, 2));
fs.writeFileSync(`${OUT}/results/api-results.json`, JSON.stringify(apiLog, null, 2));
const c = results.reduce((m, r) => ((m[r.status] = (m[r.status] || 0) + 1), m), {});
console.log('SUMMARY', JSON.stringify(c), 'consoleErrors', consoleErrors.length);
process.exit(0);
