// Supplement to e2e.mjs run 3: re-tests A9 and D7 so each asserts exactly what its name claims. API only, no tokens in output.
import fs from 'node:fs';
import crypto from 'node:crypto';
const BASE = 'http://127.0.0.1:5174';
const OUT = process.argv[2];
const PASSWORD = fs.readFileSync(OUT + '/secrets/demo-password.txt', 'utf8').trim();
const run3 = JSON.parse(fs.readFileSync(OUT + '/results/e2e-results.json', 'utf8'));
const RUN = new Date().toISOString().replace(/[-:T.Z]/g, '').slice(0, 14);
const results = [];
async function api(method, path, { token, body, headers = {} } = {}) {
  const h = { 'Content-Type': 'application/json', ...headers };
  if (token) h.Authorization = 'Bearer ' + token;
  let r;
  // The backend rate-limits auth per IP (20/min); right after e2e.mjs the bucket can be empty, so wait and retry on 429.
  for (let i = 0; i < 8; i++) {
    r = await fetch(BASE + path, { method, headers: h, body: body && JSON.stringify(body) });
    if (r.status !== 429) break;
    await new Promise((res) => setTimeout(res, 10000));
  }
  const j = await r.json().catch(() => null);
  return { status: r.status, code: j?.code, message: j?.message, data: j?.data };
}
function rec(id, name, pass, evidence) { results.push({ id, name, status: pass ? 'PASS' : 'FAIL', evidence, at: new Date().toISOString() }); console.log(pass ? 'PASS' : 'FAIL', id, name, JSON.stringify(evidence)); }

const a = await api('POST', '/api/v1/auth/register', { body: { email: `qa.adm.${RUN}@riseai.example`, password: 'QaStrong1' + RUN, fullName: 'QA Admin Attempt', accountType: 'ADMIN' } });
rec('A9a', 'register with accountType=ADMIN is rejected', a.status === 400, { status: a.status, code: a.code });

const email = `qa.inj.${RUN}@riseai.example`;
const b = await api('POST', '/api/v1/auth/register', { body: { email, password: 'QaStrong1' + RUN, fullName: 'QA Injection', accountType: 'DONOR', roles: ['ADMIN'], role: 'ADMIN' } });
const roles = b.data?.user?.roles ?? b.data?.roles;
const login = await api('POST', '/api/v1/auth/login', { body: { email, password: 'QaStrong1' + RUN } });
const st = await api('GET', '/api/v1/admin/statistics', { token: login.data?.accessToken });
rec('A9b', 'extra roles=[ADMIN] in a DONOR registration is ignored', (b.status === 201 || b.status === 200) && JSON.stringify(roles) === '["DONOR"]' && st.status === 403,
  { register: b.status, roles, adminApiWithNewAccount: st.status });

const d2 = await api('POST', '/api/v1/auth/login', { body: { email: 'donor2@riseai.example', password: PASSWORD } });
const donationId = run3.ids.donor2DonationId;
const don = await api('GET', `/api/v1/donations/${donationId}`, { token: d2.data.accessToken });
const big = await api('POST', `/api/v1/donations/${donationId}/refunds`, { token: d2.data.accessToken, headers: { 'Idempotency-Key': crypto.randomUUID() },
  body: { amount: Number(don.data.amount) + 1, reason: 'QA: one more than donated' } });
const after = await api('GET', `/api/v1/donations/${donationId}`, { token: d2.data.accessToken });
rec('D7', 'refund larger than the donation is rejected (no refund pending on that donation)',
  big.status >= 400 && big.status < 500 && after.data.donationStatus === don.data.donationStatus,
  { donationStatusBefore: don.data.donationStatus, amount: don.data.amount, requested: Number(don.data.amount) + 1, status: big.status, code: big.code, donationStatusAfter: after.data.donationStatus });

fs.writeFileSync(OUT + '/results/supplement-results.json', JSON.stringify({ run: RUN, results }, null, 2));
